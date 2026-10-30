package ru.secondmemory;
import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
public class MainActivity extends Activity {
 @Override public void onCreate(Bundle b){super.onCreate(b);
 LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(30,50,30,30);
 TextView t=new TextView(this);t.setText("Вторая память — прототип 0.1\nЗаписывает звук локально. Пока без распознавания и YouGile.");l.addView(t);
 Button start=new Button(this);start.setText("Начать запись");l.addView(start);
 Button stop=new Button(this);stop.setText("Остановить");l.addView(stop);setContentView(l);
 start.setOnClickListener(v->{if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS},1);return;} Intent i=new Intent(this,RecorderService.class);i.setAction("START");startForegroundService(i);});
 stop.setOnClickListener(v->{Intent i=new Intent(this,RecorderService.class);i.setAction("STOP");startService(i);});
 }
}