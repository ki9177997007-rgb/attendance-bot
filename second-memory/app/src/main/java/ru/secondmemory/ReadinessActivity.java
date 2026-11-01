package ru.secondmemory;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
public class ReadinessActivity extends Activity {
 private LinearLayout page;private String spoken="";
 public void onCreate(Bundle b){super.onCreate(b);ScrollView scroll=new ScrollView(this);page=Ui.column(this);scroll.addView(page);setContentView(scroll);ScreenLayout.apply(this,page);VoiceOutput.init(this);}
 protected void onResume(){super.onResume();new Handler(Looper.getMainLooper()).postDelayed(this::render,500);}
 private void render(){if(isFinishing())return;page.removeAllViews();Ui.title(page,"Готовность помощника","Проверка записи, голоса и напоминаний");StringBuilder report=new StringBuilder();
  check(report,"Микрофон",checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED,"Доступ разрешён","Разреши микрофон в настройках приложения");
  check(report,"Твой голос",OwnerVoice.enrolled(this),"Образец сохранён", "Один раз запиши образец в голосовом помощнике");
  check(report,"Озвучивание",VoiceOutput.available(),"Русский офлайн-голос готов",VoiceOutput.status);
  check(report,"Уведомления",getSystemService(NotificationManager.class).areNotificationsEnabled(),"Разрешены","Разреши уведомления для напоминаний");
  boolean exact=Build.VERSION.SDK_INT<31||getSystemService(AlarmManager.class).canScheduleExactAlarms();check(report,"Точность напоминаний",exact,"Сигнал по заданному времени","Без разрешения Android может задержать сигнал");
  boolean battery=getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());check(report,"Работа в фоне",battery,"Оптимизация батареи отключена","Проверь ограничения батареи, если запись прерывается");
  long free=getFilesDir().getUsableSpace();check(report,"Место на телефоне",free>200*1024*1024L,"Свободно "+Ui.bytes(free),"Осталось "+Ui.bytes(free)+". Освободи место для записи");
  try(TaskDb db=new TaskDb(this)){boolean connected=SecureConfig.load(this)!=null;check(report,"YouGile",connected,SyncEngine.status,"Не подключён. Задач в очереди: "+db.pendingCount()+". Офлайн-дела работают");}catch(Exception e){check(report,"YouGile",false,"","Нужно проверить подключение");}
  spoken=report.toString();Ui.button(page,"Озвучить состояние",true,()->VoiceOutput.speak(this,spoken));Ui.button(page,"Скопировать всё",false,()->Ui.copy(this,"Готовность",spoken));Ui.button(page,"Настройки и разрешения",false,()->startActivity(new Intent(this,SettingsActivity.class)));Ui.button(page,"Настройки батареи",false,()->{try{startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_SETTINGS));}});Ui.button(page,"Назад",false,this::finish);
 }
 private void check(StringBuilder report,String title,boolean ok,String yes,String no){LinearLayout card=Ui.card(page);card.addView(Ui.text(this,(ok?"✓  ":"○  ")+title,18,ok?Ui.TEAL:Ui.INK));String info=ok?yes:no;card.addView(Ui.label(this,info,14));report.append(title).append(": ").append(info).append(". ");}
}
