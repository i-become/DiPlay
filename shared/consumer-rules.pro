# JNI entry points and SDK calls referenced from the integrity library.
-keep class com.shilapi.xcertplay.security.NativeIntegrity { *; }
-keep class com.ucar.vehiclesdk.UCarAdapter { public boolean init(android.content.Context, com.ucar.vehiclesdk.UCarConfig, com.ucar.vehiclesdk.ICarInitCallback); }
-keep class com.ucar.vehiclesdk.UCarConfig { *; }
-keep interface com.ucar.vehiclesdk.ICarInitCallback { *; }
