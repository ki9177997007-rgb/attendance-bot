package ru.secondmemory;
import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import org.json.*;
import org.vosk.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

@RunWith(AndroidJUnit4.class)
public class WorkingFlowTest {
 private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
 private Button button(View v,String text){if(v instanceof Button&&text.contentEquals(((Button)v).getText()))return (Button)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){Button b=button(g.getChildAt(i),text);if(b!=null)return b;}}return null;}
 @Test public void settingsAndYouGileAreReachableWithoutLongPress(){
  Context c=inst.getTargetContext();Activity main=inst.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();
  Instrumentation.ActivityMonitor monitor=inst.addMonitor(SettingsActivity.class.getName(),null,false);
  inst.runOnMainSync(()->{Button b=button(main.getWindow().getDecorView(),"Настройки");assertNotNull(b);assertTrue(b.isShown());b.performClick();});
  Activity settings=inst.waitForMonitorWithTimeout(monitor,5000);assertNotNull(settings);inst.removeMonitor(monitor);inst.waitForIdleSync();
  inst.runOnMainSync(()->{assertNotNull(button(settings.getWindow().getDecorView(),"Скачать / повторить загрузку"));assertNotNull(button(settings.getWindow().getDecorView(),"Выбрать скачанный файл модели"));});
  Instrumentation.ActivityMonitor loginMonitor=inst.addMonitor(ConnectActivity.class.getName(),null,false);
  inst.runOnMainSync(()->button(settings.getWindow().getDecorView(),"Подключить YouGile").performClick());
  Activity login=inst.waitForMonitorWithTimeout(loginMonitor,5000);assertNotNull(login);inst.removeMonitor(loginMonitor);
  inst.runOnMainSync(()->{assertNotNull(button(login.getWindow().getDecorView(),"Войти и подключить"));login.finish();settings.finish();main.finish();});
 }
 @Test public void russianSpeechWorksWithoutSpeakerModelAndSavesTask()throws Exception{
  Context c=inst.getTargetContext();c.getSharedPreferences("voice",0).edit().clear().commit();
  File model=ModelAssets.unpack(c,"ru-small-0.22");StringBuilder text=new StringBuilder();
  try(Model m=new Model(model.getPath());Recognizer rec=new Recognizer(m,16000);InputStream in=inst.getContext().getAssets().open("test-speech.pcm")){
   byte[] b=new byte[6400];int n;while((n=in.read(b))!=-1)if(rec.acceptWaveForm(b,n))text.append(new JSONObject(rec.getResult()).optString("text","")).append(' ');
   text.append(new JSONObject(rec.getFinalResult()).optString("text",""));
  }
  assertTrue("Vosk must emit text without a speaker model",text.toString().trim().length()>3);
  String id="instrumentation-"+System.nanoTime();File session=new File(c.getFilesDir(),"sessions/"+id);assertTrue(session.mkdirs());
  TranscriptStore.save(c,session,id,new JSONObject().put("text",text.toString()));assertTrue(new File(session,"transcript.txt").length()>0);
  TranscriptStore.save(c,session,id,new JSONObject().put("text","нужно отправить документы завтра"));
  try(TaskDb db=new TaskDb(c)){boolean found=false;for(TaskDb.Row row:db.all())if(id.equals(row.session))found=true;assertTrue("No enrollment gate may discard a task",found);}
  assertFalse(c.getSharedPreferences("voice",0).contains("embedding"));
 }
}
