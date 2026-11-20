"""Exercise the release seal with a disposable key; no production credentials needed.

Run: python scripts/test_apk_content_seal.py
Requires JDK 17+ (java and keytool on PATH).
"""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import warnings
import zipfile


TOOL = Path(__file__).with_name("ApkContentSeal.java").resolve()


class ContentSealTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix="diplay-seal-test-")
        cls.root = Path(cls.temp.name)
        cls.env = dict(os.environ, DIPLAY_SEAL_KEYSTORE=str(cls.root / "test.jks"),
                       DIPLAY_SEAL_STORE_PASSWORD="disposable-test-password",
                       DIPLAY_SEAL_KEY_PASSWORD="disposable-test-password", DIPLAY_SEAL_ALIAS="test")
        subprocess.run(["keytool", "-genkeypair", "-alias", "test", "-keyalg", "RSA",
                        "-keysize", "2048", "-dname", "CN=Disposable seal test", "-validity", "1",
                        "-keystore", cls.env["DIPLAY_SEAL_KEYSTORE"], "-storetype", "JKS",
                        "-storepass:env", "DIPLAY_SEAL_STORE_PASSWORD",
                        "-keypass:env", "DIPLAY_SEAL_KEY_PASSWORD"],
                       env=cls.env, capture_output=True, check=True)
        cls.official = cls.root / "sealed.apk"
        with zipfile.ZipFile(cls.official, "w") as out:
            for name in ("classes.dex", "classes2.dex", "resources.arsc", "AndroidManifest.xml",
                         "lib/arm64-v8a/libdiplay_integrity.so", "assets/offline-mfi/identity.pk8"):
                out.writestr(name, b"test fixture bytes: " + name.encode())
        cls.run_tool("seal", cls.official, True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    @classmethod
    def run_tool(cls, mode, path, expected):
        result = subprocess.run(["java", str(TOOL), mode, str(path)],
                                env=cls.env, capture_output=True, text=True)
        if (result.returncode == 0) != expected:
            raise AssertionError(f"Unexpected {mode} verdict: {result.stdout}\n{result.stderr}")

    def altered(self, label, change):
        target = self.root / (label + ".apk")
        with zipfile.ZipFile(self.official) as src, zipfile.ZipFile(target, "w") as out:
            for item in src.infolist():
                data = change(item.filename, src.read(item))
                if data is not None:
                    out.writestr(item, data)
        return target

    def test_official_and_repeat_packaging(self):
        self.run_tool("verify", self.official, True)
        target = self.altered("repeat", lambda name, data: data)
        self.run_tool("seal", target, True)
        self.run_tool("verify", target, True)

    def test_changed_payloads(self):
        for name in ("classes.dex", "classes2.dex", "resources.arsc", "AndroidManifest.xml",
                     "lib/arm64-v8a/libdiplay_integrity.so", "assets/offline-mfi/identity.pk8",
                     "assets/diplay-integrity/content.v1", "assets/diplay-integrity/content.sig"):
            with self.subTest(name=name):
                target = self.altered("changed", lambda n, data: data + b"changed" if n == name else data)
                self.run_tool("verify", target, False)

    def test_missing_and_added_entries(self):
        for name in ("classes2.dex", "assets/diplay-integrity/content.sig"):
            target = self.altered("missing", lambda n, data: None if n == name else data)
            self.run_tool("verify", target, False)
        target = self.altered("added", lambda name, data: data)
        with zipfile.ZipFile(target, "a") as out:
            out.writestr("classes3.dex", b"injected code")
        self.run_tool("verify", target, False)

    def test_duplicate_entry(self):
        target = self.altered("duplicate", lambda name, data: data)
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(target, "a") as out:
                out.writestr("classes.dex", b"duplicate code")
        self.run_tool("verify", target, False)
        self.run_tool("seal", target, False)


if __name__ == "__main__":
    unittest.main()
