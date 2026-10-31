package ru.secondmemory;
import java.nio.charset.StandardCharsets;
final class LocalAi implements AutoCloseable {
 static {System.loadLibrary("memory_ai");}
 private long handle;
 private static native long open(byte[] path);
 private static native byte[] generate(long handle,byte[] prompt);
 private static native void free(long handle);
 static native void cancel();
 LocalAi(String path){handle=open(path.getBytes(StandardCharsets.UTF_8));if(handle==0)throw new IllegalStateException("Не удалось загрузить модель: возможно, недостаточно памяти");}
 String ask(String prompt){byte[] b=generate(handle,prompt.getBytes(StandardCharsets.UTF_8));if(b==null)throw new IllegalStateException("Разбор прерван или слишком длинный текст");return new String(b,StandardCharsets.UTF_8);}
 public void close(){if(handle!=0){free(handle);handle=0;}}
}
