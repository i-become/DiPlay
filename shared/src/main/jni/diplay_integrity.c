#include <jni.h>
#include <stdatomic.h>
#include <string.h>
#include <pthread.h>
#include <time.h>
#include <stdint.h>

/* Public release certificate fingerprint, not a secret. Change deliberately on key rotation. */
static const char official[] = "359e689fd876a64c7d635a9acf4d307bbec6090fcb93f3e172ae5cceddbbc4f5";
static atomic_int trusted = 0;
static pthread_mutex_t trust_lock = PTHREAD_MUTEX_INITIALIZER;
static int64_t loaded_at_ms;

static int64_t monotonic_ms(void) {
    struct timespec now;
    if (clock_gettime(CLOCK_BOOTTIME, &now)) return -1;
    return (int64_t)now.tv_sec * 1000 + now.tv_nsec / 1000000;
}

static jlong integrity_warning_delay(JNIEnv *e, jobject self) {
    (void)e; (void)self;
    int64_t now = monotonic_ms();
    /* Clock failure must not reveal the notice early. */
    if (now < loaded_at_ms) return 30 * 60 * 1000;
    int64_t remaining = 30 * 60 * 1000 - (now - loaded_at_ms);
    return remaining > 0 ? remaining : 0;
}

static jmethodID method(JNIEnv *e, jobject o, const char *name, const char *sig) {
    jclass c = (*e)->GetObjectClass(e, o);
    if (!c) return NULL;
    jmethodID m = (*e)->GetMethodID(e, c, name, sig);
    (*e)->DeleteLocalRef(e, c);
    return m;
}
#define STOP_IF_ERROR() if ((*e)->ExceptionCheck(e)) return 0
#include "apk_content_seal.h"

static int verify_installation(JNIEnv *e, jobject context) {
    if (!context) return 0;
    if (!DIPLAY_ENFORCE) return 1; /* Build-time only; no runtime debug/property bypass. */
    if (atomic_load(&trusted)) return 1;
    jmethodID m = method(e, context, "getPackageName", "()Ljava/lang/String;");
    if (!m) return 0;
    jstring name = (*e)->CallObjectMethod(e, context, m); STOP_IF_ERROR();
    const char *n = (*e)->GetStringUTFChars(e, name, NULL);
    if (!n) return 0;
    int package_ok = strcmp(n, "com.shihab.diplay") == 0;
    (*e)->ReleaseStringUTFChars(e, name, n);
    if (!package_ok) return 0;
    m = method(e, context, "getPackageManager", "()Landroid/content/pm/PackageManager;");
    if (!m) return 0;
    jobject pm = (*e)->CallObjectMethod(e, context, m); STOP_IF_ERROR();
    jclass version = (*e)->FindClass(e, "android/os/Build$VERSION");
    if (!version) return 0;
    jfieldID sdk_field = (*e)->GetStaticFieldID(e, version, "SDK_INT", "I");
    if (!sdk_field) return 0;
    int sdk = (*e)->GetStaticIntField(e, version, sdk_field);
    m = method(e, pm, "getPackageInfo", "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");
    if (!m) return 0;
    jobject info = (*e)->CallObjectMethod(e, pm, m, name, sdk >= 28 ? 0x08000000 : 64); STOP_IF_ERROR();
    if (!info) return 0;
    jclass cls = (*e)->GetObjectClass(e, info);
    jobjectArray signers;
    if (sdk >= 28) {
        jfieldID f = (*e)->GetFieldID(e, cls, "signingInfo", "Landroid/content/pm/SigningInfo;");
        if (!f) return 0;
        jobject signing = (*e)->GetObjectField(e, info, f);
        if (!signing) return 0;
        m = method(e, signing, "getApkContentsSigners", "()[Landroid/content/pm/Signature;");
        if (!m) return 0;
        signers = (*e)->CallObjectMethod(e, signing, m); STOP_IF_ERROR();
    } else {
        jfieldID f = (*e)->GetFieldID(e, cls, "signatures", "[Landroid/content/pm/Signature;");
        if (!f) return 0;
        signers = (*e)->GetObjectField(e, info, f);
    }
    if (!signers || (*e)->GetArrayLength(e, signers) != 1) return 0;
    jobject signer = (*e)->GetObjectArrayElement(e, signers, 0);
    m = method(e, signer, "toByteArray", "()[B");
    if (!m) return 0;
    jbyteArray cert = (*e)->CallObjectMethod(e, signer, m); STOP_IF_ERROR();
    jclass md = (*e)->FindClass(e, "java/security/MessageDigest");
    if (!md) return 0;
    m = (*e)->GetStaticMethodID(e, md, "getInstance", "(Ljava/lang/String;)Ljava/security/MessageDigest;");
    if (!m) return 0;
    jstring algo = (*e)->NewStringUTF(e, "SHA-256");
    jobject digest = (*e)->CallStaticObjectMethod(e, md, m, algo); STOP_IF_ERROR();
    m = method(e, digest, "digest", "([B)[B");
    if (!m) return 0;
    jbyteArray bytes = (*e)->CallObjectMethod(e, digest, m, cert); STOP_IF_ERROR();
    if (!bytes || (*e)->GetArrayLength(e, bytes) != 32) return 0;
    jbyte hash[32]; (*e)->GetByteArrayRegion(e, bytes, 0, 32, hash); STOP_IF_ERROR();
    const char hex[] = "0123456789abcdef";
    unsigned diff = 0;
    for (int i = 0; i < 32; ++i) {
        unsigned b = (unsigned char) hash[i];
        diff |= (unsigned)(hex[b >> 4] ^ official[i * 2]);
        diff |= (unsigned)(hex[b & 15] ^ official[i * 2 + 1]);
    }
    if (diff) return 0;
    return verify_content_seal(e, context);
}

