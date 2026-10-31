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
  String time=new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date());
  data.remove("spk");data.remove("spk_frames");data.put("label","ВСЕ ГОЛОСА").put("priority",true).put("saved_at",time).put("recorded_at",System.currentTimeMillis());
  append(new File(session,"transcript.jsonl"),data.toString()+"\n");String line="["+time+"] "+text+"\n";append(new File(session,"transcript.txt"),line);
  CommandQueue.offer(c,text);
  try(TaskDb db=new TaskDb(c)){if(!PhoneCommand.addressed(text))db.offer(sessionId,text,true);}catch(RuntimeException e){append(new File(session,"task-errors.txt"),"Текст сохранён, ошибка очереди: "+e.getClass().getSimpleName()+"\n");}
  String recent=c.getSharedPreferences("display",0).getString("recent","")+line;if(recent.length()>6000)recent=recent.substring(recent.length()-6000);c.getSharedPreferences("display",0).edit().putString("recent",recent).apply();
  ReminderReceiver.restore(c);return line;
 }
 private static void append(File f,String text)throws IOException{try(FileOutputStream out=new FileOutputStream(f,true)){out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}}
}
