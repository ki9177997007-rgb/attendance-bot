package ru.secondmemory;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
 private Button toggle;private TextView state,live,history;private ScrollView historyScroll;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
 private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));t.setIncludeFontPadding(true);return t;}
 private GradientDrawable background(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;}
 private final Runnable refresh=new Runnable(){public void run(){
  toggle.setText(RecorderService.stopping?"Сохраняю…":RecorderService.running?"ВЫКЛ · остановить запись":"ВКЛ · начать запись");toggle.setEnabled(!RecorderService.stopping);
  toggle.setBackground(background(RecorderService.running?Color.rgb(166,42,45):Color.rgb(29,78,216)));
  String message=RecorderService.message;int export=message.indexOf(" Текст:");if(export>=0)message=message.substring(0,export);
  state.setText((CommandQueue.read(MainActivity.this).length()>0?"Есть команды: открой Настройки → Команды телефона.\n":"")+message+(RecorderService.running?"\nМикрофон: "+RecorderService.micLevel+" / 100 · фраз: "+RecorderService.phraseCount+" · в обработке: "+RecorderService.speechPending:""));
  live.setText(RecorderService.partial.isEmpty()?(RecorderService.running?"Слушаю… Сделай короткую паузу, чтобы сохранить фразу.":"GigaAM v3 · речь распознаётся на телефоне."):RecorderService.partial);
  String value=RecorderService.transcript;if(value.isEmpty())value=getSharedPreferences("display",MODE_PRIVATE).getString("recent","");
  String shown=value.isEmpty()?"Распознанные фразы появятся здесь.\nЗаписи и поручения доступны в настройках.":value;
  if(!shown.contentEquals(history.getText())){boolean bottom=historyScroll.getChildAt(0).getBottom()-historyScroll.getScrollY()-historyScroll.getHeight()<dp(80);history.setText(shown);if(bottom&&RecorderService.running)historyScroll.post(()->historyScroll.fullScroll(View.FOCUS_DOWN));}handler.postDelayed(this,600);
 }};
 @Override public void onCreate(Bundle b){super.onCreate(b);
  getWindow().setStatusBarColor(Color.rgb(244,247,252));getWindow().setNavigationBarColor(Color.rgb(244,247,252));getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(16),dp(8),dp(16),dp(8));page.setFitsSystemWindows(true);page.setBackgroundColor(Color.rgb(244,247,252));
  page.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(16)+insets.getSystemWindowInsetLeft(),dp(8)+insets.getSystemWindowInsetTop(),dp(16)+insets.getSystemWindowInsetRight(),dp(8)+insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
  TextView title=text("Вторая память",22,Color.rgb(20,31,51));title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));page.addView(title);
  Button settings=new Button(this);settings.setText("Настройки");settings.setTextSize(14);settings.setAllCaps(false);settings.setMinHeight(dp(48));settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));page.addView(settings,new LinearLayout.LayoutParams(-1,-2));
  Button analysis=new Button(this);analysis.setText("Анализ разговоров");analysis.setTextSize(14);analysis.setAllCaps(false);analysis.setMinHeight(dp(48));analysis.setOnClickListener(v->startActivity(new Intent(this,AnalysisActivity.class)));page.addView(analysis,new LinearLayout.LayoutParams(-1,-2));
  historyScroll=new ScrollView(this);historyScroll.setFillViewport(true);historyScroll.setBackground(background(Color.WHITE));
  LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(14),dp(12),dp(14),dp(16));historyScroll.addView(content);
  state=text("",13,Color.rgb(71,85,105));content.addView(state);
  live=text("",15,Color.rgb(29,78,216));live.setPadding(0,dp(12),0,dp(12));content.addView(live);
  TextView subtitle=text("Расшифровка",14,Color.rgb(71,85,105));content.addView(subtitle);
  Button copy=new Button(this);copy.setText("Скопировать текст");copy.setTextSize(13);copy.setAllCaps(false);copy.setMinHeight(dp(48));copy.setOnClickListener(v->{getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Расшифровка",history.getText()));Toast.makeText(this,"Текст скопирован",Toast.LENGTH_SHORT).show();});content.addView(copy);
  history=text("",16,Color.rgb(20,31,51));history.setLineSpacing(dp(3),1f);history.setTextIsSelectable(true);content.addView(history);
  LinearLayout.LayoutParams body=new LinearLayout.LayoutParams(-1,0,1);body.topMargin=dp(6);body.bottomMargin=dp(8);page.addView(historyScroll,body);
  toggle=new Button(this);toggle.setTextSize(19);toggle.setAllCaps(false);toggle.setTextColor(Color.WHITE);toggle.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));toggle.setMinHeight(dp(68));toggle.setPadding(dp(12),dp(14),dp(12),dp(14));page.addView(toggle,new LinearLayout.LayoutParams(-1,-2));
  setContentView(page);page.requestApplyInsets();
  toggle.setOnClickListener(v->{if(RecorderService.running)startService(new Intent(this,RecorderService.class).setAction("STOP"));else requestStart();});
  SyncJob.schedule(this);SyncEngine.kick(this);AiJob.schedule(this);ReminderReceiver.restore(this);
 }
 private void requestStart(){
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);return;}
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!getPreferences(MODE_PRIVATE).getBoolean("notificationAsked",false)){
   getPreferences(MODE_PRIVATE).edit().putBoolean("notificationAsked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},2);return;
  }
  try{startForegroundService(new Intent(this,RecorderService.class).setAction("START"));}catch(RuntimeException e){RecorderService.message="Не удалось включить микрофон: "+e.getMessage();}
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==2||(request==1&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED))requestStart();else RecorderService.message="Нет доступа к микрофону. Открой Настройки → Разрешения приложения.";}
 @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
 @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
