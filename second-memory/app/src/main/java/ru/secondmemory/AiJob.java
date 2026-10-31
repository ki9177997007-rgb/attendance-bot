package ru.secondmemory;
import android.app.job.*;
import android.content.*;
import org.json.*;
import java.io.*;
import java.time.ZoneId;
import java.util.*;

/** Analyze every saved conversation in resumable batches, including non-task speech. */
public class AiJob extends JobService {
 private static final java.util.concurrent.atomic.AtomicBoolean active=new java.util.concurrent.atomic.AtomicBoolean();
 static boolean isActive(){return active.get();}
 static volatile String status="Анализ разговоров ещё не запущен";
 private volatile boolean stopped;private Thread worker;
 static void schedule(Context c){JobScheduler jobs=c.getSystemService(JobScheduler.class);if(active.get()||jobs.getPendingJob(405)!=null)return;jobs.schedule(new JobInfo.Builder(405,new ComponentName(c,AiJob.class)).setMinimumLatency(2000).setPersisted(true).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());}
 static void pauseForSpeech(){if(active.get())try{LocalAi.cancel();}catch(LinkageError ignored){}}
 @Override public boolean onStartJob(JobParameters parameters){stopped=false;worker=new Thread(()->{
  boolean more=false;if(!active.compareAndSet(false,true)){jobFinished(parameters,true);return;}
  try{
   if(RecorderService.stopping||RecorderService.speechPending>0){status="Жду завершения распознавания звука. Разговоры стоят в очереди.";more=true;return;}
   if(!LocalModel.ready(this)){if(RecorderService.running){status="Для анализа разговоров подготовь Qwen на экране «Анализ разговоров»";more=true;return;}LocalModel.importPending(this);if(!LocalModel.verify(this)){status="Для полного анализа нужна однократная загрузка Qwen. Открой «Анализ разговоров».";return;}}
   getSharedPreferences("ai",0).edit().remove("last_error").apply();File root=new File(getFilesDir(),"sessions");File[] dirs=root.listFiles(File::isDirectory);if(dirs==null)return;Arrays.sort(dirs,(a,b)->b.getName().compareTo(a.getName()));long started=System.currentTimeMillis();
   try(LocalAi ai=new LocalAi(LocalModel.file(this).getAbsolutePath());TaskDb db=new TaskDb(this)){
    boolean work=true;while(work){work=false;for(File dir:dirs){
     if(stopped||RecorderService.stopping||RecorderService.speechPending>0||System.currentTimeMillis()-started>5*60000){more=true;return;}
     List<ConversationAnalysis.Line> all=ConversationAnalysis.read(dir);if(all.isEmpty())continue;JSONObject state=ConversationAnalysis.load(dir);int cursor=state.optInt("cursor",0);List<ConversationAnalysis.Line> batch=ConversationAnalysis.batch(all,cursor);if(batch.isEmpty())continue;
     if(state.optInt("failures",0)>=3){status="Есть разговоры, требующие повторной проверки. Открой «Анализ разговоров».";continue;}
     if(RecorderService.running&&batch.stream().mapToInt(x->x.text.length()).sum()<800&&System.currentTimeMillis()-new File(dir,"transcript.jsonl").lastModified()<60000){more=true;continue;}
     work=true;status="Анализирую разговор "+dir.getName()+" · фрагменты "+batch.get(0).id+"–"+batch.get(batch.size()-1).id+" из "+all.size();
     try{
      String raw=ai.analyze(ConversationAnalysis.prompt(batch));if(stopped||RecorderService.speechPending>0){more=true;return;}JSONArray points=ConversationAnalysis.parse(raw,batch);JSONArray saved=state.getJSONArray("items");
      for(int i=0;i<points.length();i++){JSONObject point=points.getJSONObject(i);boolean duplicate=false;for(int j=0;j<saved.length();j++){JSONObject old=saved.getJSONObject(j);if(old.optString("kind").equals(point.getString("kind"))&&old.optString("quote").equals(point.getString("quote"))){duplicate=true;break;}}if(!duplicate)saved.put(point);
       if("task".equals(point.getString("kind"))){ConversationAnalysis.Line source=null;for(ConversationAnalysis.Line line:batch)if(line.id==point.getInt("line"))source=line;if(source!=null&&!PhoneCommand.addressed(source.text)&&TaskRules.candidate(source.text)){long due=DueParser.parse(source.text,source.recorded,ZoneId.systemDefault());db.semantic(dir.getName(),source.text,point.getString("text"),point.optString("owner"),due,true,source.recorded);}}
      }
      state.put("cursor",batch.get(batch.size()-1).id).put("total",all.size()).put("updated",System.currentTimeMillis()).put("failures",0).remove("error");ConversationAnalysis.save(dir,state);
     }catch(Exception e){if(stopped||RecorderService.speechPending>0){more=true;return;}state.put("error",e.getMessage()).put("failures",state.optInt("failures",0)+1);ConversationAnalysis.save(dir,state);status="Не удалось подтвердить разбор "+dir.getName()+". Исходные реплики сохранены.";more=true;}
     if(RecorderService.running){more=true;return;}
    }}
   }
   boolean blocked=false;for(File dir:dirs)if(ConversationAnalysis.load(dir).optInt("failures",0)>=3)blocked=true;status=blocked?"Разбор готов частично: есть записи, требующие повтора. Открой «Анализ разговоров».":"Анализ всех доступных разговоров завершён. Результаты — на экране «Анализ разговоров».";ReminderReceiver.restore(this);SyncEngine.kick(this);
  }catch(Exception|LinkageError|OutOfMemoryError e){if(!stopped){if(RecorderService.speechPending>0){status="Разбор приостановлен: приоритет у распознавания речи";more=true;}else{status="Анализ остановлен: "+e.getMessage();getSharedPreferences("ai",0).edit().putString("last_error",status).apply();}}}
  finally{active.set(false);if(!stopped)jobFinished(parameters,more);}
 },"conversation-analysis");worker.start();return true;}
 @Override public boolean onStopJob(JobParameters p){stopped=true;if(worker!=null)worker.interrupt();try{LocalAi.cancel();}catch(LinkageError ignored){}return true;}
}