static int verify(JNIEnv *e, jobject context) {
    if (!DIPLAY_ENFORCE) return 1;
    if (atomic_load(&trusted)) return 1;
    pthread_mutex_lock(&trust_lock);
    int ok = atomic_load(&trusted);
    if (!ok && !(*e)->PushLocalFrame(e, 96)) {
        ok = verify_installation(e, context);
        (*e)->PopLocalFrame(e, NULL);
        if (ok) atomic_store(&trusted, 1);
    }
    pthread_mutex_unlock(&trust_lock);
    return ok;
}

static const char warning_text[] = "本软件为免费软件，若遇收费必为骗子，谨防上当！抖音关注“卖捣蛋的小男孩（66857839104）”";

static int require_trust(JNIEnv *e, jobject context) {
    if (verify(e, context)) return 1;
    if ((*e)->ExceptionCheck(e)) (*e)->ExceptionClear(e);
    jclass ex = (*e)->FindClass(e, "java/lang/SecurityException");
    if (ex) (*e)->ThrowNew(e, ex, "Connection initialization failed");
    return 0;
}

static jstring integrity_warning(JNIEnv *e, jobject self, jobject context) {
    (void)self;
    if (integrity_warning_delay(e, self) > 0) return NULL;
    if (verify(e, context)) return NULL;
    if ((*e)->ExceptionCheck(e)) (*e)->ExceptionClear(e);
    return (*e)->NewStringUTF(e, warning_text);
}

static jbyteArray integrity_sign(JNIEnv *e, jobject self, jobject context, jobject key, jbyteArray data) {
    (void)self;
    if (!require_trust(e, context)) return NULL;
    if (!data || (*e)->GetArrayLength(e, data) != 32) {
        jclass ex = (*e)->FindClass(e, "java/lang/IllegalArgumentException");
        if (ex) (*e)->ThrowNew(e, ex, "Expected a 32-byte digest");
        return NULL;
    }
    jclass cls = (*e)->FindClass(e, "java/security/Signature");
    if (!cls) return NULL;
    jmethodID m = (*e)->GetStaticMethodID(e, cls, "getInstance", "(Ljava/lang/String;)Ljava/security/Signature;");
    if (!m) return NULL;
    jstring algo = (*e)->NewStringUTF(e, "NONEwithECDSA");
    jobject signer = (*e)->CallStaticObjectMethod(e, cls, m, algo);
    if ((*e)->ExceptionCheck(e)) return NULL;
    m = method(e, signer, "initSign", "(Ljava/security/PrivateKey;)V");
    if (!m) return NULL;
    (*e)->CallVoidMethod(e, signer, m, key);
    if ((*e)->ExceptionCheck(e)) return NULL;
    m = method(e, signer, "update", "([B)V");
    if (!m) return NULL;
    (*e)->CallVoidMethod(e, signer, m, data);
    if ((*e)->ExceptionCheck(e)) return NULL;
    m = method(e, signer, "sign", "()[B");
    return m ? (*e)->CallObjectMethod(e, signer, m) : NULL;
}

static jboolean integrity_carlink(JNIEnv *e, jobject self, jobject context, jobject adapter, jobject config, jobject callback) {
    (void)self;
    if (!require_trust(e, context)) return JNI_FALSE;
    jmethodID m = method(e, adapter, "init", "(Landroid/content/Context;Lcom/ucar/vehiclesdk/UCarConfig;Lcom/ucar/vehiclesdk/ICarInitCallback;)Z");
    return m ? (*e)->CallBooleanMethod(e, adapter, m, context, config, callback) : JNI_FALSE;
}

/* Export only the loader hook; operation names are not exported ELF symbols. */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    loaded_at_ms = monotonic_ms();
    if (loaded_at_ms < 0) return JNI_ERR;
    JNIEnv *e = NULL;
    if ((*vm)->GetEnv(vm, (void **)&e, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass cls = (*e)->FindClass(e, "com/shilapi/xcertplay/security/NativeIntegrity");
    if (!cls) return JNI_ERR;
    const JNINativeMethod methods[] = {
        {"warningDelayMillis", "()J", (void *)integrity_warning_delay},
        {"warning", "(Landroid/content/Context;)Ljava/lang/String;", (void *)integrity_warning},
        {"sign", "(Landroid/content/Context;Ljava/security/PrivateKey;[B)[B", (void *)integrity_sign},
        {"initializeCarlink", "(Landroid/content/Context;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z", (void *)integrity_carlink},
    };
    int result = (*e)->RegisterNatives(e, cls, methods, sizeof(methods) / sizeof(methods[0]));
    (*e)->DeleteLocalRef(e, cls);
    return result == JNI_OK ? JNI_VERSION_1_6 : JNI_ERR;
}
