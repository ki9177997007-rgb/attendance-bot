package ru.secondmemory;
import android.content.*;
import org.json.*;
import java.io.*;
import java.time.ZoneId;
import java.util.*;
import java.util.function.BooleanSupplier;
/** One shared worker for scheduled and user-started analysis. Commits each batch before continuing. */
final class AnalysisWork {
 static volatile Thread owner;
 static boolean runPass(Context context,BooleanSupplier stop,File scope){boolean more=false;if(!AiJob.active.compareAndSet(false,true))return true;owner=Thread.currentThread();try{
   if(stop.getAsBoolean())return false;
   if(MemoryActivity.searching.get()||RecorderService.stopping||RecorderService.speechPending>2){AiJob.status="Жду завершения распознавания звука. Разговоры стоят в очереди.";more=true;return more;}
   if(!LocalModel.ready(context)){AiJob.status="Проверяю файл нейросети…";LocalModel.importPending(context);if(!LocalModel.verify(context)){AiJob.status="Для полного анализа нужна однократная загрузка Qwen. Открой «Анализ разговоров».";return more;}}
   context.getSharedPreferences("ai",0).edit().remove("last_error").apply();File root=new File(context.getFilesDir(),"sessions");File[] dirs=scope==null?root.listFiles(File::isDirectory):new File[]{scope};if(dirs==null)return false;Arrays.sort(dirs,(a,b)->b.getName().compareTo(a.getName()));long started=System.currentTimeMillis();
   boolean pending=false,blocked=false,waiting=false;for(File dir:dirs){List<ConversationAnalysis.Line> lines=ConversationAnalysis.read(dir);JSONObject state=ConversationAnalysis.load(dir);List<ConversationAnalysis.Line> todo=ConversationAnalysis.batch(lines,state.optInt("cursor",0));if(!todo.isEmpty()){if(RecorderService.running&&todo.stream().mapToInt(x->x.text.length()).sum()<800&&System.currentTimeMillis()-new File(dir,"transcript.jsonl").lastModified()<60000){waiting=true;continue;}if(state.optInt("failures",0)>=3)blocked=true;else pending=true;}}
   if(!pending&&waiting){AiJob.status="Жду окончания короткого речевого фрагмента…";return true;}
   if(!pending){AiJob.status=blocked?"Есть ошибки разбора. Нажми «Повторить ошибки».":"Все доступные разговоры разобраны";return false;}
   if(stop.getAsBoolean())return false;
   try(LocalAi ai=new LocalAi(LocalModel.file(context).getAbsolutePath());TaskDb db=new TaskDb(context)){
    boolean work=true;while(work){work=false;for(File dir:dirs){
     if(stop.getAsBoolean()||MemoryActivity.searching.get()||RecorderService.stopping||RecorderService.speechPending>2||System.currentTimeMillis()-started>5*60000){more=true;return more;}
     List<ConversationAnalysis.Line> all=ConversationAnalysis.read(dir);if(all.isEmpty())continue;JSONObject state=ConversationAnalysis.load(dir);int cursor=state.optInt("cursor",0);List<ConversationAnalysis.Line> batch=ConversationAnalysis.batch(all,cursor);if(batch.isEmpty())continue;
     if(state.optInt("failures",0)>=3){AiJob.status="Есть разговоры, требующие повторной проверки. Открой «Анализ разговоров».";continue;}
     if(RecorderService.running&&batch.stream().mapToInt(x->x.text.length()).sum()<800&&System.currentTimeMillis()-new File(dir,"transcript.jsonl").lastModified()<60000){more=true;continue;}
     work=true;AiJob.status="Анализирую разговор "+dir.getName()+" · фрагменты "+batch.get(0).id+"–"+batch.get(batch.size()-1).id+" из "+all.size();
     try{
      String raw=ai.analyze(ConversationAnalysis.prompt(batch));if(stop.getAsBoolean()||MemoryActivity.searching.get()||RecorderService.speechPending>2){more=true;return more;}JSONArray points=ConversationAnalysis.parse(raw,batch);JSONArray saved=state.getJSONArray("items");
      for(int i=0;i<points.length();i++){JSONObject point=points.getJSONObject(i);boolean duplicate=false;for(int j=0;j<saved.length();j++){JSONObject old=saved.getJSONObject(j);if(old.optString("kind").equals(point.getString("kind"))&&old.optString("quote").equals(point.getString("quote"))){duplicate=true;break;}}if(!duplicate)saved.put(point);
       if("task".equals(point.getString("kind"))){ConversationAnalysis.Line source=null;for(ConversationAnalysis.Line line:batch)if(line.id==point.getInt("line"))source=line;if(source!=null&&!PhoneCommand.addressed(source.text)&&TaskRules.candidate(source.text)){long due=DueParser.parse(source.text,source.recorded,ZoneId.systemDefault());db.semantic(dir.getName(),source.text,point.getString("text"),point.optString("owner"),due,true,source.recorded);}}
      }
      state.put("cursor",batch.get(batch.size()-1).id).put("total",all.size()).put("updated",System.currentTimeMillis()).put("failures",0).remove("error");ConversationAnalysis.save(dir,state);
     }catch(Exception e){if(stop.getAsBoolean()||MemoryActivity.searching.get()||RecorderService.speechPending>2){more=true;return more;}state.put("error",e.getMessage()).put("failures",state.optInt("failures",0)+1);ConversationAnalysis.save(dir,state);AiJob.status="Не удалось подтвердить разбор "+dir.getName()+". Исходные реплики сохранены.";more=true;}
     if(RecorderService.running){more=true;return more;}
    }}
   }
   if(more)return true;
   blocked=false;for(File dir:dirs)if(ConversationAnalysis.load(dir).optInt("failures",0)>=3)blocked=true;AiJob.status=blocked?"Разбор готов частично: есть записи, требующие повтора. Открой «Анализ разговоров».":"Анализ всех доступных разговоров завершён. Результаты — на экране «Анализ разговоров».";ReminderReceiver.restore(context);SyncEngine.kick(context);
  }catch(Exception|LinkageError|OutOfMemoryError e){if(!stop.getAsBoolean()){if(RecorderService.speechPending>2){AiJob.status="Разбор приостановлен: приоритет у распознавания речи";more=true;}else{AiJob.status="Анализ остановлен: "+e.getMessage();context.getSharedPreferences("ai",0).edit().putString("last_error",AiJob.status).apply();}}}
  finally{owner=null;AiJob.active.set(false);}return more;
 }
}
