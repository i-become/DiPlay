/* Included by diplay_integrity.c. All decisions stay behind the native operation gate. */
#include <stdlib.h>
#include "seal_authority.h"

#define SEAL_MANIFEST "assets/diplay-integrity/content.v1"
#define SEAL_SIGNATURE "assets/diplay-integrity/content.sig"
#define SEAL_MAGIC "DIPLAY-SEAL-1\n"

static int seal_covered(const char *s) {
    return strncmp(s, "META-INF/", 9) && strcmp(s, SEAL_MANIFEST) && strcmp(s, SEAL_SIGNATURE);
}

/* Bounded reads, heap buffers, no extracted DEX or resource files on disk. */
static jbyteArray seal_read(JNIEnv *e, jobject zip, const char *name, int limit) {
    jmethodID get = method(e, zip, "getEntry", "(Ljava/lang/String;)Ljava/util/zip/ZipEntry;");
    if (!get) return NULL;
    jstring path = (*e)->NewStringUTF(e, name);
    jobject entry = (*e)->CallObjectMethod(e, zip, get, path);
    if (!entry || (*e)->ExceptionCheck(e)) return NULL;
    jmethodID size = method(e, entry, "getSize", "()J");
    if (!size) return NULL;
    jlong length = (*e)->CallLongMethod(e, entry, size);
    if ((*e)->ExceptionCheck(e) || length < 1 || length > limit) return NULL;
    jmethodID open = method(e, zip, "getInputStream", "(Ljava/util/zip/ZipEntry;)Ljava/io/InputStream;");
    if (!open) return NULL;
    jobject in = (*e)->CallObjectMethod(e, zip, open, entry);
    if (!in || (*e)->ExceptionCheck(e)) return NULL;
    jmethodID read = method(e, in, "read", "([BII)I");
    jmethodID close = method(e, in, "close", "()V");
    if (!read || !close) return NULL;
    jbyteArray bytes = (*e)->NewByteArray(e, (jsize)length);
    if (!bytes) return NULL;
    int offset = 0;
    while (offset < length) {
        int n = (*e)->CallIntMethod(e, in, read, bytes, offset, (int)length - offset);
        if ((*e)->ExceptionCheck(e) || n <= 0) return NULL;
        offset += n;
    }
    (*e)->CallVoidMethod(e, in, close);
    return (*e)->ExceptionCheck(e) ? NULL : bytes;
}

static int seal_signature_ok(JNIEnv *e, jbyteArray content, jbyteArray signature) {
    jclass spec_cls = (*e)->FindClass(e, "java/security/spec/X509EncodedKeySpec");
    jclass factory_cls = (*e)->FindClass(e, "java/security/KeyFactory");
    jclass sig_cls = (*e)->FindClass(e, "java/security/Signature");
    if (!spec_cls || !factory_cls || !sig_cls) return 0;
    jbyteArray der = (*e)->NewByteArray(e, sizeof(seal_authority));
    if (!der) return 0;
    (*e)->SetByteArrayRegion(e, der, 0, sizeof(seal_authority), (const jbyte *)seal_authority);
    jmethodID ctor = (*e)->GetMethodID(e, spec_cls, "<init>", "([B)V");
    jmethodID get = (*e)->GetStaticMethodID(e, factory_cls, "getInstance", "(Ljava/lang/String;)Ljava/security/KeyFactory;");
    if (!ctor || !get) return 0;
    jobject spec = (*e)->NewObject(e, spec_cls, ctor, der); STOP_IF_ERROR();
    jobject factory = (*e)->CallStaticObjectMethod(e, factory_cls, get, (*e)->NewStringUTF(e, "RSA")); STOP_IF_ERROR();
    if (!factory) return 0;
    jmethodID generate = method(e, factory, "generatePublic", "(Ljava/security/spec/KeySpec;)Ljava/security/PublicKey;");
    if (!generate) return 0;
    jobject key = (*e)->CallObjectMethod(e, factory, generate, spec); STOP_IF_ERROR();
    if (!key) return 0;
    get = (*e)->GetStaticMethodID(e, sig_cls, "getInstance", "(Ljava/lang/String;)Ljava/security/Signature;");
    if (!get) return 0;
    jobject verifier = (*e)->CallStaticObjectMethod(e, sig_cls, get, (*e)->NewStringUTF(e, "SHA256withRSA")); STOP_IF_ERROR();
    if (!verifier) return 0;
    jmethodID init = method(e, verifier, "initVerify", "(Ljava/security/PublicKey;)V");
    jmethodID update = method(e, verifier, "update", "([B)V");
    jmethodID verify_sig = method(e, verifier, "verify", "([B)Z");
    if (!init || !update || !verify_sig) return 0;
    (*e)->CallVoidMethod(e, verifier, init, key); STOP_IF_ERROR();
    (*e)->CallVoidMethod(e, verifier, update, content); STOP_IF_ERROR();
    int ok = (*e)->CallBooleanMethod(e, verifier, verify_sig, signature); STOP_IF_ERROR();
    return ok;
}

