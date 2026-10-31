package ru.secondmemory;
import android.content.Context;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;

/** Durable PCM queue. Capture never waits for neural inference and never drops audio. */
final class SpeechQueue implements AutoCloseable {
 private final Context context;private final NeuralSpeech speech;private final ExecutorService executor=Executors.newSingleThreadExecutor();
 final AtomicInteger pending=new AtomicInteger();final Set<File> touched=Collections.synchronizedSet(new HashSet<>());
 volatile String error="";
 SpeechQueue(Context c,NeuralSpeech speech){context=c;speech=speech;}
 void recover(){File root=new File(context.getFilesDir(),"sessions");File[] dirs=root.listFiles(File::isDirectory);if(dirs==null)return;Arrays.sort(dirs,Comparator.comparing(File::getName));for(File dir:dirs){File[] files=dir.listFiles((d,n)->n.endsWith(".pending.pcm"));if(files==null)continue;Arrays.sort(files,Comparator.comparing(File::getName));for(File f:files)submit(f);}}
 void offer(File session,float[] samples,long recorded)throws IOException{
  File file=new File(session,"speech_"+recorded+"_"+UUID.randomUUID()+".pending.pcm"),temp=new File(file.getPath()+".tmp");
  try(FileOutputStream out=new FileOutputStream(temp)){byte[] pcm=new byte[samples.length*2];for(int n=0;n<samples.length;n++){int v=Math.max(-32768,Math.min(32767,Math.round(samples[n]*32768f)));pcm[2*n]=(byte)v;pcm[2*n+1]=(byte)(v>>8);}out.write(pcm);out.getFD().sync();}
  if(!temp.renameTo(file))throw new IOException("Не удалось сохранить речевой фрагмент");submit(file);
 }
 private void submit(File file){pending.incrementAndGet();executor.submit(()->{try{
   if(file.length()>16000*2*30L)throw new IOException("Слишком длинный фрагмент. Оригинальное аудио сохранено.");
   byte[] bytes;try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);bytes=out.toByteArray();}
   long started=System.currentTimeMillis();String text=speech.transcribe(NeuralSpeech.samples(bytes,bytes.length));
   if(!text.isEmpty()){
    long recorded=Long.parseLong(file.getName().split("_")[1]);JSONObject data=new JSONObject().put("text",text).put("engine","gigaam-v3").put("segment_id",file.getName()).put("recorded_at",recorded);
    String saved=TranscriptStore.save(context,file.getParentFile(),file.getParentFile().getName(),data);
    if(!saved.isEmpty())RecorderService.phraseCount++;RecorderService.transcript=context.getSharedPreferences("display",0).getString("recent","");
   }
   touched.add(file.getParentFile());if(!file.delete())throw new IOException("Не удалось отметить обработанный фрагмент");
   context.getSharedPreferences("speech",0).edit().putString("last_engine",NeuralSpeech.NAME).putLong("last_ms",System.currentTimeMillis()-started).remove("last_error").apply();
  }catch(Exception|LinkageError|OutOfMemoryError e){error="Ошибка GigaAM: "+e.getMessage()+". Фрагмент сохранён для повторного распознавания.";context.getSharedPreferences("speech",0).edit().putString("last_error",error).apply();}finally{pending.decrementAndGet();RecorderService.speechPending=pending.get();}});RecorderService.speechPending=pending.get();}
 public void close(){executor.shutdown();boolean interrupted=false;while(true){try{if(executor.awaitTermination(1,TimeUnit.SECONDS))break;RecorderService.message="Микрофон выключен. GigaAM обрабатывает фрагменты: "+pending.get();}catch(InterruptedException e){interrupted=true;}}if(interrupted)Thread.currentThread().interrupt();}
}
