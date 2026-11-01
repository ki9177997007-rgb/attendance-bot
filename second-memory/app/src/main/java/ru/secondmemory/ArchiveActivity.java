package ru.secondmemory;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** One-time setup; credentials never appear in reports or the APK. */
public class ArchiveActivity extends Activity {
 private EditText endpoint,token;private TextView state;
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);ScrollView scroll=new ScrollView(this);scroll.addView(page);setContentView(scroll);ScreenLayout.apply(this,page);
  TextView title=new TextView(this);title.setText("Архив для ChatGPT");title.setTextSize(23);page.addView(title);
  TextView help=new TextView(this);help.setText("Записи и расшифровки сначала сохраняются на телефоне. Передача начинается только после настройки приёмника. Подтверждение доставки не означает, что ChatGPT уже разобрал разговор.\n\nФайл подключения содержит личный ключ. Не пересылай его другим людям.");help.setTextSize(16);page.addView(help);
  endpoint=new EditText(this);endpoint.setHint("HTTPS-адрес приёмника /sync");endpoint.setSingleLine(true);endpoint.setSaveEnabled(false);page.addView(endpoint);
  token=new EditText(this);token.setHint("Личный ключ подключения");token.setSingleLine(true);token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);token.setSaveEnabled(false);token.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);page.addView(token);
  state=new TextView(this);state.setTextSize(16);page.addView(state);status();
  button(page,"Импортировать файл подключения",()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),11));
  button(page,"Сохранить подключение",()->{try{ArchiveConfig.save(this,endpoint.getText().toString().trim(),token.getText().toString().trim());token.setText("");ArchiveJob.status="Подключение сохранено. Передача ожидает сеть и запуск Android.";ArchiveJob.periodic(this);status();}catch(Exception e){state.setText("Не сохранено: "+e.getMessage());}});
  button(page,"Повторить передачу",()->{ArchiveJob.schedule(this);status();});
  button(page,"Отключить передачу",()->new AlertDialog.Builder(this).setTitle("Отключить архив?").setMessage("Новые передачи будут остановлены. Исходные записи и уже переданные данные не удаляются.").setPositiveButton("Отключить",(d,w)->{ArchiveConfig.clear(this);android.app.job.JobScheduler j=getSystemService(android.app.job.JobScheduler.class);j.cancel(406);j.cancel(407);ArchiveJob.status="Передача отключена. Записи остаются на телефоне.";token.setText("");endpoint.setText("");status();}).setNegativeButton("Назад",null).show());
  button(page,"Назад",this::finish);
 }
 private void button(LinearLayout p,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setOnClickListener(v->action.run());p.addView(b);}
 private void status(){state.setText((ArchiveConfig.configured(this)?"Подключение сохранено.\n":"Подключение не настроено.\n")+ArchiveJob.status);}
 @Override protected void onResume(){super.onResume();if(state!=null)status();}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=11||result!=RESULT_OK||data==null||data.getData()==null)return;
  new Thread(()->{try(InputStream in=getContentResolver().openInputStream(data.getData());ByteArrayOutputStream out=new ByteArrayOutputStream()){
   if(in==null)throw new IOException("Не удалось открыть файл");byte[] buf=new byte[1024];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>8192)throw new IOException("Неверный файл подключения");}
   JSONObject config=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));String address=config.getString("endpoint"),key=config.getString("token");ArchiveConfig.save(this,address,key);ArchiveJob.periodic(this);ArchiveJob.status="Файл подключения принят. Передача ожидает сеть и запуск Android.";runOnUiThread(()->{if(!isFinishing()){endpoint.setText(address);token.setText("");status();}});
  }catch(Exception e){runOnUiThread(()->{if(!isFinishing())state.setText("Подключение не сохранено: "+e.getClass().getSimpleName());});}},"archive-import").start();
 }
}
