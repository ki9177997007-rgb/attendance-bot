package ru.secondmemory;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.*;

public class MainActivity extends Activity {
 private Button toggle;private TextView state,live,history;private ScrollView historyScroll;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable refresh=new Runnable(){public void run(){
  toggle.setText(RecorderService.running?"ВЫКЛ":"ВКЛ");toggle.setEnabled(!RecorderService.stopping);
  state.setText(RecorderService.message+(RecorderService.running?"\nУровень микрофона: "+RecorderService.micLevel+" / 100 · готовых фраз: "+RecorderService.phraseCount:""));
  live.setText(RecorderService.partial.isEmpty()?(RecorderService.running?"Слушаю. После короткой паузы фраза сохранится ниже.":"Распознавание всех голосов. Обучение не требуется."):"Сейчас слышу: "+RecorderService.partial);
  String text=RecorderService.transcript;if(text.isEmpty())text=getSharedPreferences("display",MODE_PRIVATE).getString("recent","");
  String shown=text.isEmpty()?"Здесь появится распознанный текст. Нажми ВКЛ и скажи несколько слов.":text;if(!shown.contentEquals(history.getText())){history.setText(shown);historyScroll.post(()->historyScroll.fullScroll(android.view.View.FOCUS_DOWN));}handler.postDelayed(this,500);
 }};
 @Override public void onCreate(Bundle b){super.onCreate(b);
  LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(24,24,24,16);page.setFitsSystemWindows(true);
  TextView title=new TextView(this);title.setText("Вторая память 0.6");title.setTextSize(24);page.addView(title);
  toggle=new Button(this);toggle.setText("ВКЛ");toggle.setTextSize(28);page.addView(toggle,new LinearLayout.LayoutParams(-1,100));
  Button settings=new Button(this);settings.setText("Настройки");settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));page.addView(settings);
  state=new TextView(this);state.setTextSize(16);page.addView(state);
  live=new TextView(this);live.setTextSize(18);live.setPadding(0,16,0,16);page.addView(live);
  ScrollView scroll=new ScrollView(this);historyScroll=scroll;history=new TextView(this);history.setTextSize(18);history.setTextIsSelectable(true);scroll.addView(history);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(page);
  toggle.setOnClickListener(v->{if(RecorderService.running)startService(new Intent(this,RecorderService.class).setAction("STOP"));else requestStart();});
  SyncJob.schedule(this);SyncEngine.kick(this);AiJob.schedule(this);ReminderReceiver.restore(this);
 }
 private void requestStart(){
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);return;}
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!getPreferences(MODE_PRIVATE).getBoolean("notificationAsked",false)){
   getPreferences(MODE_PRIVATE).edit().putBoolean("notificationAsked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},2);return;
  }
  getSystemService(android.app.job.JobScheduler.class).cancel(405);
  try{startForegroundService(new Intent(this,RecorderService.class).setAction("START"));}catch(RuntimeException e){RecorderService.message="Не удалось включить микрофон: "+e.getMessage();}
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==2||(request==1&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED))requestStart();else RecorderService.message="Нет доступа к микрофону. Открой Настройки → Разрешения приложения.";}
 @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
 @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
