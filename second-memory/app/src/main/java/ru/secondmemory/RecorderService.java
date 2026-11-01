package ru.secondmemory;
import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import com.k2fsa.sherpa.onnx.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class RecorderService extends Service {
 public static volatile boolean running,stopping;
 public static volatile String message="Готово к записи. Речь распознаёт встроенная GigaAM v3.",transcript="",partial="";
 public static volatile int phraseCount,micLevel,speechPending;
 private volatile boolean cancelled;private Thread worker;private PowerManager.WakeLock wake;private File session;private String sessionId;private int chunks;
 private final Handler main=new Handler(Looper.getMainLooper());
 @Override public IBinder onBind(Intent intent){return null;}
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(intent==null){stopSelf();return START_NOT_STICKY;}
  if("STOP".equals(intent.getAction())){cancelled=true;stopping=true;message="Выключаю микрофон и завершаю распознавание…";if(worker==null){running=false;stopping=false;stopSelf();}return START_NOT_STICKY;}
  if(running||stopping)return START_NOT_STICKY;
  cancelled=false;running=true;partial="";micLevel=0;phraseCount=0;speechPending=0;chunks=0;session=null;transcript=getSharedPreferences("display",0).getString("recent","");
  boolean retry="RETRY".equals(intent.getAction());
  try{
   NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("memory","Вторая память",NotificationManager.IMPORTANCE_LOW));
   PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
   PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,RecorderService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
   startForeground(1,new Notification.Builder(this,"memory").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle(retry?"GigaAM: повторное распознавание":"Вторая память включена").setContentText("GigaAM v3 · локальное распознавание русской речи").setContentIntent(open).setOngoing(true).addAction(android.R.drawable.ic_media_pause,"ВЫКЛ",stop).build());
   wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"SecondMemory:recognition");wake.acquire();
   message="Загружаю встроенную речевую нейросеть GigaAM v3…";worker=new Thread(()->capture(retry),"memory-capture");worker.start();
  }catch(Exception e){running=false;stopping=false;message="Не удалось запустить: "+e.getMessage();stopSelf();}
  return START_NOT_STICKY;
 }
 private void capture(boolean retry){AudioRecord microphone=null;WavWriter wav=null;NeuralSpeech speech=null;SpeechQueue queue=null;Vad vad=null;boolean failed=false;
  try{
   VoiceOutput.init(this);speech=new NeuralSpeech(this);if(cancelled)return;queue=new SpeechQueue(this,speech);queue.recover();
   if(retry){message="Повторно распознаю сохранённые фрагменты: "+queue.pending.get();return;}
   sessionId=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date());session=new File(getFilesDir(),"sessions/"+sessionId);if(!session.mkdirs())throw new IOException("Не удалось создать запись");
   vad=NeuralSpeech.detector(this);
   int minimum=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
   if(minimum<=0)throw new IOException("Микрофон не поддерживает 16 кГц");
   if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new IOException("Нет разрешения на микрофон");
   microphone=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(32000,minimum*2));
   if(microphone.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Микрофон недоступен");if(cancelled)return;
   microphone.startRecording();if(microphone.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IOException("Не удалось включить микрофон");
   wav=nextWav();long recorded=System.currentTimeMillis(),lastSync=SystemClock.elapsedRealtime();byte[] buffer=new byte[1024];message="Запись включена. Речь распознаёт GigaAM v3.";
   while(!cancelled){
    int n=microphone.read(buffer,0,buffer.length);if(n<0)throw new IOException("Ошибка микрофона: "+n);if(n==0)continue;wav.write(buffer,n);
    float[] samples=NeuralSpeech.samples(buffer,n);double energy=0;for(float f:samples)energy+=f*f;micLevel=Math.min(100,(int)(Math.sqrt(energy/Math.max(1,samples.length))*327));
    vad.acceptWaveform(samples);drain(vad,queue,recorded);partial=vad.isSpeechDetected()?"GigaAM: слышу речь, жду короткую паузу…":queue.pending.get()>0?"GigaAM: обрабатываю фрагменты — "+queue.pending.get():"";
    if(!queue.error.isEmpty())message=queue.error;
    if(SystemClock.elapsedRealtime()-lastSync>60000){lastSync=SystemClock.elapsedRealtime();SyncEngine.kick(this);queue.recover();}
    if(wav.size()>=16000*2*300){wav.close();wav=nextWav();}
   }
   vad.flush();drain(vad,queue,recorded);
  }catch(Exception|LinkageError|OutOfMemoryError e){failed=true;message="Распознавание остановлено: "+e.getMessage()+". Исходная запись и очередь сохранены. Повтор доступен в настройках.";getSharedPreferences("speech",0).edit().putString("last_error",message).apply();}
  finally{
   stopping=true;running=false;partial="";micLevel=0;
   if(microphone!=null){try{microphone.stop();}catch(RuntimeException ignored){}microphone.release();}
   if(wav!=null)try{wav.close();}catch(IOException e){failed=true;message="Ошибка сохранения аудио: "+e.getMessage();}
   if(vad!=null)vad.release();
   String failureMessage=message;
   if(queue!=null){queue.close();if(failed)message=failureMessage;if(!queue.error.isEmpty()){failed=true;message=queue.error;}}
   if(speech!=null)speech.close();String exported="";
   if(queue!=null){for(File dir:queue.touched){try{ExportFiles.session(this,dir);}catch(IOException e){exported=" Текст остался в меню записей.";}}}
   if(session!=null)try{ExportFiles.session(this,session);}catch(IOException e){exported=" Текст остался в меню записей.";}
   if(!failed)message="Выключено. GigaAM v3: сохранено фраз "+phraseCount+". Аудио сохранено."+exported;
   SyncEngine.kick(this);main.post(()->{worker=null;running=false;stopping=false;speechPending=0;AiJob.schedule(this);ReminderReceiver.restore(this);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();});
  }
 }
 private void drain(Vad vad,SpeechQueue queue,long recorded)throws IOException{while(!vad.empty()){SpeechSegment segment=vad.front();queue.offer(session,segment.getSamples(),recorded+segment.getStart()*1000/16000);vad.pop();}}
 private WavWriter nextWav()throws IOException{return new WavWriter(new File(session,String.format(Locale.US,"audio_%03d.wav",++chunks)));}
 @Override public void onDestroy(){cancelled=true;if(wake!=null&&wake.isHeld())wake.release();if(worker==null){running=false;stopping=false;}super.onDestroy();}
}
