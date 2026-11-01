package ru.secondmemory;
import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.json.*;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
public class FinalFlowTest {
 private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
 private Context c(){return inst.getTargetContext();}
 @Test public void unnamedReplyRequiresOneUnambiguousTask()throws Exception{String a,b;long now=System.currentTimeMillis();try(TaskDb db=new TaskDb(c())){a=db.addReminder("Искусственная задача A",0);b=db.addReminder("Искусственная задача B",0);}try{
  AssistantEngine.remember(c(),new JSONArray().put(a).put(b),now);AssistantEngine.execute(c(),VoiceIntent.parse("сделал"),now,"unclear-"+UUID.randomUUID(),false);try(TaskDb db=new TaskDb(c())){assertFalse(db.get(a).completed);assertFalse(db.get(b).completed);}
  AssistantEngine.remember(c(),new JSONArray().put(a),now);AssistantEngine.execute(c(),VoiceIntent.parse("сделал"),now,"clear-"+UUID.randomUUID(),false);try(TaskDb db=new TaskDb(c())){assertTrue(db.get(a).completed);assertFalse(db.get(b).completed);}
 }finally{try(TaskDb db=new TaskDb(c())){db.getWritableDatabase().delete("tasks","id=? OR id=?",new String[]{a,b});}}}
 @Test public void newVoiceTaskHasNoInventedDeadlineAndReplaysOnce()throws Exception{String event="add-"+UUID.randomUUID();String id=UUID.nameUUIDFromBytes(("voice:"+event).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();long now=System.currentTimeMillis();AssistantEngine.execute(c(),VoiceIntent.parse("Помощник запиши задачу проверить документы"),now,event,false);AssistantEngine.execute(c(),VoiceIntent.parse("Помощник запиши задачу проверить документы"),now,event,false);try(TaskDb db=new TaskDb(c())){TaskDb.Row row=db.get(id);assertNotNull(row);assertEquals("проверить документы",row.text);assertEquals(0,row.due);assertTrue(db.received(event));db.getWritableDatabase().delete("tasks","id=?",new String[]{id});}}
 @Test public void damagedJournalDoesNotHideValidConversationAndPhoneActionIsQueued()throws Exception{String session="torn-"+UUID.randomUUID();File dir=new File(c().getFilesDir(),"sessions/"+session);assertTrue(dir.mkdirs());try(FileOutputStream out=new FileOutputStream(new File(dir,"transcript.jsonl"))){out.write("{\"text\":\"оборванная строка".getBytes(java.nio.charset.StandardCharsets.UTF_8));}c().getSharedPreferences("commands",0).edit().clear().commit();TranscriptStore.save(c(),dir,session,new JSONObject().put("text","Помощник таймер на десять минут").put("assistant_command",true));assertEquals(1,CommandQueue.read(c()).length());assertTrue(ConversationAnalysis.read(dir).isEmpty());TranscriptStore.save(c(),dir,session,new JSONObject().put("text","Обсуждали отправку документов в пятницу"));assertEquals(1,ConversationAnalysis.read(dir).size());assertTrue(new String(java.nio.file.Files.readAllBytes(new File(dir,"transcript.jsonl").toPath()),java.nio.charset.StandardCharsets.UTF_8).contains("оборванная"));c().getSharedPreferences("commands",0).edit().clear().commit();}
 @Test public void reverseSyncCompletesCleanTaskButPreservesLocalEdit()throws Exception{String id;try(TaskDb db=new TaskDb(c())){id=db.addReminder("Искусственная синхронизация",System.currentTimeMillis()+600000);try{SyncEngine.applyRemote(c(),db,id,new JSONObject().put("completed",true));assertTrue(db.get(id).completed);TaskDb.Row row=db.get(id);db.edit(id,row.text,"",row.due,false);SyncEngine.applyRemote(c(),db,id,new JSONObject().put("completed",true));assertFalse("Unsynced local change must win",db.get(id).completed);}finally{ReminderReceiver.schedule(c(),id,0);db.getWritableDatabase().delete("tasks","id=?",new String[]{id});}}}
 @Test public void redesignedScreensAreReachableAndCaptureScreenshots()throws Exception{Class<?>[] screens={SettingsActivity.class,TasksActivity.class,VoiceSetupActivity.class,ReadinessActivity.class};String[] names={"settings-v13","tasks-v13","voice-v13","readiness-v13"};for(int i=0;i<screens.length;i++){Activity a=inst.startActivitySync(new Intent(c(),screens[i]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();Thread.sleep(700);String cmd="screencap -p /data/local/tmp/"+names[i]+".png";try(android.os.ParcelFileDescriptor fd=inst.getUiAutomation().executeShellCommand(cmd);InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] buf=new byte[512];while(in.read(buf)!=-1){}}inst.runOnMainSync(a::finish);}}
}
