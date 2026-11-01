package ru.secondmemory;
import android.app.*;
import android.app.job.JobScheduler;
import android.content.*;
import android.os.*;
import java.io.*;
/** User-visible local file processing. Does not wait for JobScheduler quotas. */
public class AnalysisService extends Service {
 static volatile boolean running;private volatile boolean cancelled;private Thread worker;private PowerManager.WakeLock wake;
 private final Handler main=new Handler(Looper.getMainLooper());
 static void startUser(Context c){startUser(c,null);}
 static void startUser(Context c,String session){c.getSharedPreferences("ai",0).edit().putBoolean("paused",false).apply();try{Intent i=new Intent(c,AnalysisService.class);if(session!=null)i.putExtra("session",session);c.startForegroundService(i);}catch(RuntimeException e){AiJob.status="Не удалось начать анализ: "+e.getMessage();c.getSharedPreferences("ai",0).edit().putString("last_error",AiJob.status).apply();AiJob.schedule(c);}}
 static void pause(Context c){c.getSharedPreferences("ai",0).edit().putBoolean("paused",true).apply();c.getSystemService(JobScheduler.class).cancel(405);if(running)c.startService(new Intent(c,AnalysisService.class).setAction("STOP"));else if(AiJob.isActive())try{LocalAi.cancel();}catch(LinkageError ignored){}AiJob.status="Анализ на паузе. Прогресс сохранён.";}
 public IBinder onBind(Intent i){return null;}
 public int onStartCommand(Intent intent,int flags,int id){if(intent==null){stopSelf();return START_NOT_STICKY;}if("STOP".equals(intent.getAction())){cancelled=true;getSharedPreferences("ai",0).edit().putBoolean("paused",true).apply();if(worker==null){running=false;stopSelf();}if(AnalysisWork.owner==worker)try{LocalAi.cancel();}catch(LinkageError ignored){}return START_NOT_STICKY;}if(running)return START_NOT_STICKY;
  cancelled=false;running=true;AiJob.status="Начинаю анализ разговоров…";getSharedPreferences("ai",0).edit().remove("last_error").apply();
  try{NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("analysis","Разбор разговоров",NotificationManager.IMPORTANCE_LOW));startForeground(11,notification());wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"SecondMemory:analysis");wake.acquire(5*60*60*1000L);getSystemService(JobScheduler.class).cancel(405);
   String scopeId=intent.getStringExtra("session");File scope=scopeId==null?null:new File(getFilesDir(),"sessions/"+scopeId);if(scope!=null&&!scope.getCanonicalFile().getParentFile().equals(new File(getFilesDir(),"sessions").getCanonicalFile()))throw new IOException("Недопустимая запись");
   final File target=scope;worker=new Thread(()->runAnalysis(target),"foreground-analysis");worker.start();main.post(tick);
  }catch(Exception e){AiJob.status="Анализ не запущен: "+e.getMessage();getSharedPreferences("ai",0).edit().putString("last_error",AiJob.status).apply();running=false;stopSelf();}return START_NOT_STICKY;
 }
 private final Runnable tick=new Runnable(){public void run(){if(!running)return;getSystemService(NotificationManager.class).notify(11,notification());main.postDelayed(this,2000);}};
 private Notification notification(){PendingIntent open=PendingIntent.getActivity(this,11,new Intent(this,AnalysisActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);PendingIntent pause=PendingIntent.getService(this,12,new Intent(this,AnalysisService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);return new Notification.Builder(this,"analysis").setSmallIcon(android.R.drawable.ic_menu_edit).setContentTitle("Вторая память · анализ").setContentText(AiJob.status).setContentIntent(open).setOngoing(true).addAction(android.R.drawable.ic_media_pause,"Пауза",pause).build();}
 private void runAnalysis(File scope){boolean more=false;try{long start=SystemClock.elapsedRealtime();while(!cancelled&&SystemClock.elapsedRealtime()-start<5*60*60*1000L){
   if(AiJob.isActive()||MemoryActivity.searching.get()||RecorderService.stopping||RecorderService.speechPending>2){AiJob.status="Жду освобождения нейросети и распознавания речи…";Thread.sleep(1000);continue;}
   more=AnalysisWork.runPass(this,()->cancelled,scope);if(!more)break;Thread.sleep(2000);
  }}catch(InterruptedException e){Thread.currentThread().interrupt();}catch(Exception|LinkageError|OutOfMemoryError e){AiJob.status="Анализ остановлен: "+e.getMessage();getSharedPreferences("ai",0).edit().putString("last_error",AiJob.status).apply();}finally{if(cancelled){getSharedPreferences("ai",0).edit().putBoolean("paused",true).apply();AiJob.status="Анализ на паузе. Прогресс сохранён.";}if(wake!=null&&wake.isHeld())wake.release();main.post(()->{running=false;main.removeCallbacks(tick);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();if(!cancelled&&LocalModel.ready(this))AiJob.schedule(getApplicationContext());});}}
 @Override public void onTimeout(int id,int type){cancelled=true;getSharedPreferences("ai",0).edit().putBoolean("paused",true).apply();if(AiJob.isActive())try{LocalAi.cancel();}catch(LinkageError ignored){}AiJob.status="Android ограничил время анализа. Прогресс сохранён.";getSharedPreferences("ai",0).edit().putString("last_error",AiJob.status).apply();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
 public void onDestroy(){cancelled=true;if(AnalysisWork.owner==worker)try{LocalAi.cancel();}catch(LinkageError ignored){}main.removeCallbacks(tick);if(wake!=null&&wake.isHeld())wake.release();if(worker==null)running=false;super.onDestroy();}
}
