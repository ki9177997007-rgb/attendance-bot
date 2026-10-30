package ru.secondmemory;
import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.text.DateFormat;
import java.util.Date;
public class MainActivity extends Activity {
 private TextView status;
 @Override public void onCreate(Bundle b){super.onCreate(b);
 LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(30,50,30,30);
 status=new TextView(this);l.addView(status);
 Button start=new Button(this);start.setText("Начать запись");l.addView(start);
 Button stop=new Button(this);stop.setText("Остановить");l.addView(stop);
 Button refresh=new Button(this);refresh.setText("Показать сохранённые записи");l.addView(refresh);
 ScrollView s=new ScrollView(this);s.addView(l);setContentView(s);showFiles();
 start.setOnClickListener(v->startRecording());
 stop.setOnClickListener(v->{Intent i=new Intent(this,RecorderService.class);i.setAction("STOP");startService(i);status.setText("Запись остановлена. Нажмите «Показать сохранённые записи».");});
 refresh.setOnClickListener(v->showFiles());
 }
 private void startRecording(){
 if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
 requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);return;
 }
 if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
 requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},2);
 }
 Intent i=new Intent(this,RecorderService.class);i.setAction("START");
 try { startForegroundService(i);status.setText("Запись запущена. Смотрите уведомление Android.");}
 catch(Exception e){status.setText("Не удалось запустить запись: "+e.getMessage());}
 }
 @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
 super.onRequestPermissionsResult(code,permissions,results);
 if(code==1 && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)startRecording();
 }
 private void showFiles(){
 File dir=new File(getFilesDir(),"recordings");
 File[] files=dir.listFiles();
 StringBuilder sb=new StringBuilder("Вторая память, прототип 0.1\nТолько локальная запись. Без распознавания и YouGile.\n\n");
 if(files==null||files.length==0)sb.append("Записей пока нет.");
 else for(File f:files)sb.append(f.getName()).append(" (").append(f.length()/1024).append(" КБ)\n");
 status.setText(sb.toString());
 }
}