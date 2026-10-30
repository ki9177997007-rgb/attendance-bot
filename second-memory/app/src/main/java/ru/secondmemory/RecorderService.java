package ru.secondmemory;
import android.app.*;
import android.content.*;
import android.media.MediaRecorder;
import android.os.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
public class RecorderService extends Service {
 private MediaRecorder recorder;
 @Override public IBinder onBind(Intent i){return null;}
 @Override public int onStartCommand(Intent i,int flags,int id){
 if(i!=null && "STOP".equals(i.getAction())){stopRecording();stopSelf();return START_NOT_STICKY;}
 if(recorder!=null)return START_NOT_STICKY;
 NotificationManager nm=getSystemService(NotificationManager.class);
 NotificationChannel c=new NotificationChannel("recording","Запись речи",NotificationManager.IMPORTANCE_LOW);nm.createNotificationChannel(c);
 Intent stop=new Intent(this,RecorderService.class);stop.setAction("STOP");
 PendingIntent pi=PendingIntent.getService(this,0,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
 Notification n=new Notification.Builder(this,"recording").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Вторая память записывает").setContentText("Нажмите «Остановить», чтобы завершить").addAction(android.R.drawable.ic_media_pause,"Остановить",pi).build();
 startForeground(1,n);
 try{
 File dir=new File(getFilesDir(),"recordings");dir.mkdirs();
 File file=new File(dir,new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".m4a");
 recorder=new MediaRecorder();recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
 recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
 recorder.setOutputFile(file.getAbsolutePath());recorder.prepare();recorder.start();
 }catch(Exception e){stopRecording();stopSelf();}
 return START_NOT_STICKY;
 }
 private void stopRecording(){if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}recorder.reset();recorder.release();recorder=null;}}
 @Override public void onDestroy(){stopRecording();super.onDestroy();}
}