package ru.secondmemory;
import android.app.job.*;
import android.content.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

public class AiJob extends JobService {
 private static final java.util.concurrent.atomic.AtomicBoolean active=new java.util.concurrent.atomic.AtomicBoolean();
 static boolean isActive(){return active.get();}
 static volatile String status="";
 private volatile boolean stopped;
 private Thread worker;
 static void schedule(Context c){c.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(405,new ComponentName(c,AiJob.class)).setMinimumLatency(2000).setPersisted(true).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());}
 @Override public boolean onStartJob(JobParameters p){
  stopped=false;worker=new Thread(()->{boolean more=false;
   if(!active.compareAndSet(false,true)){jobFinished(p,true);return;}
   try{if(RecorderService.running||RecorderService.stopping){more=true;return;}LocalModel.importPending(this);if(!LocalModel.verify(this))return;
    getSharedPreferences("ai",0).edit().remove("last_error").apply();
    File root=new File(getFilesDir(),"sessions");File[] dirs=root.listFiles(File::isDirectory);if(dirs==null)return;Arrays.sort(dirs,Comparator.comparing(File::getName));
    long start=System.currentTimeMillis();
    try(LocalAi ai=new LocalAi(LocalModel.file(this).getAbsolutePath());TaskDb db=new TaskDb(this)){
     for(File dir:dirs){File transcript=new File(dir,"transcript.jsonl");if(!transcript.exists())continue;
      String key=dir.getName();int cursor=getSharedPreferences("aiProgress",0).getInt(key,0);int index=0;String context="";
      try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(transcript),StandardCharsets.UTF_8))){String line;
       while((line=reader.readLine())!=null){index++;
        if(stopped||RecorderService.running||System.currentTimeMillis()-start>6*60000){more=true;return;}
        JSONObject o;try{o=new JSONObject(line);}catch(JSONException e){save(key,index);continue;}
        String text=o.optString("text","");
        if(index>cursor&&!text.isEmpty()){
         status="Разбираю запись "+key+", реплика "+index;
         long recorded=o.optLong("recorded_at",0);
         if(recorded==0)try{recorded=LocalDateTime.parse(key.substring(0,8)+" "+o.getString("saved_at"),java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss")).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();}catch(Exception e){recorded=transcript.lastModified();}
         if(text.length()>1800){status="Длинная реплика сохранена для ручной проверки";save(key,index);continue;}
         String result=ai.ask(AiResult.prompt(text,context,Staff.get(this),recorded,ZoneId.systemDefault()));
         if(stopped||RecorderService.running){more=true;return;}
         try{AiResult task=AiResult.parse(result,recorded,ZoneId.systemDefault());
          if(task!=null){long explicitDue=DueParser.parse(text,recorded,ZoneId.systemDefault());db.semantic(key,text,task.action,task.assignee,explicitDue,o.optBoolean("priority",false),recorded);ReminderReceiver.restore(this);}
         }catch(JSONException e){appendError(dir,index,"Некорректный ответ модели; проверь исходную реплику");}
         save(key,index);
        }
        context=(context+"\n"+text);if(context.length()>500)context=context.substring(context.length()-500);
       }
      }
     }
    }
    status="Локальный разбор завершён";ReminderReceiver.restore(this);SyncEngine.kick(this);
   }catch(Exception|LinkageError e){if(!stopped){status="Разбор текста остановлен: "+e.getMessage();getSharedPreferences("ai",0).edit().putString("last_error",status).apply();}}
   finally{active.set(false);if(!stopped)jobFinished(p,more);}
  },"local-ai");worker.start();return true;
 }
 private void save(String key,int index){getSharedPreferences("aiProgress",0).edit().putInt(key,index).commit();}
 private void appendError(File dir,int n,String message)throws IOException{try(FileOutputStream out=new FileOutputStream(new File(dir,"ai-review.txt"),true)){out.write((n+": "+message+"\n").getBytes(StandardCharsets.UTF_8));}}
 @Override public boolean onStopJob(JobParameters p){stopped=true;if(worker!=null)worker.interrupt();try{LocalAi.cancel();}catch(LinkageError ignored){}return true;}
}
