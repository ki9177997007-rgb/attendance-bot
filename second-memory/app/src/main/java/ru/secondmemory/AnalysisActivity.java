package ru.secondmemory;
import android.app.*;
import android.content.*;
import android.os.*;
import android.text.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

/** A searchable, copyable overview of all conversations, with literal source evidence. */
public class AnalysisActivity extends Activity {
 private TextView status;private EditText search;private CheckBox today,bySession;private LinearLayout list;private Button start;
 private String report="",lastSignature="";private volatile boolean loading;private boolean dirty;private long refreshed;private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable tick=new Runnable(){public void run(){status.setText(LocalModel.status(AnalysisActivity.this)+"\n"+AiJob.status+"\n"+getSharedPreferences("ai",0).getString("last_error",""));if(System.currentTimeMillis()-refreshed>6000)refresh();handler.postDelayed(this,1200);}};
 static final class Entry {File dir;JSONObject state;List<ConversationAnalysis.Line> lines;Entry(File d,JSONObject s,List<ConversationAnalysis.Line> l){dir=d;state=s;lines=l;}}
 @Override public void onCreate(Bundle b){super.onCreate(b);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);setContentView(page);ScreenLayout.apply(this,page);
  TextView title=label("Анализ разговоров",22);page.addView(title);status=label("",13);page.addView(status);
  start=new Button(this);start.setText("Подготовить ИИ / начать анализ");start.setAllCaps(false);start.setOnClickListener(v->begin());page.addView(start);
  search=new EditText(this);search.setHint("Поиск: человек, тема, решение…");search.setSingleLine(true);page.addView(search);
  today=new CheckBox(this);today.setText("Только сегодня");page.addView(today);bySession=new CheckBox(this);bySession.setText("По отдельным записям");page.addView(bySession);
  LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.VERTICAL);button(actions,"Скопировать всю сводку",()->copy(report));button(actions,"Повторить ошибки анализа",this::retry);page.addView(actions);
  ScrollView scroll=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int p,int c,int a){}public void onTextChanged(CharSequence s,int a,int b,int c){refresh();}public void afterTextChanged(Editable e){}});today.setOnCheckedChangeListener((v,c)->refresh());bySession.setOnCheckedChangeListener((v,c)->refresh());
  AiJob.schedule(this);refresh();
 }
 private TextView label(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(android.graphics.Color.rgb(20,31,51));v.setTextIsSelectable(true);return v;}
 private void button(LinearLayout parent,String value,Runnable r){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setMinHeight(Math.round(48*getResources().getDisplayMetrics().density));b.setOnClickListener(v->r.run());parent.addView(b);}
 private void begin(){
  if(LocalModel.ready(this)||LocalModel.file(this).length()==LocalModel.SIZE){AiJob.schedule(this);Toast.makeText(this,"Анализ всех записей поставлен в очередь",Toast.LENGTH_LONG).show();return;}
  new AlertDialog.Builder(this).setTitle("Подготовить анализ всех разговоров").setMessage("Нужно один раз скачать Qwen: 1,12 ГБ. После проверки модель будет автоматически разбирать сохранённые разговоры на телефоне. Результаты появятся здесь. Если загрузка уже идёт, можно дождаться её завершения.")
   .setPositiveButton("Скачать по Wi-Fi",(d,w)->download(true)).setNeutralButton("Через любую сеть",(d,w)->download(false)).setNegativeButton("Дождаться / назад",null).show();
 }
 private void download(boolean wifi){try{LocalModel.download(this,wifi);Toast.makeText(this,"Загрузка запущена. После неё начнётся анализ разговоров.",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}
 private void retry(){new Thread(()->{try{for(File dir:dirs()){JSONObject state=ConversationAnalysis.load(dir);state.put("failures",0).remove("error");ConversationAnalysis.save(dir,state);}getSharedPreferences("ai",0).edit().remove("last_error").apply();AiJob.schedule(this);runOnUiThread(this::refresh);}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Повтор не запущен: "+e.getMessage(),Toast.LENGTH_LONG).show());}},"analysis-retry").start();}
 private File[] dirs(){File[] dirs=new File(getFilesDir(),"sessions").listFiles(File::isDirectory);if(dirs==null)return new File[0];Arrays.sort(dirs,(a,b)->b.getName().compareTo(a.getName()));return dirs;}
 private void refresh(){if(loading){dirty=true;return;}loading=true;String filter=search.getText().toString().trim().toLowerCase(Locale.ROOT);boolean onlyToday=today.isChecked(),sessions=bySession.isChecked();
  new Thread(()->{List<Entry> entries=new ArrayList<>();String error="";for(File dir:dirs()){try{List<ConversationAnalysis.Line> lines=ConversationAnalysis.read(dir);if(lines.isEmpty())continue;if(onlyToday&&lines.stream().noneMatch(l->Instant.ofEpochMilli(l.recorded).atZone(ZoneId.systemDefault()).toLocalDate().equals(LocalDate.now())))continue;entries.add(new Entry(dir,ConversationAnalysis.load(dir),lines));}catch(Exception e){error="Некоторые записи требуют проверки: "+e.getMessage();}}
   String warning=error;StringBuilder stamp=new StringBuilder(filter+"|"+sessions+"|"+onlyToday+"|"+warning);for(Entry e:entries)stamp.append(e.dir.getName()).append(e.state.toString().hashCode()).append(e.lines.size());String signature=stamp.toString();runOnUiThread(()->{loading=false;refreshed=System.currentTimeMillis();if(!isFinishing()&&!isDestroyed()&&!signature.equals(lastSignature)){lastSignature=signature;render(entries,filter,sessions,warning);}if(dirty){dirty=false;refresh();}});
  },"analysis-read").start();
 }
 private String sessionTitle(File dir){try{return LocalDateTime.parse(dir.getName(),DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));}catch(Exception e){return dir.getName();}}
 private boolean matches(JSONObject p,String filter){return filter.isEmpty()||(p.optString("text")+" "+p.optString("owner")+" "+p.optString("quote")).toLowerCase(Locale.ROOT).contains(filter);}
 private void render(List<Entry> entries,String filter,boolean sessions,String warning){list.removeAllViews();StringBuilder text=new StringBuilder("Анализ разговоров\n");int total=0,processed=0;for(Entry entry:entries){total+=entry.lines.size();processed+=Math.min(entry.lines.size(),entry.state.optInt("cursor"));}String progress="Записей: "+entries.size()+" · разобрано фрагментов: "+processed+" из "+total;list.addView(label(progress,14));text.append(progress).append('\n');if(!warning.isEmpty())list.addView(label(warning,14));
  if(sessions){for(Entry e:entries){list.addView(label("Запись "+sessionTitle(e.dir)+" · "+e.state.optInt("cursor")+"/"+e.lines.size(),19));for(String kind:ConversationAnalysis.KINDS)addKind(entriesOf(e),kind,filter,text);if(!e.state.optString("error").isEmpty())list.addView(label("Нужна повторная проверка: "+e.state.optString("error"),14));}}
  else{for(String kind:ConversationAnalysis.KINDS)addKind(entries,kind,filter,text);for(Entry e:entries)if(!e.state.optString("error").isEmpty())list.addView(label("Запись "+sessionTitle(e.dir)+": "+e.state.optString("error"),14));}
  if(processed==0){list.addView(label("Здесь появится разбор всех сохранённых разговоров: темы, сведения, решения, обещания, поручения и открытые вопросы. Нажми «Подготовить ИИ / начать анализ». Каждая запись обрабатывается последовательно; прогресс сохраняется.",16));}
  report=text.toString();
 }
 private List<Entry> entriesOf(Entry e){return Collections.singletonList(e);}
 private void addKind(List<Entry> entries,String kind,String filter,StringBuilder report){boolean heading=false;for(Entry e:entries){JSONArray points=e.state.optJSONArray("items");if(points==null)continue;for(int n=0;n<points.length();n++){JSONObject p=points.optJSONObject(n);if(p==null||!kind.equals(p.optString("kind"))||!matches(p,filter))continue;if(!heading){String title=ConversationAnalysis.title(kind);list.addView(label(title,19));report.append('\n').append(title).append(":\n");heading=true;}String point=p.optString("text")+(p.optString("owner").isEmpty()?"":" · "+p.optString("owner"));list.addView(label(point,16));String source=sessionTitle(e.dir)+" · "+p.optString("time");button(list,"Источник: "+source,()->source(e,p));button(list,"Скопировать этот пункт",()->copy(point+"\nИсточник: "+source+"\n«"+p.optString("quote")+"»"));report.append("• ").append(point).append("\nИсточник: ").append(source).append("\n«").append(p.optString("quote")).append("»\n");}}}
 private void source(Entry e,JSONObject p){LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(24,16,24,16);content.addView(label("Исходная реплика "+p.optInt("line")+" · "+p.optString("time")+"\n\n«"+p.optString("quote")+"»",18));ScrollView scroll=new ScrollView(this);scroll.addView(content);new AlertDialog.Builder(this).setTitle(sessionTitle(e.dir)).setView(scroll).setPositiveButton("Вся расшифровка",(d,w)->{StringBuilder raw=new StringBuilder();for(ConversationAnalysis.Line line:e.lines)raw.append('[').append(line.time).append("] ").append(line.text).append('\n');TextView t=label(raw.toString(),16);ScrollView full=new ScrollView(this);full.addView(t);new AlertDialog.Builder(this).setTitle("Исходная запись").setView(full).setPositiveButton("Закрыть",null).setNeutralButton("Скопировать",(x,y)->copy(raw.toString())).show();}).setNeutralButton("Скопировать цитату",(d,w)->copy(p.optString("quote"))).setNegativeButton("Назад",null).show();}
 private void copy(String value){getSystemService(android.content.ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Анализ разговоров",value));Toast.makeText(this,"Скопировано",Toast.LENGTH_SHORT).show();}
 @Override protected void onResume(){super.onResume();handler.removeCallbacks(tick);handler.post(tick);}
 @Override protected void onPause(){handler.removeCallbacks(tick);super.onPause();}
}
