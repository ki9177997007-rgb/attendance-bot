package ru.secondmemory;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.widget.*;
import java.time.*;
import java.util.*;
import org.json.*;

public class CommandsActivity extends Activity {
 private LinearLayout page,pending;private EditText input;private TextView status;
 @Override public void onCreate(Bundle b){super.onCreate(b);ScrollView scroll=new ScrollView(this);page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(24,24,24,24);page.setFitsSystemWindows(true);scroll.addView(page);setContentView(scroll);ScreenLayout.apply(this,page);
  label("Команды телефона",24);label("Во время записи скажи: «Память, поставь будильник на 6:30». Затем открой уведомление и подтверди действие. Будильники, таймеры и приложения требуют подтверждения на этом экране.\n\nЗдесь можно ввести команду без слова «Память». Примеры:\nТаймер на 20 минут\nОткрой Telegram\nНапомни завтра в 8:30 позвонить Олегу",16);
  input=new EditText(this);input.setHint("Команда");page.addView(input);if(b!=null)input.setText(b.getString("input",""));
  button(page,"Выполнить команду",()->preview(input.getText().toString(),System.currentTimeMillis(),false));
  button(page,"Выбрать приложение",()->apps(null,()->{}));button(page,"Настроить уведомления и точные напоминания",()->startActivity(new Intent(this,SettingsActivity.class)));button(page,"Поручения и напоминания",()->startActivity(new Intent(this,TasksActivity.class)));
  status=label("",16);pending=new LinearLayout(this);pending.setOrientation(LinearLayout.VERTICAL);page.addView(pending);button(page,"Назад",this::finish);refresh();
 }
 @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("input",input.getText().toString());}
 @Override protected void onResume(){super.onResume();if(pending!=null)refresh();}
 private TextView label(String text,int size){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextIsSelectable(true);page.addView(t);return t;}
 private void button(LinearLayout parent,String title,Runnable r){Button b=new Button(this);b.setText(title);b.setOnClickListener(v->r.run());parent.addView(b);}
 private void refresh(){pending.removeAllViews();status.setText(getSharedPreferences("commands",0).getString("last",""));JSONArray list=CommandQueue.read(this);
  for(int i=0;i<list.length();i++){JSONObject o=list.optJSONObject(i);if(o==null)continue;String text=o.optString("text");long at=o.optLong("at");button(pending,text,()->preview(text,at,true));button(pending,"Убрать эту команду",()->{CommandQueue.remove(this,text,at);refresh();});}
 }
 private void preview(String text,long at,boolean queued){try{PhoneCommand command=PhoneCommand.parse(text,at,ZoneId.systemDefault());if(command.kind!=PhoneCommand.Kind.OPEN&&command.kind!=PhoneCommand.Kind.TIMER&&command.value<=System.currentTimeMillis())throw new IllegalArgumentException("Время команды уже прошло. Введи новое время.");
   new AlertDialog.Builder(this).setTitle("Подтвердить действие").setMessage(command.summary()).setPositiveButton("Выполнить",(d,w)->{try{execute(command,()->{if(queued)CommandQueue.remove(this,text,at);else input.setText("");refresh();});}catch(Exception e){showError(e.getMessage());}}).setNegativeButton("Отмена",null).show();
  }catch(IllegalArgumentException e){showError(e.getMessage());}}
 private void execute(PhoneCommand cmd,Runnable accepted)throws Exception{
  if(cmd.kind==PhoneCommand.Kind.OPEN){apps(cmd.target,accepted);return;}
  if(cmd.kind==PhoneCommand.Kind.REMINDER){String id;try(TaskDb db=new TaskDb(this)){id=db.addReminder(cmd.target,cmd.value);}ReminderReceiver.schedule(this,id,cmd.value);SyncEngine.kick(this);boolean enabled=getSystemService(NotificationManager.class).areNotificationsEnabled();String msg=enabled?"Напоминание сохранено. Время: "+cmd.summary():"Напоминание сохранено. Разреши уведомления в настройках приложения, чтобы получить сигнал.";if(android.os.Build.VERSION.SDK_INT>=31&&!getSystemService(AlarmManager.class).canScheduleExactAlarms())msg+=" Для сигнала точно по времени разреши точные напоминания в настройках.";getSharedPreferences("commands",0).edit().putString("last",msg).apply();Toast.makeText(this,msg,Toast.LENGTH_LONG).show();accepted.run();return;}
  Intent intent;if(cmd.kind==PhoneCommand.Kind.TIMER){intent=new Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH,(int)cmd.value).putExtra(AlarmClock.EXTRA_MESSAGE,"Вторая память");}else{ZonedDateTime t=Instant.ofEpochMilli(cmd.value).atZone(ZoneId.systemDefault());intent=new Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR,t.getHour()).putExtra(AlarmClock.EXTRA_MINUTES,t.getMinute()).putExtra(AlarmClock.EXTRA_MESSAGE,"Вторая память");}
  // Show the clock UI so the user can verify the receiving app actually accepted the request.
  intent.putExtra(AlarmClock.EXTRA_SKIP_UI,false);try{startActivity(intent);accepted.run();getSharedPreferences("commands",0).edit().putString("last","Команда передана в «Часы». Проверь установленное время в открывшемся приложении.").apply();}catch(ActivityNotFoundException e){throw new IllegalArgumentException("Не найдено приложение «Часы», поддерживающее эту команду.");}
 }
 private void apps(String requested,Runnable accepted){
  Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);List<ResolveInfo> all=getPackageManager().queryIntentActivities(launch,0);Map<String,ResolveInfo> unique=new TreeMap<>();
  String key=requested==null?"":TaskRules.normalize(requested).replace("телеграмм","telegram").replace("телеграм","telegram").replace("егайл","yougile").replace("югайл","yougile").replace("ютуб","youtube").replace("ватсап","whatsapp");
  for(ResolveInfo r:all){String name=r.loadLabel(getPackageManager()).toString();String normalized=TaskRules.normalize(name);String pkg=r.activityInfo.packageName;if(requested==null||normalized.equals(key)||normalized.contains(key)||pkg.contains(key.replace(" ","")))unique.put(name+" · "+pkg,r);}
  if(unique.isEmpty()){showError("Приложение «"+requested+"» не найдено. Нажми «Выбрать приложение», чтобы посмотреть установленные.");return;}
  String[] names=unique.keySet().toArray(new String[0]);ResolveInfo[] items=unique.values().toArray(new ResolveInfo[0]);
  if(requested!=null&&items.length==1){launch(items[0],accepted);return;}
  new AlertDialog.Builder(this).setTitle("Выбери приложение").setItems(names,(d,i)->launch(items[i],accepted)).setNegativeButton("Отмена",null).show();
 }
 private void launch(ResolveInfo app,Runnable accepted){try{startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(new ComponentName(app.activityInfo.packageName,app.activityInfo.name)));accepted.run();}catch(RuntimeException e){showError("Не удалось открыть приложение. Проверь, что оно доступно в текущем профиле телефона.");}}
 private void showError(String error){status.setText(error);Toast.makeText(this,error,Toast.LENGTH_LONG).show();}
}
