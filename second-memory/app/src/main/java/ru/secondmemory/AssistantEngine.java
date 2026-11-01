package ru.secondmemory;
import android.content.*;
import android.app.NotificationManager;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
/** Runs only explicit, recent owner-verified commands; conversational audio stays independent. */
final class AssistantEngine {
 private static final ExecutorService worker=Executors.newSingleThreadExecutor();
 static boolean candidate(Context c,String text,long recorded){if(!c.getSharedPreferences("voice",0).getBoolean("enabled",false))return false;if(PhoneCommand.addressed(text))return true;return recorded<=VoiceOutput.followUntil&&recorded>=VoiceOutput.followUntil-20000&&VoiceIntent.parse(text).kind!=VoiceIntent.Kind.UNKNOWN;}
 static void offer(Context context,String text,float[] pcm,long recorded,String segment){Context c=context.getApplicationContext();if(System.currentTimeMillis()-recorded>120000||recorded>System.currentTimeMillis()+5000)return;worker.submit(()->{try{
  if(!c.getSharedPreferences("voice",0).getBoolean("enabled",false))return;if(!OwnerVoice.enrolled(c)){error(c,"Для голосовых команд сохрани образец голоса в настройках");return;}
  boolean accepted;try(OwnerVoice owner=new OwnerVoice(c)){accepted=owner.accepts(c,pcm);}if(!accepted){error(c,"Команда пропущена: голос не совпал с образцом. Повтори ближе к телефону");return;}
  c.getSharedPreferences("voice",0).edit().remove("last_error").apply();execute(c,VoiceIntent.parse(text),recorded,segment,true);
 }catch(Exception|LinkageError e){error(c,"Команда не выполнена: "+e.getMessage());}});}
 private static void error(Context c,String msg){c.getSharedPreferences("voice",0).edit().putString("last_error",msg).apply();RecorderService.message=msg;}
 static void execute(Context c,VoiceIntent cmd,long recorded,String segment,boolean speak)throws Exception{
  String answer="";JSONArray spokenIds=null;long now=System.currentTimeMillis();
  if(cmd.kind==VoiceIntent.Kind.QUIET){VoiceOutput.quiet();return;}
  if(cmd.kind==VoiceIntent.Kind.REPEAT){answer=c.getSharedPreferences("voice",0).getString("last_answer","Пока нечего повторять. Скажи: что делать?");}
  else if(cmd.kind==VoiceIntent.Kind.LIST){try(TaskDb db=new TaskDb(c)){List<TaskDb.Row> rows=new ArrayList<>();for(TaskDb.Row r:db.all())if(!r.completed)rows.add(r);rows.sort((a,b)->{long da=a.due==0?Long.MAX_VALUE:a.due,dd=b.due==0?Long.MAX_VALUE:b.due;int n=Long.compare(da,dd);return n==0?Long.compare(a.created,b.created):n;});JSONArray ids=new JSONArray();StringBuilder out=new StringBuilder(rows.isEmpty()?"Открытых поручений нет.":"Открытых поручений: "+rows.size()+". ");for(int i=0;i<Math.min(5,rows.size());i++){TaskDb.Row r=rows.get(i);ids.put(r.id);out.append(i+1).append(". ").append(r.text);if(r.due>0)out.append(". Срок: ").append(date(r.due));out.append(". ");}spokenIds=ids;answer=out.toString();}}
  else if(cmd.kind==VoiceIntent.Kind.DONE||cmd.kind==VoiceIntent.Kind.SNOOZE){
   android.content.SharedPreferences p=c.getSharedPreferences("voice",0);JSONArray ids=new JSONArray(p.getString("last_ids","[]"));long at=p.getLong("last_list_at",0);int index=cmd.ordinal<0&&ids.length()==1?0:cmd.ordinal;if(now-at>600000||index<0||index>=ids.length()){answer="Сначала спроси: что делать? Затем назови номер из этого списка.";}
   else {String id=ids.getString(index);try(TaskDb db=new TaskDb(c)){TaskDb.Row r=db.get(id);if(r==null||r.completed){answer="Этот пункт уже выполнен или недоступен. Спроси новый список.";}else if(db.received(segment)){return;}else {boolean done=cmd.kind==VoiceIntent.Kind.DONE;long due=done?r.due:DueParser.parse(cmd.text,recorded,ZoneId.systemDefault());if(!done&&due<=now)answer="Назови новое время: перенеси второе через час или завтра в девять.";else{db.getWritableDatabase().beginTransaction();try{db.edit(id,r.text,r.assignee,due,done);db.receipt(segment);db.getWritableDatabase().setTransactionSuccessful();}finally{db.getWritableDatabase().endTransaction();}ReminderReceiver.schedule(c,id,done?0:due);c.getSystemService(NotificationManager.class).cancel(id,2);SyncEngine.kick(c);answer=done?"Выполнено: "+r.text:"Перенесла на "+date(due)+": "+r.text;}}}}
  }
  else if(cmd.kind==VoiceIntent.Kind.REMINDER||cmd.kind==VoiceIntent.Kind.ADD){long due=DueParser.parse(cmd.text,recorded,ZoneId.systemDefault());if(cmd.kind==VoiceIntent.Kind.REMINDER&&due<=now)answer="Укажи время: напомни через десять минут позвонить Иванову. Без срока скажи: запиши задачу.";else{try(TaskDb db=new TaskDb(c)){String id=UUID.nameUUIDFromBytes(("voice:"+segment).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();if(db.received(segment))return;db.getWritableDatabase().beginTransaction();try{db.voiceReminder(id,cmd.kind==VoiceIntent.Kind.ADD?cmd.text.replaceFirst("^(?:запиши задачу|добавь задачу|задача) +", ""):cmd.text,due);db.receipt(segment);db.getWritableDatabase().setTransactionSuccessful();}finally{db.getWritableDatabase().endTransaction();}spokenIds=new JSONArray().put(id);ReminderReceiver.schedule(c,id,due);}SyncEngine.kick(c);answer=due>0?"Напомню "+date(due)+". "+cmd.text:"Сохранила задачу без срока. "+cmd.text.replaceFirst("^(?:запиши задачу|добавь задачу|задача) +", "");}}
  else if(cmd.kind==VoiceIntent.Kind.RECALL){long[] window=VoiceIntent.window(cmd.text,now,ZoneId.systemDefault());String query=cmd.text.replaceAll("\\b(?:вчера|сегодня|вспомни|найди)\\b","");List<MemorySearch.Hit> hits=MemorySearch.find(MemorySearch.load(new File(c.getFilesDir(),"sessions")),query,Collections.emptyList(),window[0],window[1]);answer=hits.isEmpty()?"Подходящий разговор не нашла. Попробуй назвать человека или тему подробнее.":"Нашла в записи от "+date(hits.get(0).doc.line.recorded)+". Цитата: "+hits.get(0).doc.line.text;}
  else if(cmd.kind==VoiceIntent.Kind.UNKNOWN&&phoneAction(cmd.text,recorded))answer="Команда телефона ждёт подтверждения. Открой уведомление или раздел команд в настройках.";
  else answer="Скажи: что делать; запиши задачу; напомни через десять минут позвонить; вспомни, что говорили про Иванова. После списка: первое сделал; перенеси второе через час.";
  if(!answer.isEmpty()){c.getSharedPreferences("voice",0).edit().putString("last_answer",answer).apply();RecorderService.message=answer;if(speak)VoiceOutput.speakTasks(c,answer,spokenIds);else if(spokenIds!=null)remember(c,spokenIds,now);}
 }
 private static boolean phoneAction(String text,long recorded){try{PhoneCommand c=PhoneCommand.parse(text,recorded,ZoneId.systemDefault());return c.kind!=PhoneCommand.Kind.REMINDER;}catch(IllegalArgumentException e){return false;}}
 static void remember(Context c,JSONArray ids,long at){c.getSharedPreferences("voice",0).edit().putString("last_ids",ids.toString()).putLong("last_list_at",at).commit();}
 static String date(long millis){return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMMM в HH:mm",new Locale("ru")));}
}