static int seal_entry_ok(JNIEnv *e, jobject zip, const char *name, const char *expected) {
    jmethodID get = method(e, zip, "getEntry", "(Ljava/lang/String;)Ljava/util/zip/ZipEntry;");
    if (!get) return 0;
    jobject entry = (*e)->CallObjectMethod(e, zip, get, (*e)->NewStringUTF(e, name)); STOP_IF_ERROR();
    if (!entry) return 0;
    jmethodID open = method(e, zip, "getInputStream", "(Ljava/util/zip/ZipEntry;)Ljava/io/InputStream;");
    if (!open) return 0;
    jobject in = (*e)->CallObjectMethod(e, zip, open, entry); STOP_IF_ERROR();
    if (!in) return 0;
    jmethodID read = method(e, in, "read", "([B)I");
    jmethodID close = method(e, in, "close", "()V");
    jclass md = (*e)->FindClass(e, "java/security/MessageDigest");
    if (!read || !close || !md) return 0;
    jmethodID get_md = (*e)->GetStaticMethodID(e, md, "getInstance", "(Ljava/lang/String;)Ljava/security/MessageDigest;");
    if (!get_md) return 0;
    jobject digest = (*e)->CallStaticObjectMethod(e, md, get_md, (*e)->NewStringUTF(e, "SHA-256")); STOP_IF_ERROR();
    if (!digest) return 0;
    jmethodID update = method(e, digest, "update", "([BII)V");
    jmethodID finish = method(e, digest, "digest", "()[B");
    if (!update || !finish) return 0;
    jbyteArray buffer = (*e)->NewByteArray(e, 32768);
    if (!buffer) return 0;
    int n;
    unsigned total = 0;
    for (;;) {
        n = (*e)->CallIntMethod(e, in, read, buffer); STOP_IF_ERROR();
        if (n == -1) break;
        if (n <= 0 || (total += (unsigned)n) > 128U * 1024U * 1024U) return 0;
        (*e)->CallVoidMethod(e, digest, update, buffer, 0, n); STOP_IF_ERROR();
    }
    (*e)->CallVoidMethod(e, in, close); STOP_IF_ERROR();
    jbyteArray bytes = (*e)->CallObjectMethod(e, digest, finish); STOP_IF_ERROR();
    if (!bytes || (*e)->GetArrayLength(e, bytes) != 32) return 0;
    jbyte hash[32];
    (*e)->GetByteArrayRegion(e, bytes, 0, 32, hash); STOP_IF_ERROR();
    const char hex[] = "0123456789abcdef";
    unsigned diff = 0;
    for (int i = 0; i < 32; i++) {
        unsigned b = (unsigned char)hash[i];
        diff |= (unsigned)(expected[i * 2] ^ hex[b >> 4]);
        diff |= (unsigned)(expected[i * 2 + 1] ^ hex[b & 15]);
    }
    return diff == 0;
}

