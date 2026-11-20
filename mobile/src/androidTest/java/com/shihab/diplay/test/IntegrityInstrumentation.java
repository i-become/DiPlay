package com.shihab.diplay.test;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import com.shilapi.xcertplay.security.NativeIntegrity;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

/** Run against both the official release and a re-signed copy on an isolated emulator. */
public final class IntegrityInstrumentation extends Instrumentation {
    private boolean expectRejected;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        expectRejected = arguments != null && "rejected".equals(arguments.getString("expected"));
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            long delay = NativeIntegrity.INSTANCE.warningDelayMillis();
            if (delay < 0 || delay > 30 * 60 * 1000L) throw new AssertionError("Invalid warning delay");
            if (delay > 0 && NativeIntegrity.INSTANCE.warning(context) != null)
                throw new AssertionError("Warning revealed before deadline");
            result.putLong("warningDelayMs", delay);
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();
            byte[] digest = new byte[32];
            long began = android.os.SystemClock.elapsedRealtime();
            boolean rejected = false;
            byte[] signature = null;
            try {
                signature = NativeIntegrity.INSTANCE.sign(context, pair.getPrivate(), digest);
            } catch (SecurityException expected) {
                rejected = true;
                if (!"Connection initialization failed".equals(expected.getMessage()))
                    throw new AssertionError("Native exception reveals the notice");
            }
            result.putLong("firstCheckMs", android.os.SystemClock.elapsedRealtime() - began);
            if (rejected != expectRejected) throw new AssertionError("Unexpected integrity verdict: rejected=" + rejected);
            if (rejected) {
                try {
                    NativeIntegrity.INSTANCE.sign(context, pair.getPrivate(), digest);
                    throw new AssertionError("Re-signed APK produced a signature");
                } catch (SecurityException expected) { /* Native signing rejects independently of UI. */ }
                try {
                    NativeIntegrity.INSTANCE.initializeCarlink(context, new Object(), new Object(), new Object());
                    throw new AssertionError("Re-signed APK reached the SDK");
                } catch (SecurityException expected) { /* Must reject before looking up SDK methods. */ }
                result.putString("integrity", "REJECTED: signing + Carlink; warning deferred");
            } else {
                Signature verifier = Signature.getInstance("NONEwithECDSA");
                verifier.initVerify(pair.getPublic());
                verifier.update(digest);
                if (!verifier.verify(signature)) throw new AssertionError("Invalid native signature");
                try {
                    NativeIntegrity.INSTANCE.sign(context, pair.getPrivate(), new byte[31]);
                    throw new AssertionError("Invalid digest length accepted");
                } catch (IllegalArgumentException expected) { }
                if (NativeIntegrity.INSTANCE.warning(context) != null) throw new AssertionError("Official warning");
                result.putString("integrity", "ACCEPTED: no warning + valid native ECDSA");
                began = android.os.SystemClock.elapsedRealtime();
                for (int i = 0; i < 100; i++) {
                    if (NativeIntegrity.INSTANCE.warning(context) != null) throw new AssertionError("Cached verdict changed");
                }
                result.putLong("cached100ChecksMs", android.os.SystemClock.elapsedRealtime() - began);
            }
            finish(-1, result);
        } catch (Throwable failure) {
            result.putString("failure", failure.toString());
            finish(0, result);
        }
    }
}
