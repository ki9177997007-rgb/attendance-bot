package ru.secondmemory;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Search all memory by a remembered fragment; dates and names are optional. */
public class MemoryActivity extends Activity {
 static final AtomicBoolean searching=new AtomicBoolean();
 private EditText query;private TextView status;private Button period;private LinearLayout results,answer;private volatile int generation;private volatile boolean destroyed;private long from=Long.MIN_VALUE,to=Long.MAX_VALUE;private String report="";
 @Override public void onCreate(Bundle b){super.onCreate(b);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);setContentView(page);ScreenLayout.apply(this,page);ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);page.addView(scroll);
  body.addView(label("Найти в памяти",23));body.addView(label("Напиши, что помнишь: обрывок фразы, тему или вопрос. Дату и имя знать не обязательно.",16));
  query=new EditText(this);query.setHint("Например: деньги так и не перечислили");query.setSingleLine(true);query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);query.setOnEditorActionListener((v,a,event)->{if(a==EditorInfo.IME_ACTION_SEARCH){runSearch();return true;}return false;});body.addView(query);
  button(body,"Найти",this::runSearch);period=new Button(this);period.setText("Период: всё время");period.setAllCaps(false);period.setOnClickListener(v->choosePeriod());body.addView(period);
  status=label("Поиск идёт по исходным разговорам и готовым сводкам. Для поиска по смыслу используется локальная Qwen, если она подготовлена.",14);body.addView(status);answer=new LinearLayout(this);answer.setOrientation(LinearLayout.VERTICAL);body.addView(answer);results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);body.addView(results);
  button(body,"Скопировать всё найденное",()->copy(report));button(body,"Обзор разговоров за день или всё время",()->startActivity(new Intent(this,AnalysisActivity.class)));button(body,"Подготовить ИИ для поиска по смыслу",()->startActivity(new Intent(this,AnalysisActivity.class).putExtra("prepare",true)));
 }
 private TextView label(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(android.graphics.Color.rgb(20,31,51));t.setTextIsSelectable(true);t.setPadding(0,8,0,8);return t;}
 private void button(LinearLayout parent,String value,Runnable action){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setMinHeight(Math.round(48*getResources().getDisplayMetrics().density));b.setOnClickListener(v->action.run());parent.addView(b);}
 private void choosePeriod(){new AlertDialog.Builder(this).setTitle("Где искать").setItems(new String[]{"Всё время","Сегодня","Вчера","Последние 7 дней","Выбрать дату"},(d,n)->{LocalDate now=LocalDate.now();if(n==4){new DatePickerDialog(this,(p,y,m,day)->setPeriod(LocalDate.of(y,m+1,day),LocalDate.of(y,m+1,day).plusDays(1)),now.getYear(),now.getMonthValue()-1,now.getDayOfMonth()).show();return;}if(n==0)setPeriod(null,null);else if(n==1)setPeriod(now,now.plusDays(1));else if(n==2)setPeriod(now.minusDays(1),now);else setPeriod(now.minusDays(6),now.plusDays(1));}).show();}
 private void setPeriod(LocalDate first,LocalDate end){generation++;from=first==null?Long.MIN_VALUE:first.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();to=end==null?Long.MAX_VALUE:end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();period.setText(first==null?"Период: всё время":"Период: "+first+((end.equals(first.plusDays(1)))?"":" — "+end.minusDays(1)));if(!query.getText().toString().trim().isEmpty())runSearch();}
 private boolean valid(int id){return !destroyed&&id==generation;}
 private void ui(int id,Runnable action){runOnUiThread(()->{if(valid(id))action.run();});}
 private void runSearch(){String text=query.getText().toString().trim();if(text.isEmpty()){query.setError("Напиши хотя бы обрывок разговора");return;}if(text.length()>500){query.setError("Сократи запрос до 500 символов");return;}int id=++generation;long start=from,end=to;answer.removeAllViews();results.removeAllViews();report="";status.setText("Ищу во всех сохранённых фразах…");new Thread(()->{
   try{List<MemorySearch.Doc> docs=MemorySearch.load(new File(getFilesDir(),"sessions"));if(!valid(id))return;List<MemorySearch.Hit> hits=MemorySearch.find(docs,text,Collections.emptyList(),start,end);ui(id,()->render(text,hits,false));
    if(!LocalModel.ready(this)){ui(id,()->status.setText("Поиск по словам, формам слов и неточным фразам завершён. Для близких по смыслу формулировок один раз подготовь ИИ кнопкой ниже."));return;}
    if(RecorderService.speechPending>2){ui(id,()->status.setText("Найдены совпадения по словам. Поиск по смыслу пока отложен: телефон распознаёт накопившийся звук. Повтори поиск после обработки."));return;}
    while(!searching.compareAndSet(false,true)){if(!valid(id))return;ui(id,()->status.setText("Совпадения по словам готовы. Жду завершения предыдущего поиска по смыслу…"));Thread.sleep(150);}
    try{ui(id,()->status.setText("Совпадения по словам готовы. Уточняю поиск по смыслу на телефоне…"));if(AiJob.isActive())LocalAi.cancel();long waiting=System.currentTimeMillis();while(AiJob.isActive()){if(!valid(id))return;if(System.currentTimeMillis()-waiting>20000)throw new IOException("Фоновый разбор ещё освобождает модель. Повтори поиск.");Thread.sleep(80);}
     if(!valid(id))return;try(LocalAi ai=new LocalAi(LocalModel.file(this).getAbsolutePath())){List<String> terms=MemorySearch.parseTerms(ai.expand(MemorySearch.expandPrompt(text)));if(!valid(id))return;List<MemorySearch.Hit> semantic=MemorySearch.find(docs,text,terms,start,end);ui(id,()->{render(text,semantic,true);status.setText("Подбираю краткий ответ по найденным исходным репликам…");});if(semantic.isEmpty()){ui(id,()->status.setText("Подходящих фрагментов не нашёл. Попробуй другой обрывок или убери ограничение по времени."));return;}
      List<ConversationAnalysis.Line> evidence=MemorySearch.evidence(semantic);JSONArray points=MemorySearch.parseAnswer(ai.analyze(MemorySearch.answerPrompt(text,evidence)),evidence,text,terms);ui(id,()->{renderAnswer(points,semantic);status.setText("Поиск завершён. Ниже — возможные совпадения; открой контекст, чтобы проверить нужный разговор.");});
     }
    }finally{searching.set(false);AiJob.schedule(this);}
   }catch(Exception|LinkageError|OutOfMemoryError e){ui(id,()->status.setText("Дополнительный поиск не завершён: "+e.getMessage()+". Найденные фрагменты по словам остаются доступны."));}
  },"memory-search").start();}
 private String date(MemorySearch.Doc d){return Instant.ofEpochMilli(d.line.recorded).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));}
 private void render(String text,List<MemorySearch.Hit> hits,boolean expanded){results.removeAllViews();StringBuilder out=new StringBuilder("Запрос: "+text+"\n");String heading=hits.isEmpty()?"Совпадений пока нет":(expanded?"Возможные совпадения по смыслу и словам":"Совпадения по словам")+" · "+hits.size();results.addView(label(heading,19));out.append(heading).append('\n');for(MemorySearch.Hit h:hits){MemorySearch.Doc d=h.doc;results.addView(label(date(d),15));results.addView(label("«"+d.line.text+"»",16));button(results,"Контекст разговора · "+date(d),()->context(d));out.append('\n').append(date(d)).append("\n«").append(d.line.text).append("»\nКонтекст:\n").append(d.context);}report=out.toString();}
 private void renderAnswer(JSONArray points,List<MemorySearch.Hit> hits){answer.removeAllViews();if(points.length()==0){answer.addView(label("В найденных репликах нет подтверждённого ответа на вопрос. Посмотри возможные совпадения ниже.",16));return;}answer.addView(label("Кратко по найденным репликам",19));StringBuilder out=new StringBuilder("Кратко по найденным репликам\n");for(int i=0;i<points.length();i++){JSONObject p=points.optJSONObject(i);if(p==null)continue;int index=p.optInt("line")-1;if(index<0||index>=hits.size())continue;MemorySearch.Doc d=hits.get(index).doc;String text=p.optString("text");answer.addView(label(text,17));button(answer,"Проверить источник · "+date(d),()->context(d));out.append("• ").append(text).append("\nИсточник: ").append(date(d)).append("\n«").append(d.line.text).append("»\n");}report=out+"\n"+report;}
 private void context(MemorySearch.Doc d){TextView t=label(d.context,17);ScrollView scroll=new ScrollView(this);scroll.addView(t);new AlertDialog.Builder(this).setTitle(date(d)).setView(scroll).setPositiveButton("Весь разговор",(v,n)->{StringBuilder all=new StringBuilder();for(ConversationAnalysis.Line l:d.all)all.append('[').append(l.time).append("] ").append(l.text).append('\n');showText("Исходный разговор",all.toString());}).setNeutralButton("Скопировать",(v,n)->copy(date(d)+"\n"+d.context)).setNegativeButton("Закрыть",null).show();}
 private void showText(String title,String text){TextView t=label(text,16);ScrollView scroll=new ScrollView(this);scroll.addView(t);new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Закрыть",null).setNeutralButton("Скопировать",(d,w)->copy(text)).show();}
 private void copy(String value){getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Память",value));Toast.makeText(this,"Скопировано",Toast.LENGTH_SHORT).show();}
 @Override public void onDestroy(){destroyed=true;generation++;super.onDestroy();}
}