static int seal_check_zip(JNIEnv *e, jobject zip) {
    jbyteArray content = seal_read(e, zip, SEAL_MANIFEST, 4 * 1024 * 1024);
    if (!content) return 0;
    jbyteArray sig = seal_read(e, zip, SEAL_SIGNATURE, 8192);
    if (!sig || !seal_signature_ok(e, content, sig)) return 0;
    int length = (*e)->GetArrayLength(e, content);
    if (length <= (int)strlen(SEAL_MAGIC)) return 0;
    char *text = malloc((size_t)length + 1);
    if (!text) return 0;
    (*e)->GetByteArrayRegion(e, content, 0, length, (jbyte *)text);
    text[length] = 0;
    int ok = 0, count = 0;
    if ((*e)->ExceptionCheck(e) || memchr(text, 0, length) || strncmp(text, SEAL_MAGIC, strlen(SEAL_MAGIC))) goto done;
    char *line = text + strlen(SEAL_MAGIC), *previous = NULL;
    while (*line) {
        char *end = strchr(line, '\n');
        if (!end || end - line < 66 || line[64] != ' ' || ++count > 100000) goto done;
        *end = 0;
        char *name = line + 65;
        if (!seal_covered(name) || (previous && strcmp(previous, name) >= 0)) goto done;
        if ((*e)->PushLocalFrame(e, 32)) goto done;
        int valid = seal_entry_ok(e, zip, name, line);
        (*e)->PopLocalFrame(e, NULL);
        if (!valid) goto done;
        previous = name; line = end + 1;
    }
    /* Missing, added and duplicate entries must fail, not just changes to listed files. */
    jmethodID entries = method(e, zip, "entries", "()Ljava/util/Enumeration;");
    if (!entries) goto done;
    jobject enumeration = (*e)->CallObjectMethod(e, zip, entries);
    if (!enumeration || (*e)->ExceptionCheck(e)) goto done;
    jmethodID more = method(e, enumeration, "hasMoreElements", "()Z");
    jmethodID next = method(e, enumeration, "nextElement", "()Ljava/lang/Object;");
    if (!more || !next) goto done;
    int actual = 0, manifests = 0, signatures = 0;
    while ((*e)->CallBooleanMethod(e, enumeration, more)) {
        if ((*e)->ExceptionCheck(e) || (*e)->PushLocalFrame(e, 8)) goto done;
        jobject entry = (*e)->CallObjectMethod(e, enumeration, next);
        jmethodID get_name = entry ? method(e, entry, "getName", "()Ljava/lang/String;") : NULL;
        if (!get_name) { (*e)->PopLocalFrame(e, NULL); goto done; }
        jstring name = (*e)->CallObjectMethod(e, entry, get_name);
        if (!name || (*e)->ExceptionCheck(e)) { (*e)->PopLocalFrame(e, NULL); goto done; }
        const char *s = (*e)->GetStringUTFChars(e, name, NULL);
        if (!s) { (*e)->PopLocalFrame(e, NULL); goto done; }
        actual += seal_covered(s) != 0;
        manifests += strcmp(s, SEAL_MANIFEST) == 0;
        signatures += strcmp(s, SEAL_SIGNATURE) == 0;
        (*e)->ReleaseStringUTFChars(e, name, s);
        (*e)->PopLocalFrame(e, NULL);
        if (actual > count || manifests > 1 || signatures > 1) goto done;
    }
    ok = !(*e)->ExceptionCheck(e) && actual == count && manifests == 1 && signatures == 1;
done:
    free(text);
    return ok;
}

static int verify_content_seal(JNIEnv *e, jobject context) {
    jmethodID get = method(e, context, "getApplicationInfo", "()Landroid/content/pm/ApplicationInfo;");
    if (!get) return 0;
    jobject info = (*e)->CallObjectMethod(e, context, get); STOP_IF_ERROR();
    if (!info) return 0;
    jclass cls = (*e)->GetObjectClass(e, info);
    if (!cls) return 0;
    jfieldID source = (*e)->GetFieldID(e, cls, "sourceDir", "Ljava/lang/String;");
    jfieldID splits = (*e)->GetFieldID(e, cls, "splitSourceDirs", "[Ljava/lang/String;");
    if (!source || !splits) return 0;
    jobjectArray extra = (*e)->GetObjectField(e, info, splits);
    if (extra && (*e)->GetArrayLength(e, extra)) return 0; /* Standalone APK only. */
    jstring path = (*e)->GetObjectField(e, info, source);
    if (!path) return 0;
    jclass zip_cls = (*e)->FindClass(e, "java/util/zip/ZipFile");
    if (!zip_cls) return 0;
    jmethodID ctor = (*e)->GetMethodID(e, zip_cls, "<init>", "(Ljava/lang/String;)V");
    jmethodID close = (*e)->GetMethodID(e, zip_cls, "close", "()V");
    if (!ctor || !close) return 0;
    jobject zip = (*e)->NewObject(e, zip_cls, ctor, path); STOP_IF_ERROR();
    if (!zip) return 0;
    int ok = seal_check_zip(e, zip);
    if ((*e)->ExceptionCheck(e)) { (*e)->ExceptionClear(e); ok = 0; }
    (*e)->CallVoidMethod(e, zip, close); STOP_IF_ERROR();
    return ok;
}
