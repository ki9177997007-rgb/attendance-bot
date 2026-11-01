package ru.secondmemory;
import android.content.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.io.*;
import java.util.*;
import org.json.*;
import static org.junit.Assert.*;
public class VoiceAssistantTest {
 private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
 @Test public void realSpeakerRuntimeEmbedsAndVerifiesRepeatedSpeech()throws Exception{
  Context c=context();byte[] bytes;try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("test-speech.pcm");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);bytes=out.toByteArray();}
  android.content.SharedPreferences p=c.getSharedPreferences("voice",0);String old=p.getString("profile","");boolean enabled=p.getBoolean("enabled",false);
  try(OwnerVoice owner=new OwnerVoice(c)){float[] pcm=NeuralSpeech.samples(bytes,bytes.length),embedding=owner.embedding(pcm);assertTrue(embedding.length>=128);assertTrue(OwnerVoice.cosine(embedding,embedding)>.999);OwnerVoice.save(c,embedding);assertTrue(owner.accepts(c,pcm));try{owner.embedding(new float[32000]);fail("silence must be rejected");}catch(IOException expected){}}
  finally{p.edit().putString("profile",old).putBoolean("enabled",enabled).commit();}
 }
 @Test public void voiceTaskLifecycleUsesStableReferencesAndIsIdempotent()throws Exception{
  Context c=context();String suffix=UUID.randomUUID().toString();String id,other;long now=System.currentTimeMillis();
  try(TaskDb db=new TaskDb(c)){id=db.addReminder("Искусственная первая задача "+suffix,now+600000);other=db.addReminder("Искусственная вторая задача "+suffix,now+700000);}
  try{
   AssistantEngine.remember(c,new JSONArray().put(id).put(other),now);
   AssistantEngine.execute(c,VoiceIntent.parse("первое сделал"),now,"done-"+suffix,false);
   try(TaskDb db=new TaskDb(c)){assertTrue(db.get(id).completed);assertFalse(db.get(other).completed);}
   AssistantEngine.execute(c,VoiceIntent.parse("перенеси второе через час"),now,"snooze-"+suffix,false);
   long due;try(TaskDb db=new TaskDb(c)){due=db.get(other).due;assertEquals(now+3600000L,due);}
   AssistantEngine.execute(c,VoiceIntent.parse("перенеси второе через час"),now+1000,"snooze-"+suffix,false);
   try(TaskDb db=new TaskDb(c)){assertEquals(due,db.get(other).due);}
   AssistantEngine.execute(c,VoiceIntent.parse("напомни через десять минут проверить документы"),now,"remind-"+suffix,false);
   AssistantEngine.execute(c,VoiceIntent.parse("напомни через десять минут проверить документы"),now,"remind-"+suffix,false);
   String added=UUID.nameUUIDFromBytes(("voice:remind-"+suffix).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();try(TaskDb db=new TaskDb(c)){assertEquals(now+600000L,db.get(added).due);db.getWritableDatabase().delete("tasks","id=?",new String[]{added});}
   AssistantEngine.remember(c,new JSONArray().put(other),now-700000);
   AssistantEngine.execute(c,VoiceIntent.parse("первое сделал"),now,"expired-"+suffix,false);try(TaskDb db=new TaskDb(c)){assertFalse(db.get(other).completed);}
  }finally{ReminderReceiver.schedule(c,id,0);ReminderReceiver.schedule(c,other,0);try(TaskDb db=new TaskDb(c)){db.getWritableDatabase().delete("tasks","id=? OR id=?",new String[]{id,other});}}
 }
 @Test public void assistantResponsesAndCommandsCannotGenerateTasksOrSearchEvidence()throws Exception{
  Context c=context();String session="voice-filter-"+UUID.randomUUID();File dir=new File(c.getFilesDir(),"sessions/"+session);assertTrue(dir.mkdirs());
  TranscriptStore.save(c,dir,session,new JSONObject().put("text","Нужно отправить выдуманные документы").put("assistant_audio",true));
  TranscriptStore.save(c,dir,session,new JSONObject().put("text","Помощник напомни через час позвонить").put("assistant_command",true));
  assertEquals(0,ConversationAnalysis.read(dir).size());assertTrue(new File(dir,"transcript.txt").length()>0);try(TaskDb db=new TaskDb(c)){for(TaskDb.Row r:db.all())assertNotEquals(session,r.session);}
 }
 @Test public void commandsRemainOptInAndCannotUseExpiredFollowup(){Context c=context();c.getSharedPreferences("voice",0).edit().putBoolean("enabled",false).commit();assertFalse(AssistantEngine.candidate(c,"Помощник что делать",System.currentTimeMillis()));c.getSharedPreferences("voice",0).edit().putBoolean("enabled",true).commit();assertTrue(AssistantEngine.candidate(c,"Помощник что делать",System.currentTimeMillis()));VoiceOutput.followUntil=System.currentTimeMillis()+20000;assertTrue(AssistantEngine.candidate(c,"первое сделал",System.currentTimeMillis()));assertFalse(AssistantEngine.candidate(c,"первое сделал",System.currentTimeMillis()-60000));c.getSharedPreferences("voice",0).edit().putBoolean("enabled",false).commit();}
}
