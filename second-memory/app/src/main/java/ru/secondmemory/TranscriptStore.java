package ru.secondmemory;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
/** Every nonempty recognition result is saved immediately; no speaker enrollment gate. */
final class TranscriptStore {
 static String save(Context c,File session,String sessionId,JSONObject data)throws Exception {
  String text=data.optString("text","").trim();if(text.isEmpty())return "";
  long recorded=data.optLong("recorded_at",System.currentTimeMillis());String time=new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date(recorded));
  boolean duplicate=false;String segment=data.optString("segment_id","");if(segment.isEmpty()){segment=UUID.randomUUID().toString();data.put("segment_id",segment);}File journal=new File(session,"transcript.jsonl");if(journal.isFile()){try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(journal),StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null)if(l.contains("\"segment_id\":\""+segment+"\"")){duplicate=true;break;}}}
  data.remove("spk");data.remove("spk_frames");data.put("label","ВСЕ ГОЛОСА").put("priority",true).put("saved_at",time).put("recorded_at",recorded);
  String line=duplicate?"":"["+time+"] "+text+"\n";if(!duplicate){append(journal,data.toString()+"\n");append(new File(session,"transcript.txt"),line);}
  try{new ArchiveOutbox(c).enqueue(ArchiveEnvelope.from(sessionId,data,0));ArchiveJob.schedule(c);}catch(Exception e){c.getSharedPreferences("archive",0).edit().putString("last_error","Фрагмент сохранён в записи, очередь архива требует восстановления").apply();}
  boolean assistant=data.optBoolean("assistant_audio")||data.optBoolean("assistant_command");if(!assistant||(!data.optBoolean("assistant_audio")&&PhoneCommand.addressed(text)&&VoiceIntent.parse(text).kind==VoiceIntent.Kind.UNKNOWN))CommandQueue.offer(c,text,recorded);
  try(TaskDb db=new TaskDb(c)){if(!assistant&&!PhoneCommand.addressed(text)){for(String part:TaskRules.fragments(text))db.offer(sessionId,part,true,recorded);}}catch(RuntimeException e){append(new File(session,"task-errors.txt"),"Текст сохранён, ошибка очереди: "+e.getClass().getSimpleName()+"\n");}
  String recent=c.getSharedPreferences("display",0).getString("recent","")+line;if(recent.length()>6000)recent=recent.substring(recent.length()-6000);c.getSharedPreferences("display",0).edit().putString("recent",recent).apply();
  if(!assistant)c.getSharedPreferences("ai",0).edit().putBoolean("pending",true).apply();ReminderReceiver.restore(c);AiJob.schedule(c);return line;
 }
 private static void append(File f,String text)throws IOException{boolean separator=false;if(f.length()>0){try(RandomAccessFile check=new RandomAccessFile(f,"r")){check.seek(f.length()-1);separator=check.read()!=10;}}try(FileOutputStream out=new FileOutputStream(f,true)){if(separator)out.write(10);out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}}
}
