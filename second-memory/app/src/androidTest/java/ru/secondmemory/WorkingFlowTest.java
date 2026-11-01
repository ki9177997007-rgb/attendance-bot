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
  byte[] pcm;try(InputStream in=inst.getContext().getAssets().open("test-speech.pcm");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);pcm=out.toByteArray();}
  String text;try(NeuralSpeech speech=new NeuralSpeech(c)){text=speech.transcribe(NeuralSpeech.samples(pcm,pcm.length));}
  assertTrue("GigaAM must emit Russian text without an extra download",text.trim().length()>3);
  File model=ModelAssets.unpack(c,"ru-small-0.22");StringBuilder old=new StringBuilder();
  try(Model m=new Model(model.getPath());Recognizer rec=new Recognizer(m,16000)){for(int p=0;p<pcm.length;p+=6400){int n=Math.min(6400,pcm.length-p);byte[] chunk=java.util.Arrays.copyOfRange(pcm,p,p+n);if(rec.acceptWaveForm(chunk,n))old.append(new JSONObject(rec.getResult()).optString("text","")).append(' ');}old.append(new JSONObject(rec.getFinalResult()).optString("text",""));}
  String reference="нужно отправить документы завтра напомни позвонить в больницу проверка связи";
  double before=wer(reference,old.toString()),after=wer(reference,text);
  String report="Reference: "+reference+"\nVosk: "+old+"\nGigaAM v3: "+text+"\nVosk WER: "+before+"\nGigaAM WER: "+after+"\nSynthetic Russian fixture; not a guarantee for live conversations.\n";
  android.util.Log.i("SpeechQuality",report);
  try(OutputStream out=new FileOutputStream(new File(c.getFilesDir(),"speech-quality.txt"))){out.write(report.getBytes(StandardCharsets.UTF_8));}
  try(android.os.ParcelFileDescriptor fd=inst.getUiAutomation().executeShellCommand("run-as "+c.getPackageName()+" cat files/speech-quality.txt > /data/local/tmp/speech-quality.txt")){try(InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] buf=new byte[256];while(in.read(buf)!=-1){}}}
  assertTrue("GigaAM must improve the old recognizer on the fixed fixture: "+report,after<before||after==0);
  assertTrue("Too many errors on the clean fixture: "+report,after<=0.25);
  String id="instrumentation-"+System.nanoTime();File session=new File(c.getFilesDir(),"sessions/"+id);assertTrue(session.mkdirs());
  TranscriptStore.save(c,session,id,new JSONObject().put("text",text).put("engine","gigaam-v3"));assertTrue(new File(session,"transcript.txt").length()>0);
  try(TaskDb db=new TaskDb(c)){boolean found=false;for(TaskDb.Row row:db.all())if(id.equals(row.session))found=true;assertTrue(found);}
  assertFalse(c.getSharedPreferences("voice",0).contains("embedding"));
 }
 private double wer(String expected,String actual){String[] a=TaskRules.normalize(expected).split(" "),b=TaskRules.normalize(actual).split(" ");int[][] d=new int[a.length+1][b.length+1];for(int i=0;i<=a.length;i++)d[i][0]=i;for(int j=0;j<=b.length;j++)d[0][j]=j;for(int i=1;i<=a.length;i++)for(int j=1;j<=b.length;j++)d[i][j]=Math.min(Math.min(d[i-1][j]+1,d[i][j-1]+1),d[i-1][j-1]+(a[i-1].equals(b[j-1])?0:1));return d[a.length][b.length]/(double)a.length;}

 @Test public void reminderActionsPersistAndCommandQueueDoesNotExecuteAudio()throws Exception{
  Context c=inst.getTargetContext();String id;
  try(TaskDb db=new TaskDb(c)){id=db.addReminder("Проверка напоминания",System.currentTimeMillis()+600000);}
  new ReminderReceiver().onReceive(c,new Intent(c,ReminderReceiver.class).setAction("SNOOZE").putExtra("id",id));
  try(TaskDb db=new TaskDb(c)){TaskDb.Row row=db.get(id);assertFalse(row.completed);assertFalse(row.notified);assertTrue(row.due>System.currentTimeMillis()+590000);}
  new ReminderReceiver().onReceive(c,new Intent(c,ReminderReceiver.class).setAction("DONE").putExtra("id",id));
  try(TaskDb db=new TaskDb(c)){assertTrue(db.get(id).completed);}
  c.getSharedPreferences("commands",0).edit().clear().commit();
  CommandQueue.offer(c,"Память таймер на десять минут");assertEquals(1,CommandQueue.read(c).length());
  CommandQueue.offer(c,"Олег поставь будильник в шесть");assertEquals(1,CommandQueue.read(c).length());
  JSONObject queued=CommandQueue.read(c).getJSONObject(0);CommandQueue.remove(c,queued.getString("text"),queued.getLong("at"));assertEquals(0,CommandQueue.read(c).length());
 }
 @Test public void phoneControlsScreenIsReachableAndDoesNotNeedNetwork(){
  Context c=inst.getTargetContext();Activity activity=inst.startActivitySync(new Intent(c,CommandsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();
  inst.runOnMainSync(()->{assertNotNull(button(activity.getWindow().getDecorView(),"Выполнить команду"));assertNotNull(button(activity.getWindow().getDecorView(),"Выбрать приложение"));activity.finish();});
 }

 @Test public void mainButtonIsReadableAtLargeFontAndScreenshots()throws Exception{
  Context c=inst.getTargetContext();
  try{
   for(String scale:new String[]{"1.0","1.8"}){
    try(android.os.ParcelFileDescriptor fd=inst.getUiAutomation().executeShellCommand("settings put system font_scale "+scale)){try(InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] buf=new byte[256];while(in.read(buf)!=-1){}}}
    Thread.sleep(1000);
    Activity a=inst.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();Thread.sleep(800);
    inst.runOnMainSync(()->{Button record=button(a.getWindow().getDecorView(),"ВКЛ · начать запись");assertNotNull(record);assertTrue(record.isShown());assertTrue("record button text must not be clipped",record.getHeight()>=record.getLayout().getHeight()+record.getCompoundPaddingTop()+record.getCompoundPaddingBottom());assertNotNull(button(a.getWindow().getDecorView(),"Настройки"));assertEquals("Settings label must not break inside a word",1,button(a.getWindow().getDecorView(),"Настройки").getLineCount());assertEquals("Tasks label must not break inside a word",1,button(a.getWindow().getDecorView(),"Поручения").getLineCount());});
    android.graphics.Bitmap image=inst.getUiAutomation().takeScreenshot();assertNotNull(image);File out=new File(c.getExternalFilesDir(null),"main-font-"+scale+".png");try(OutputStream stream=new FileOutputStream(out)){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}image.recycle();
    try(android.os.ParcelFileDescriptor fd=inst.getUiAutomation().executeShellCommand("screencap -p /data/local/tmp/main-font-"+scale+".png")){try(InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] buf=new byte[256];while(in.read(buf)!=-1){}}}

    inst.runOnMainSync(a::finish);inst.waitForIdleSync();
   }
  }finally{try(android.os.ParcelFileDescriptor fd=inst.getUiAutomation().executeShellCommand("settings put system font_scale 1.0")){try(InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] buf=new byte[256];while(in.read(buf)!=-1){}}}}
 }

 @Test public void importingRecordingsPreservesSource()throws Exception{
  Context c=inst.getTargetContext();String session="import-test-"+System.nanoTime();File dir=new File(c.getFilesDir(),"sessions/"+session);assertTrue(dir.mkdirs());File source=new File(dir,"transcript.txt");try(OutputStream out=new FileOutputStream(source)){out.write("Проверка импорта".getBytes(StandardCharsets.UTF_8));}
  android.net.Uri uri=android.provider.DocumentsContract.buildDocumentUri(RecordingsProvider.AUTHORITY,session+"/transcript.txt");File copied=RecordingImport.copy(c,uri);assertTrue(copied.isFile());assertTrue(source.isFile());assertEquals(source.length(),copied.length());assertNotEquals(source.getCanonicalPath(),copied.getCanonicalPath());
 }

 @Test public void neuralVadProducesSegmentsAndQueueSavesResults()throws Exception{
  Context c=inst.getTargetContext();byte[] pcm;try(InputStream in=inst.getContext().getAssets().open("test-speech.pcm");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);pcm=out.toByteArray();}
  com.k2fsa.sherpa.onnx.Vad vad=NeuralSpeech.detector(c);int segments=0;
  try{for(int p=0;p<pcm.length;p+=1024){byte[] b=java.util.Arrays.copyOfRange(pcm,p,Math.min(p+1024,pcm.length));vad.acceptWaveform(NeuralSpeech.samples(b,b.length));}vad.flush();while(!vad.empty()){assertTrue(vad.front().getSamples().length>1000);segments++;vad.pop();}}finally{vad.release();}assertTrue(segments>0);
  String id="neural-queue-"+System.nanoTime();File dir=new File(c.getFilesDir(),"sessions/"+id);assertTrue(dir.mkdirs());
  try(NeuralSpeech speech=new NeuralSpeech(c)){SpeechQueue queue=new SpeechQueue(c,speech);queue.offer(dir,NeuralSpeech.samples(pcm,pcm.length),System.currentTimeMillis());queue.close();assertEquals("",queue.error);assertEquals(0,queue.pending.get());}
  assertTrue(new File(dir,"transcript.txt").length()>0);assertEquals(0,dir.listFiles((d,n)->n.endsWith(".pending.pcm")).length);
 }

 @Test public void fullAnalysisOverviewShowsNonTaskFactsAndSources()throws Exception{
  Context c=inst.getTargetContext();String id="analysis-test-"+System.nanoTime();File dir=new File(c.getFilesDir(),"sessions/"+id);assertTrue(dir.mkdirs());
  try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(dir,"transcript.jsonl")),StandardCharsets.UTF_8)){out.write(new JSONObject().put("text","Решили заказать бумагу в понедельник.").put("saved_at","18:00:00").put("recorded_at",System.currentTimeMillis()).toString()+"\n");}
  java.util.List<ConversationAnalysis.Line> lines=ConversationAnalysis.read(dir);JSONObject item=new JSONObject().put("kind","decision").put("text","Заказать бумагу в понедельник").put("owner","").put("line",1).put("quote","Решили заказать бумагу в понедельник");JSONArray points=ConversationAnalysis.parse(new JSONObject().put("items",new JSONArray().put(item)).toString(),lines);ConversationAnalysis.save(dir,new JSONObject().put("cursor",1).put("items",points));
  Activity a=inst.startActivitySync(new Intent(c,AnalysisActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();java.util.concurrent.atomic.AtomicReference<Button> found=new java.util.concurrent.atomic.AtomicReference<>();
  for(int i=0;i<20&&found.get()==null;i++){Thread.sleep(250);inst.runOnMainSync(()->found.set(button(a.getWindow().getDecorView(),"Источник: "+id+" · 18:00:00")));}
  assertNotNull("Full-conversation decisions must be shown with a source button",found.get());inst.runOnMainSync(()->{assertNotNull(button(a.getWindow().getDecorView(),"Скопировать всю сводку"));assertNotNull(button(a.getWindow().getDecorView(),"Подготовить ИИ / начать анализ"));a.finish();});
 }
 @Test public void forgottenFragmentSearchFindsOldConversationWithoutDateOrAnalysis()throws Exception{
  Context c=inst.getTargetContext();String id="recall-old-"+System.nanoTime();File dir=new File(c.getFilesDir(),"sessions/"+id);assertTrue(dir.mkdirs());long when=java.time.LocalDate.now().minusDays(8).atTime(12,0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
  try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(dir,"transcript.jsonl")),StandardCharsets.UTF_8)){out.write(new JSONObject().put("text","Отправили заказным письмом, трек пока не сообщили.").put("saved_at","12:00:00").put("recorded_at",when).toString()+"\n");out.write(new JSONObject().put("text","Тогда запросим номер отправления завтра.").put("saved_at","12:01:00").put("recorded_at",when+60000).toString()+"\n");}
  Activity a=inst.startActivitySync(new Intent(c,MemoryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();String date=java.time.Instant.ofEpochMilli(when).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));java.util.concurrent.atomic.AtomicReference<Button> found=new java.util.concurrent.atomic.AtomicReference<>();
  inst.runOnMainSync(()->{assertEquals("Период: всё время",button(a.getWindow().getDecorView(),"Период: всё время").getText().toString());android.widget.EditText edit=findEdit(a.getWindow().getDecorView());assertNotNull(edit);edit.setText("заказное писмо");button(a.getWindow().getDecorView(),"Найти").performClick();});
  for(int i=0;i<40&&found.get()==null;i++){Thread.sleep(200);inst.runOnMainSync(()->found.set(button(a.getWindow().getDecorView(),"Контекст разговора · "+date)));}
  assertNotNull("A fragment must find an eight-day-old raw transcript without prepared AI",found.get());
  inst.runOnMainSync(()->{assertNotNull(button(a.getWindow().getDecorView(),"Скопировать всё найденное"));found.get().performClick();});inst.waitForIdleSync();Thread.sleep(200);try(android.os.ParcelFileDescriptor fd=inst.getUiAutomation().executeShellCommand("screencap -p /data/local/tmp/recall-context.png")){new java.io.FileInputStream(fd.getFileDescriptor()).read();}inst.runOnMainSync(a::finish);
 }
 private android.widget.EditText findEdit(android.view.View view){if(view instanceof android.widget.EditText)return (android.widget.EditText)view;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){android.widget.EditText edit=findEdit(group.getChildAt(i));if(edit!=null)return edit;}}return null;}

}
