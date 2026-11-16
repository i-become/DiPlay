"""Deduplicate donor Guava and gate SDK diagnostic output through the host.

Connection code is retained; only log sinks are redirected while re-serializing
the DEX. The original donor DEX is never edited.
"""
import argparse
from collections import Counter
import hashlib
from pathlib import Path
import re
import struct
import os
import subprocess
import zipfile
import zlib


def definitions(data):
    if data[:8] not in (b'dex\n035\0', b'dex\n037\0', b'dex\n038\0', b'dex\n039\0', b'dex\n040\0'):
        raise ValueError('Unsupported DEX format')
    u32 = lambda offset: struct.unpack_from('<I', data, offset)[0]
    if u32(32) != len(data) or u32(36) != 112:
        raise ValueError('Invalid DEX header')
    if hashlib.sha1(data[32:]).digest() != data[12:32] or zlib.adler32(data[12:]) & 0xffffffff != u32(8):
        raise ValueError('Invalid DEX checksum')
    strings, types, classes = u32(60), u32(68), u32(100)
    result = {}
    for index in range(u32(96)):
        record = classes + index * 32
        type_index = u32(record)
        offset = u32(strings + u32(types + type_index * 4) * 4)
        while data[offset] & 0x80:
            offset += 1
        offset += 1
        name = data[offset:data.index(b'\0', offset)].decode('ascii')
        if name in result:
            raise ValueError(f'Duplicate definition: {name}')
        result[name] = bytes(data[record:record + 32])
    return result


def host_definitions(apk, sdk):
    result = Counter()
    injected = []
    expected = definitions(sdk)
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if re.fullmatch(r'classes\d*\.dex', name):
                data = archive.read(name)
                # assemble may be up-to-date and hand us the already injected APK.
                entries = definitions(data)
                if 'Lcom/ucar/vehiclesdk/UCarAdapter;' in entries:
                    if not set(entries) <= set(expected):
                        raise ValueError('Unexpected or changed injected SDK')
                    injected.append(name)
                    continue
                result.update(entries.keys())
    return result, injected


def tools_classpath(sdk_dir):
    libraries = sdk_dir / 'cmdline-tools' / 'latest' / 'lib'
    jars = [path for path in libraries.rglob('*.jar') if path.name.startswith(('smali-dexlib2-', 'guava-', 'failureaccess-'))]
    if not any(path.name.startswith('smali-dexlib2-') for path in jars):
        raise ValueError('Android SDK cmdline-tools;latest with smali-dexlib2 is required')
    return os.pathsep.join(map(str, jars))


def prepare(apk, original, output, sdk_dir, java):
    sdk = original.read_bytes()
    records = definitions(sdk)
    host, injected = host_definitions(apk, sdk)
    # A previously prepared SDK can also be present on an up-to-date build.
    # Its non-Guava SDK markers distinguish it from every ordinary host DEX.
    if host.get('Lcom/ucar/vehiclesdk/UCarAdapter;', 0):
        raise ValueError('Repackage the host APK before preparing an injected SDK')
    duplicates = set(records).intersection(host)
    unexpected = {name for name in duplicates if not name.startswith('Lcom/google/common/')}
    if unexpected or any(count > 1 for count in host.values()):
        raise ValueError(f'Unexpected duplicate host/SDK definitions: {sorted(unexpected)[:10]}')
    output.parent.mkdir(parents=True, exist_ok=True)
    removal_file = output.with_suffix('.removed')
    removal_file.write_text('\n'.join(sorted(duplicates)), encoding='utf-8')
    subprocess.run([java, '-cp', tools_classpath(sdk_dir),
                    str(Path(__file__).with_name('CarlinkDexFilter.java')), str(original), str(output), str(removal_file)], check=True)
    assert set(definitions(output.read_bytes())) == set(records) - duplicates
    output.with_suffix('.entries').write_text('\n'.join(injected), encoding='utf-8')
    print(f'Carlink: gated SDK log sinks; using host Guava for {len(duplicates)} shared definitions')


def verify(apk, original):
    counts = Counter()
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if re.fullmatch(r'classes\d*\.dex', name):
                counts.update(definitions(archive.read(name)).keys())
    duplicate = [name for name, count in counts.items() if count > 1]
    missing = set(definitions(original.read_bytes())) - counts.keys()
    if duplicate or missing:
        raise ValueError(f'Final APK: {len(duplicate)} duplicate, {len(missing)} missing SDK classes')
    # Inspect all SDK type references, including dependencies absent from BOTH
    # debug and release. Intersecting with debug definitions misses this case.
    data = original.read_bytes()
    type_count, type_offset = struct.unpack_from('<II', data, 64)
    string_offset = struct.unpack_from('<I', data, 60)[0]
    referenced = set()
    for index in range(type_count):
        string_index = struct.unpack_from('<I', data, type_offset + index * 4)[0]
        offset = struct.unpack_from('<I', data, string_offset + string_index * 4)[0]
        while data[offset] & 0x80:
            offset += 1
        offset += 1
        referenced.add(data[offset:data.index(b'\0', offset)].decode('ascii').lstrip('['))
    platform = ('Landroid/', 'Ljava/', 'Ljavax/', 'Ldalvik/', 'Lorg/json/',
                'Lorg/xml/', 'Lorg/w3c/', 'Lsun/misc/')
    # These annotations have no runtime implementation calls in the SDK.
    compile_annotations = ('Lcom/google/errorprone/annotations/',)
    unresolved = sorted(name for name in referenced if name.startswith('L')
                        and not name.startswith(platform + compile_annotations)
                        and name not in counts)
    if unresolved:
        raise ValueError('Missing SDK runtime dependencies: ' + ', '.join(unresolved))
    print('Carlink: final APK has complete SDK definitions and no duplicate classes')
    print('Carlink: all non-platform SDK runtime type references are packaged')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('sdk', type=Path)
    parser.add_argument('output', type=Path, nargs='?')
    parser.add_argument('--verify', action='store_true')
    parser.add_argument('--sdk-dir', type=Path)
    parser.add_argument('--java', default='java')
    parser.add_argument('--audit-members', action='store_true')
    args = parser.parse_args()
    if args.verify:
        verify(args.apk, args.sdk)
        if args.audit_members:
            if args.sdk_dir is None:
                parser.error('--sdk-dir is required for member auditing')
            subprocess.run([args.java, '-cp', tools_classpath(args.sdk_dir),
                            str(Path(__file__).with_name('CarlinkApkAudit.java')), str(args.apk),
                            str(args.apk.parent / 'carlink-members.txt')], check=True)
    elif args.output is not None:
        sdk_dir = args.sdk_dir
        if sdk_dir is None:
            sdk_dir = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
        if sdk_dir is None:
            for line in Path(__file__).resolve().parents[1].joinpath('local.properties').read_text().splitlines():
                if line.startswith('sdk.dir='):
                    sdk_dir = line[len('sdk.dir='):].replace('\\:', ':').replace('\\\\', '\\')
        if sdk_dir is None:
            parser.error('Android SDK directory is required')
        prepare(args.apk, args.sdk, args.output, Path(sdk_dir), args.java)
    else:
        parser.error('output is required when preparing SDK DEX')
