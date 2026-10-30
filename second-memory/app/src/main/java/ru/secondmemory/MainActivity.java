package ru.secondmemory;
import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
public class MainActivity extends Activity {
 private TextView status;
 private SpeechRecognizer speech;
 private boolean listening=false;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private File transcript;
 private StringBuilder recognized=new StringBuilder();
 private Intent speechIntent;
 @Override public void onCreate(Bundle b){super.onCreate(b);
 LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(30,50,30,30);
 status=new TextView(this);l.addView(status);
 Button start=new Button(this);start.setText("Начать аудиозапись");l.addView(start);
 Button stop=new Button(this);stop.setText("Остановить аудиозапись");l.addView(stop);
 Button listen=new Button(this);listen.setText("Начать распознавание речи");l.addView(listen);
 Button stopListen=new Button(this);stopListen.setText("Остановить распознавание");l.addView(stopListen);
 Button refresh=new Button(this);refresh.setText("Показать сохранённые файлы");l.addView(refresh);
 ScrollView s=new ScrollView(this);s.addView(l);setContentView(s);showFiles();
 start.setOnClickListener(v->startRecording());
 stop.setOnClickListener(v->{Intent i=new Intent(this,RecorderService.class);i.setAction("STOP");startService(i);status.setText("Аудиозапись остановлена.");});
 listen.setOnClickListener(v->startRecognition());
 stopListen.setOnClickListener(v->stopRecognition());
 refresh.setOnClickListener(v->showFiles());
 }
 private boolean micAllowed(){
 if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);return false;}
 return true;
 }
 private void startRecording(){
 if(!micAllowed())return;
 if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},2);
 Intent i=new Intent(this,RecorderService.class);i.setAction("START");
 try{startForegroundService(i);status.setText("Аудиозапись запущена.");}catch(Exception e){status.setText("Ошибка записи: "+e.getMessage());}
 }
 private void startRecognition(){
 if(!micAllowed())return;
 if(!SpeechRecognizer.isRecognitionAvailable(this)){status.setText("На телефоне нет доступного системного сервиса распознавания речи.");return;}
 if(listening)return;
 File dir=new File(getFilesDir(),"transcripts");dir.mkdirs();
 transcript=new File(dir,new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".txt");
 recognized.setLength(0);
 speechIntent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
 speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
 speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU");
 speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
 listening=true;
 speech=SpeechRecognizer.createSpeechRecognizer(this);
 speech.setRecognitionListener(new RecognitionListener(){
 public void onReadyForSpeech(Bundle p){}
 public void onBeginningOfSpeech(){}
 public void onRmsChanged(float r){}
 public void onBufferReceived(byte[] b){}
 public void onEndOfSpeech(){}
 public void onEvent(int t,Bundle b){}
 public void onPartialResults(Bundle b){ArrayList<String> a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(a!=null&&!a.isEmpty())status.setText("Распознавание (предварительно):\n"+a.get(0)+"\n\nСохранено:\n"+recognized);}
 public void onResults(Bundle b){ArrayList<String> a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(a!=null&&!a.isEmpty()){recognized.append(a.get(0)).append("\n");saveTranscript();status.setText("Сохранённый текст:\n"+recognized);}restart();}
 public void onError(int error){if(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS){stopRecognition();status.setText("Нет разрешения на микрофон.");return;}restart();}
 });
 try{speech.startListening(speechIntent);status.setText("Распознавание русского языка запущено. Держите приложение открытым.");}
 catch(Exception e){stopRecognition();status.setText("Ошибка распознавания: "+e.getMessage());}
 }
 private void restart(){if(listening)handler.postDelayed(()->{if(listening&&speech!=null){try{speech.cancel();speech.startListening(speechIntent);}catch(Exception e){status.setText("Ошибка перезапуска: "+e.getMessage());}}},600);}
 private void saveTranscript(){if(transcript==null)return;try(FileOutputStream out=new FileOutputStream(transcript)){out.write(recognized.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception e){status.setText("Ошибка сохранения текста: "+e.getMessage());}}
 private void stopRecognition(){listening=false;handler.removeCallbacksAndMessages(null);if(speech!=null){speech.cancel();speech.destroy();speech=null;}saveTranscript();status.setText("Распознавание остановлено.\n"+recognized);}
 @Override protected void onDestroy(){stopRecognition();super.onDestroy();}
 private void showFiles(){
 StringBuilder sb=new StringBuilder("Вторая память, прототип 0.2\nАудиозапись и системное распознавание русского языка.\nРаспознавание работает только при открытом приложении; доступность зависит от Android.\nОдновременная запись и распознавание могут конфликтовать из-за доступа к микрофону.\n\n");
 for(String folder:new String[]{"recordings","transcripts"}){File[] files=new File(getFilesDir(),folder).listFiles();sb.append(folder).append(":\n");if(files==null||files.length==0)sb.append("Нет файлов\n");else for(File f:files)sb.append(f.getName()).append(" (").append(f.length()/1024).append(" КБ)\n");}
 status.setText(sb.toString());
 }
}