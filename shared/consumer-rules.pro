# linux_i2c_jni.c creates this exception by its exact class name and constructor.
-keep class com.shilapi.xcertplay.transport.LinuxI2cNativeException {
    <init>(int, java.lang.String);
}
