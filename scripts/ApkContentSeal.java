import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Release-only content seal. Never stores a private key or password in an APK. */
public final class ApkContentSeal {
    static final String MANIFEST = "assets/diplay-integrity/content.v1";
    static final String SIGNATURE = "assets/diplay-integrity/content.sig";
    static final byte[] MAGIC = "DIPLAY-SEAL-1\n".getBytes(StandardCharsets.US_ASCII);

    static boolean covered(String name) {
        return !name.startsWith("META-INF/") && !name.equals(MANIFEST) && !name.equals(SIGNATURE);
    }

    static byte[] manifest(ZipFile zip) throws Exception {
        TreeMap<String, ZipEntry> entries = new TreeMap<>();
        HashSet<String> all = new HashSet<>();
        for (ZipEntry entry : Collections.list(zip.entries())) {
            String name = entry.getName();
            if (!all.add(name)) throw new SecurityException("Duplicate APK entry: " + name);
            if (name.isEmpty() || name.chars().anyMatch(c -> c < 32 || c > 126))
                throw new SecurityException("Unsupported APK entry name");
            if (covered(name)) entries.put(name, entry);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MAGIC);
        byte[] buffer = new byte[32768];
        for (Map.Entry<String, ZipEntry> item : entries.entrySet()) {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            try (InputStream in = zip.getInputStream(item.getValue())) {
                for (int n; (n = in.read(buffer)) != -1;) hash.update(buffer, 0, n);
            }
            out.write((HexFormat.of().formatHex(hash.digest()) + " " + item.getKey() + "\n")
                .getBytes(StandardCharsets.US_ASCII));
        }
        return out.toByteArray();
    }

    static KeyStore store(String[] args) throws Exception {
        // Credentials travel in child-process environment, never in command arguments/logs.
        String path = System.getenv("DIPLAY_SEAL_KEYSTORE");
        char[] password = Objects.requireNonNull(System.getenv("DIPLAY_SEAL_STORE_PASSWORD")).toCharArray();
        try { return KeyStore.getInstance(new File(path), password); }
        finally { Arrays.fill(password, '\0'); }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("seal|verify|export-key <path>");
        KeyStore store = store(args);
        String alias = Objects.requireNonNull(System.getenv("DIPLAY_SEAL_ALIAS"));
        PublicKey publicKey = store.getCertificate(alias).getPublicKey();
        if (!publicKey.getAlgorithm().equals("RSA")) throw new SecurityException("Expected RSA release authority");
        Path path = Path.of(args[1]);
        if (args[0].equals("export-key")) {
            StringBuilder header = new StringBuilder("/* Public release content-seal authority (SPKI DER). Not a secret. */\nstatic const unsigned char seal_authority[] = {\n");
            for (byte b : publicKey.getEncoded()) header.append(String.format("0x%02x,", b & 255));
            header.append("\n};\n");
            Files.writeString(path, header);
            return;
        }
        if (args[0].equals("verify")) {
            try (ZipFile zip = new ZipFile(path.toFile())) {
                byte[] content = zip.getInputStream(Objects.requireNonNull(zip.getEntry(MANIFEST))).readAllBytes();
                byte[] signature = zip.getInputStream(Objects.requireNonNull(zip.getEntry(SIGNATURE))).readAllBytes();
                Signature verifier = Signature.getInstance("SHA256withRSA");
                verifier.initVerify(publicKey); verifier.update(content);
                if (!verifier.verify(signature) || !MessageDigest.isEqual(content, manifest(zip)))
                    throw new SecurityException("APK content seal rejected");
            }
            System.out.println("Content seal verified (all non-signature entries)");
            return;
        }
        if (!args[0].equals("seal")) throw new IllegalArgumentException("Unknown mode");
        char[] password = Objects.requireNonNull(System.getenv("DIPLAY_SEAL_KEY_PASSWORD")).toCharArray();
        PrivateKey key;
        try { key = (PrivateKey) store.getKey(alias, password); }
        finally { Arrays.fill(password, '\0'); }
        Path output = Files.createTempFile(path.toAbsolutePath().getParent(), "content-seal-", ".apk");
        try {
            try (ZipFile zip = new ZipFile(path.toFile());
                 ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(output)))) {
                byte[] content = manifest(zip);
                Signature signer = Signature.getInstance("SHA256withRSA");
                signer.initSign(key); signer.update(content);
                byte[] signature = signer.sign();
                for (ZipEntry entry : Collections.list(zip.entries())) {
                    if (!covered(entry.getName())) continue;
                    ZipEntry copy = new ZipEntry(entry.getName());
                    copy.setMethod(entry.getMethod());
                    if (entry.getMethod() == ZipEntry.STORED) { copy.setSize(entry.getSize()); copy.setCrc(entry.getCrc()); }
                    out.putNextEntry(copy);
                    try (InputStream in = zip.getInputStream(entry)) { in.transferTo(out); }
                    out.closeEntry();
                }
                out.putNextEntry(new ZipEntry(MANIFEST)); out.write(content); out.closeEntry();
                out.putNextEntry(new ZipEntry(SIGNATURE)); out.write(signature); out.closeEntry();
            }
            Files.move(output, path, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(output); }
        System.out.println("Release content sealed after SDK injection");
    }
}
