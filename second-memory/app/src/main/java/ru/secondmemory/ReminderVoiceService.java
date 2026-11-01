package ru.secondmemory;
import android.app.*;
import android.content.*;
import android.os.*;
/** Alarm playback, without opening a background microphone. Notification is always the fallback. */
public class ReminderVoiceService extends Service {
 public IBinder onBind(Intent i){return null;}
 public int onStartCommand(Intent i,int flags,int startId){if(i==null){stopSelf();return START_NOT_STICKY;}NotificationManager n=getSystemService(NotificationManager.class);n.createNotificationChannel(new NotificationChannel("reminder-voice","Озвучивание напоминаний",NotificationManager.IMPORTANCE_LOW));startForeground(8,new Notification.Builder(this,"reminder-voice").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Вторая память: напоминание").build());VoiceOutput.speak(this,i.getStringExtra("text"));new Handler(Looper.getMainLooper()).postDelayed(()->{stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId);},45000);return START_NOT_STICKY;}
}
