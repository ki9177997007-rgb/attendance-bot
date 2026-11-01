package ru.secondmemory;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.widget.*;

public class SettingsActivity extends Activity {
 private LinearLayout page;private TextView modelStatus,yougileStatus,alarmStatus,speechStatus;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable refresh=new Runnable(){public void run(){
  speechStatus.setText(NeuralSpeech.NAME+"\nМодель встроена в приложение. Дополнительно скачивать не нужно.\nФрагментов в обработке: "+RecorderService.speechPending+"\n"+getSharedPreferences("speech",0).getString("last_error",""));
  modelStatus.setText(LocalModel.status(SettingsActivity.this)+"\n"+getSharedPreferences("ai",0).getString("last_error","")+"\n"+AiJob.status);
  yougileStatus.setText(SyncEngine.status);
  boolean notifications=getSystemService(NotificationManager.class).areNotificationsEnabled();boolean exact=Build.VERSION.SDK_INT<31||getSystemService(AlarmManager.class).canScheduleExactAlarms();
  alarmStatus.setText(!notifications?"Уведомления запрещены. Разреши их кнопкой ниже.":exact?"Уведомления и точные напоминания разрешены.":"Уведомления включены; для сигнала вовремя разреши точные напоминания.");
  handler.postDelayed(this,1000);
 }};
 @Override public void onCreate(Bundle b){super.onCreate(b);ScrollView scroll=new ScrollView(this);page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(24,24,24,24);scroll.addView(page);scroll.setFitsSystemWindows(true);setContentView(scroll);ScreenLayout.apply(this,page);
  heading("Настройки");button("Назад к записи",()->finish());
  heading("Управление телефоном");label("Будильники, таймеры, открытие приложений и напоминания. Во время записи обращайся: «Память, …». Действие выполняется после подтверждения в уведомлении или на экране команд.");button("Команды телефона",()->startActivity(new Intent(this,CommandsActivity.class)));
  heading("Речевая нейросеть — GigaAM v3");speechStatus=label("");label("Распознаются все голоса. Текст появляется после паузы и обработки нейросетью. Исходное аудио сохраняется полностью. Qwen не распознаёт звук и для расшифровки не нужен.");button("Повторить распознавание сохранённых фрагментов",()->{if(RecorderService.running||RecorderService.stopping){Toast.makeText(this,"Сначала останови текущую запись и дождись обработки",Toast.LENGTH_LONG).show();return;}if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){Toast.makeText(this,"Сначала разреши микрофон, включив запись на главном экране",Toast.LENGTH_LONG).show();return;}getSharedPreferences("speech",0).edit().remove("last_error").apply();startForegroundService(new Intent(this,RecorderService.class).setAction("RETRY"));});button("Поручения и напоминания",()->startActivity(new Intent(this,TasksActivity.class)));button("Сотрудники",this::staff);button("Открыть / поделиться записью",this::records);button("Импорт записей из версии 06",this::importRecords);
  heading("Анализ всех разговоров — Qwen");button("Анализ разговоров",()->startActivity(new Intent(this,AnalysisActivity.class)));label("Qwen разбирает все расшифровки: темы, сведения, решения, обещания, поручения и открытые вопросы. Разбор идёт в фоне, с приоритетом распознавания речи. Однократная загрузка 1,12 ГБ. Для обычной расшифровки она не нужна.");modelStatus=label("");
  button("Скачать / повторить загрузку",this::downloadDialog);
  button("Проверить модель и запустить разбор",()->{getSharedPreferences("ai",0).edit().remove("last_error").apply();AiJob.schedule(this);Toast.makeText(this,"Проверка и разбор поставлены в очередь. Результаты появятся на экране «Анализ разговоров».",Toast.LENGTH_LONG).show();});
  button("Скачать модель через браузер",()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(LocalModel.URL+"?download=true")));}catch(ActivityNotFoundException e){Toast.makeText(this,"Браузер не найден",Toast.LENGTH_LONG).show();}});
  button("Выбрать скачанный файл модели",()->{if(RecorderService.running||AiJob.isActive()){Toast.makeText(this,"Сначала останови запись и дождись окончания разбора",Toast.LENGTH_LONG).show();return;}startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),20);});
  heading("Архив для ChatGPT");label("Отправка расшифровок с сохранением очереди без интернета. До настройки приёмника ничего не передаётся.");button("Подключение и статус архива",()->startActivity(new Intent(this,ArchiveActivity.class)));
  heading("YouGile");yougileStatus=label("");button("Подключить YouGile",()->startActivity(new Intent(this,ConnectActivity.class)));button("Проверить подключение YouGile",this::checkYouGile);button("Повторить синхронизацию YouGile",()->SyncEngine.kick(this));
  heading("Уведомления и фон");alarmStatus=label("");button("Разрешить уведомления",()->{if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},30);else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));});
  button("Разрешить точные напоминания",()->{if(Build.VERSION.SDK_INT>=31&&!getSystemService(AlarmManager.class).canScheduleExactAlarms())startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));else Toast.makeText(this,"Уже разрешено",Toast.LENGTH_SHORT).show();});
  button("Разрешения приложения",()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
 }
 private void checkYouGile(){SyncEngine.status="Проверяю доступ к YouGile…";new Thread(()->{try{org.json.JSONObject config=SecureConfig.load(this);if(config==null){SyncEngine.status="Сначала подключи YouGile";return;}YouGileApi api=new YouGileApi(config.getString("key"));org.json.JSONObject who=api.call("GET","/api-v2/users/me",null);api.call("GET","/api-v2/projects/"+config.getString("project"),null);SyncEngine.status="Доступ подтверждён: "+who.optString("realName",who.optString("email",""))+"\nПроект «Вторая память» доступен";}catch(Exception e){SyncEngine.status="Проверка не пройдена: "+e.getMessage();}},"yougile-check").start();}
 private TextView label(String text){TextView t=new TextView(this);t.setText(text);t.setTextSize(16);t.setTextIsSelectable(true);page.addView(t);return t;}
 private void heading(String text){TextView t=label(text);t.setTextSize(22);t.setPadding(0,24,0,12);}
 private void button(String text,Runnable action){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->action.run());page.addView(b);}
 private void staff(){EditText input=new EditText(this);input.setHint("По одному сотруднику на строку");input.setText(Staff.get(this));new AlertDialog.Builder(this).setTitle("Сотрудники").setView(input).setPositiveButton("Сохранить",(d,w)->Staff.set(this,input.getText().toString())).setNegativeButton("Назад",null).show();}
 private void downloadDialog(){
  if(LocalModel.ready(this)){AiJob.schedule(this);Toast.makeText(this,"Модель уже готова. Разбор поставлен в очередь.",Toast.LENGTH_LONG).show();return;}
  new AlertDialog.Builder(this).setTitle("Загрузка нейросети").setMessage("Будет скачано 1,12 ГБ. Если предыдущая загрузка зависла, она начнётся заново. Запись речи продолжит работать без модели.")
   .setPositiveButton("Wi-Fi",(d,w)->download(true)).setNeutralButton("Любая сеть",(d,w)->download(false)).setNegativeButton("Отмена",null).show();
 }
 private void download(boolean wifi){try{LocalModel.download(this,wifi);AiJob.status="";Toast.makeText(this,"Загрузка запущена. Прогресс виден в настройках.",Toast.LENGTH_LONG).show();}catch(Exception e){getSharedPreferences("ai",0).edit().putString("last_error",e.getMessage()).apply();Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}
 private void importRecords(){Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);pick.putExtra(DocumentsContract.EXTRA_INITIAL_URI,DocumentsContract.buildDocumentUri("ru.secondmemory.mobile.recordings","root"));startActivityForResult(pick,40);}
 private void records(){Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);pick.putExtra(DocumentsContract.EXTRA_INITIAL_URI,DocumentsContract.buildDocumentUri(RecordingsProvider.AUTHORITY,"root"));startActivityForResult(pick,10);}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||(data.getData()==null&&data.getClipData()==null))return;
  if(request==40){java.util.List<Uri> selected=new java.util.ArrayList<>();if(data.getClipData()!=null){for(int n=0;n<data.getClipData().getItemCount();n++)selected.add(data.getClipData().getItemAt(n).getUri());}else if(data.getData()!=null)selected.add(data.getData());new Thread(()->{int imported=0;String error="";for(Uri item:selected){try{RecordingImport.copy(this,item);imported++;}catch(Exception e){error=" Часть файлов не импортирована: "+e.getMessage();}}final String message="Импортировано файлов: "+imported+error;runOnUiThread(()->{if(!isFinishing())new AlertDialog.Builder(this).setTitle("Импорт записей").setMessage(message+"\nОригиналы в версии 06 сохранены. Импортированные файлы доступны в меню записей.").setPositiveButton("Понятно",null).show();});},"recording-import").start();return;}
  Uri uri=data.getData();if(request==10){Intent share=new Intent(Intent.ACTION_SEND).setType(getContentResolver().getType(uri));share.putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);share.setClipData(ClipData.newRawUri("Запись",uri));startActivity(Intent.createChooser(share,"Поделиться записью"));}
  if(request==20){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);getSharedPreferences("ai",0).edit().putString("import_uri",uri.toString()).remove("last_error").apply();AiJob.schedule(this);Toast.makeText(this,"Файл выбран. Копирование и проверка начнутся в фоне после остановки записи.",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"Не удалось открыть файл: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
 }
 @Override public void onRequestPermissionsResult(int r,String[]p,int[]v){super.onRequestPermissionsResult(r,p,v);ReminderReceiver.restore(this);}
 @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
 @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
