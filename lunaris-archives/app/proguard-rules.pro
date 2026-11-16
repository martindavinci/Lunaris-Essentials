# The JNI methods are registered by name in JNI_OnLoad.
-keepclasseswithmembernames class io.github.martindavinci.lunaris.archives.engine.NativeEngine { native <methods>; }
# engine_sink.c looks these up by name and signature.
-keep class io.github.martindavinci.lunaris.archives.engine.NativeSink {
    java.nio.ByteBuffer buffer;
    void onArchive(int, int[], int);
    boolean onEntry(long, byte[], boolean, int, long, boolean, long, boolean, boolean, byte[], boolean);
    int onEntryStart(long, byte[], long, boolean, long);
    int onData(int, long);
    boolean onEntryEnd(long, int);
    boolean onProgress(long);
    void onError(int, byte[]);
}
# Optional codecs Commons Compress refers to but the app never uses.
-dontwarn org.objectweb.asm.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
