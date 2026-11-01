package ru.secondmemory;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONObject;

/** Journals remain the source of truth; durable ack files are scoped to the receiver. */
final class ArchiveOutbox {
 private final File root;
 ArchiveOutbox(Context c){root=new File(c.getFilesDir(),"archive-outbox");}
 private File events(){return new File(root,"events");}
 void enqueue(JSONObject envelope)throws Exception{synchronized(ArchiveOutbox.class){
  File dir=events();if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Не удалось создать очередь архива");
  File file=new File(dir,envelope.getString("segment_id")+".json");
  if(file.exists()){if(!ArchiveEnvelope.hash(read(file)).equals(ArchiveEnvelope.hash(envelope)))throw new IOException("Конфликт фрагмента архива; исходная запись сохранена");return;}
  write(file,envelope.toString());}
 }
 int recover(Context context)throws Exception{
  File[] sessions=new File(context.getFilesDir(),"sessions").listFiles(File::isDirectory);if(sessions==null)return 0;
  Arrays.sort(sessions,Comparator.comparing(File::getName));int errors=0;
  android.content.SharedPreferences index=context.getSharedPreferences("archive_index",0);
  for(File dir:sessions){File journal=new File(dir,"transcript.jsonl");if(!journal.isFile())continue;
   String stamp=journal.length()+":"+journal.lastModified();if(stamp.equals(index.getString(dir.getName(),"")))continue;int before=errors;
   try(BufferedReader in=new BufferedReader(new InputStreamReader(new FileInputStream(journal),StandardCharsets.UTF_8))){String line;int n=0;while((line=in.readLine())!=null){n++;if(Thread.currentThread().isInterrupted())throw new InterruptedException();try{JSONObject e=ArchiveEnvelope.from(dir.getName(),new JSONObject(line),n);enqueue(e);}catch(org.json.JSONException|IllegalArgumentException|IOException e){errors++;}}}
   if(errors==before)index.edit().putString(dir.getName(),stamp).commit();
  }
  return errors;
 }
 private File ack(File event,String receiver)throws Exception{return new File(new File(root,"acks/"+ArchiveEnvelope.sha(receiver)),event.getName());}
 List<File> pending(String receiver,int limit)throws Exception{
  List<File> out=new ArrayList<>();File[] files=events().listFiles((d,n)->n.endsWith(".json"));if(files==null)return out;
  Arrays.sort(files,Comparator.comparing(File::getName));for(File f:files){if(!ack(f,receiver).isFile())out.add(f);if(out.size()>=limit)break;}return out;
 }
 void accepted(File event,String receiver,JSONObject envelope)throws Exception{
  File mark=ack(event,receiver);File parent=mark.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Не удалось сохранить подтверждение архива");write(mark,ArchiveEnvelope.hash(envelope));
 }
 static JSONObject read(File file)throws Exception{
  if(file.length()>256000)throw new IOException("Фрагмент архива слишком большой");
  try(InputStream in=new AtomicFile(file).openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>256000)throw new IOException("Фрагмент архива слишком большой");}return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}
 }
 private static void write(File f,String value)throws IOException{
  AtomicFile a=new AtomicFile(f);FileOutputStream out=null;try{out=a.startWrite();out.write(value.getBytes(StandardCharsets.UTF_8));a.finishWrite(out);}catch(IOException e){if(out!=null)a.failWrite(out);throw e;}
 }
}
