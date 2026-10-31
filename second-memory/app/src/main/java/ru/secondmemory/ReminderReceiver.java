package ru.secondmemory;
import android.app.*;
import android.content.*;
import android.os.Build;
import android.provider.Settings;
import android.net.Uri;

public class ReminderReceiver extends BroadcastReceiver {
 static PendingIntent alarm(Context c,String id){return PendingIntent.getBroadcast(c,0,new Intent(c,ReminderReceiver.class).setAction("REMIND").setData(Uri.parse("memory://task/"+id)).putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 static void schedule(Context c,String id,long due){
  AlarmManager a=c.getSystemService(AlarmManager.class);PendingIntent p=alarm(c,id);a.cancel(p);
  if(due<=0)return;
  long at=Math.max(System.currentTimeMillis()+1000,due);
  if(Build.VERSION.SDK_INT<31||a.canScheduleExactAlarms())a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,p);
  else a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,p);
 }
 static void restore(Context c){try(TaskDb db=new TaskDb(c)){for(TaskDb.Row r:db.all())if(!r.completed&&r.due>0&&!r.notified)schedule(c,r.id,r.due);}}
 @Override public void onReceive(Context c,Intent i){
  if(!"REMIND".equals(i.getAction())){restore(c);return;}
  String id=i.getStringExtra("id");if(id==null)return;
  try(TaskDb db=new TaskDb(c)){
   TaskDb.Row r=db.get(id);if(r==null||r.completed||r.notified||r.due<=0)return;
   if(r.due>System.currentTimeMillis()+2000){schedule(c,id,r.due);return;}
   NotificationManager nm=c.getSystemService(NotificationManager.class);
   nm.createNotificationChannel(new NotificationChannel("reminders","Напоминания о поручениях",NotificationManager.IMPORTANCE_HIGH));
   if(!nm.areNotificationsEnabled())return;
   PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,TasksActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   nm.notify(id,2,new Notification.Builder(c,"reminders").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Пора: "+r.text).setContentText(r.assignee.isEmpty()?"Открой список поручений":r.assignee).setStyle(new Notification.BigTextStyle().bigText(r.text)).setContentIntent(open).setAutoCancel(true).build());
   db.notified(id);
  }
 }
}
