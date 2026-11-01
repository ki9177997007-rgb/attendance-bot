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
  try{if(Build.VERSION.SDK_INT<31||a.canScheduleExactAlarms())a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,p);else a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,p);}catch(SecurityException e){a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,p);}
 }
 static void restore(Context c){try(TaskDb db=new TaskDb(c)){for(TaskDb.Row r:db.all())if(!r.completed&&r.due>0&&!r.notified)schedule(c,r.id,r.due);}}
 @Override public void onReceive(Context c,Intent i){
  if("DONE".equals(i.getAction())||"SNOOZE".equals(i.getAction())){
   String id=i.getStringExtra("id");if(id==null)return;
   try(TaskDb db=new TaskDb(c)){TaskDb.Row r=db.get(id);if(r==null||r.completed)return;boolean done="DONE".equals(i.getAction());long due=done?r.due:System.currentTimeMillis()+10*60000L;db.edit(id,r.text,r.assignee,due,done);schedule(c,id,done?0:due);c.getSystemService(NotificationManager.class).cancel(id,2);}SyncEngine.kick(c);return;
  }
  if(!"REMIND".equals(i.getAction())){restore(c);ArchiveJob.periodic(c);return;}
  String id=i.getStringExtra("id");if(id==null)return;
  try(TaskDb db=new TaskDb(c)){
   TaskDb.Row r=db.get(id);if(r==null||r.completed||r.notified||r.due<=0)return;
   if(r.due>System.currentTimeMillis()+2000){schedule(c,id,r.due);return;}
   NotificationManager nm=c.getSystemService(NotificationManager.class);
   nm.createNotificationChannel(new NotificationChannel("reminders","Напоминания о поручениях",NotificationManager.IMPORTANCE_HIGH));
   if(!nm.areNotificationsEnabled())return;
   PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,TasksActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   PendingIntent done=action(c,id,"DONE"),later=action(c,id,"SNOOZE");
   nm.notify(id,2,new Notification.Builder(c,"reminders").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Пора: "+r.text).setContentText(r.assignee.isEmpty()?"Открой список поручений":r.assignee).setStyle(new Notification.BigTextStyle().bigText(r.text)).setContentIntent(open).addAction(android.R.drawable.checkbox_on_background,"Выполнено",done).addAction(android.R.drawable.ic_lock_idle_alarm,"Через 10 минут",later).setAutoCancel(true).build());
   db.notified(id);
  }
 }
 static PendingIntent action(Context c,String id,String action){return PendingIntent.getBroadcast(c,0,new Intent(c,ReminderReceiver.class).setAction(action).setData(Uri.parse("memory://task/"+id)).putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
}
