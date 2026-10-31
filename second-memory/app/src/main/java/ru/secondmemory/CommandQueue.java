package ru.secondmemory;
import android.app.*;
import android.content.*;
import java.time.ZoneId;
import org.json.*;

/** Recognition only queues actions; it never launches another app from the background. */
final class CommandQueue {
 static synchronized void offer(Context c,String text){
  if(!PhoneCommand.addressed(text))return;
  try{
   PhoneCommand command=PhoneCommand.parse(text,System.currentTimeMillis(),ZoneId.systemDefault());
   JSONArray list=read(c);if(list.length()>=30)return;
   list.put(new JSONObject().put("text",text).put("at",System.currentTimeMillis()));write(c,list);
   NotificationManager nm=c.getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("commands","Команды телефона",NotificationManager.IMPORTANCE_DEFAULT));
   PendingIntent open=PendingIntent.getActivity(c,14,new Intent(c,CommandsActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   if(nm.areNotificationsEnabled())nm.notify(14,new Notification.Builder(c,"commands").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Команда ждёт подтверждения").setContentText(command.summary()).setContentIntent(open).setAutoCancel(true).build());
  }catch(IllegalArgumentException e){c.getSharedPreferences("commands",0).edit().putString("last",e.getMessage()).apply();}catch(JSONException e){c.getSharedPreferences("commands",0).edit().putString("last","Не удалось сохранить команду").apply();}
 }
 static synchronized JSONArray read(Context c){try{return new JSONArray(c.getSharedPreferences("commands",0).getString("queue","[]"));}catch(JSONException e){return new JSONArray();}}
 static synchronized void remove(Context c,String text,long at){JSONArray src=read(c),dst=new JSONArray();for(int i=0;i<src.length();i++){JSONObject o=src.optJSONObject(i);if(o!=null&&!(text.equals(o.optString("text"))&&at==o.optLong("at")))dst.put(o);}write(c,dst);if(dst.length()==0)c.getSystemService(NotificationManager.class).cancel(14);}
 private static void write(Context c,JSONArray list){c.getSharedPreferences("commands",0).edit().putString("queue",list.toString()).commit();}
}
