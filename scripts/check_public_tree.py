#!/usr/bin/env python3
"""Fail CI if credential containers or private-key blocks enter the source tree."""
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[1]
names = subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).decode().split('\0')
blocked_suffixes = {'.pk8', '.p7b', '.pem', '.key', '.p12', '.pfx', '.jks', '.keystore',
                    '.cer', '.crt', '.der', '.mobileprovision', '.apk', '.aab'}
blocked_prefixes = ('runtime-assets/',)
private_block = re.compile(rb'-----BEGIN (?:[A-Z0-9 ]+ )?PRIVATE KEY-----\s+[A-Za-z0-9+/=\r\n]{40,}')
failures = []
for name in filter(None, names):
    path = root / name
    if not path.is_file():
        continue
    if ('.private' in path.relative_to(root).parts or 'offline-mfi' in path.relative_to(root).parts
            or path.suffix.lower() in blocked_suffixes or name.startswith(blocked_prefixes)
            or name in {'common/libs/icar-sdk-stubs.jar', 'mobile/libs/icar-sdk-corrected.dex'}
            or path.name in {'libovmsink.so', 'libusbio.so'}
            or path.name == 'local.properties'
            or ('signing' in path.name.lower() and path.suffix == '.properties')):
        failures.append(name)
    elif private_block.search(path.read_bytes()):
        failures.append(name)
if failures:
    raise SystemExit('Credential or distribution files are forbidden in the public tree:\n' + '\n'.join(failures))
print('Public tree check passed: no credentials, private-key blocks or local vendor binaries.')
