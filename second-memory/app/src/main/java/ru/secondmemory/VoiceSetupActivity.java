package ru.secondmemory;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.*;
import android.widget.*;
import java.util.*;

public class VoiceSetupActivity extends Activity {
 private TextView status;private Button enroll;private volatile boolean cancelled;private final Handler main=new Handler(Looper.getMainLooper());
 public void onCreate(Bundle b){super.onCreate(b);LinearLayout p=new LinearLayout(this);p.setOrientation(1);p.setPadding(24,24,24,24);ScrollView scroll=new ScrollView(this);scroll.addView(p);setContentView(scroll);ScreenLayout.apply(this,p);
  TextView title=new TextView(this);title.setText("Голосовой помощник");title.setTextSize(26);p.addView(title);status=new TextView(this);status.setTextSize(18);p.addView(status);show();
  TextView help=new TextView(this);help.setText("Один раз запиши свой голос в тихой комнате. Нажми кнопку и читай 15 секунд:\n\nПомощник, что мне нужно сделать сегодня? Сначала надо отправить документы. Потом позвонить Иванову. Затем проверить ответ и назначить встречу. Помощник, вспомни, о чём мы вчера говорили. Напомни мне через десять минут проверить почту.\n\nПосле настройки включи запись на главном экране. Обращайся «Помощник». Все разговоры сохраняются; команды проходят проверку сходства с твоим голосом. Она может ошибаться в шуме и не является защитой от подделки голоса.");help.setTextSize(17);p.addView(help);
  enroll=new Button(this);enroll.setText("Записать мой голос — 15 секунд");p.addView(enroll);enroll.setOnClickListener(v->start());
  Switch enabled=new Switch(this);enabled.setText("Голосовые команды включены");enabled.setChecked(getSharedPreferences("voice",0).getBoolean("enabled",false));p.addView(enabled);enabled.setOnCheckedChangeListener((v,on)->getSharedPreferences("voice",0).edit().putBoolean("enabled",on).apply());
  Button test=new Button(this);test.setText("Проверить озвучивание");test.setOnClickListener(v->{VoiceOutput.speak(this,"Голос помощника работает. Обратись ко мне: Помощник, что делать?");main.postDelayed(this::show,2000);});p.addView(test);
  Button tts=new Button(this);tts.setText("Настройки русского голоса Android");tts.setOnClickListener(v->{try{startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}catch(ActivityNotFoundException e){Toast.makeText(this,"Открой в настройках телефона «Синтез речи»",Toast.LENGTH_LONG).show();}});p.addView(tts);
  Button back=new Button(this);back.setText("Назад");back.setOnClickListener(v->finish());p.addView(back);VoiceOutput.init(this);
 }
 private void show(){if(status!=null)status.setText((OwnerVoice.enrolled(this)?"Образец голоса сохранён":"Сначала запиши свой голос")+"\nКоманды: "+(getSharedPreferences("voice",0).getBoolean("enabled",false)?"включены":"выключены")+"\n"+VoiceOutput.status+"\n"+getSharedPreferences("voice",0).getString("last_error",""));}
 private void start(){if(RecorderService.running||RecorderService.stopping){status.setText("Останови запись на главном экране на время настройки голоса");return;}if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);return;}enroll.setEnabled(false);cancelled=false;new Thread(()->{AudioRecord mic=null;try(OwnerVoice voice=new OwnerVoice(this)){
   int size=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);mic=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(32000,size*2));if(mic.getState()!=AudioRecord.STATE_INITIALIZED)throw new Exception("Микрофон недоступен");short[] all=new short[16000*15];mic.startRecording();int n=0;while(n<all.length&&!cancelled){int r=mic.read(all,n,Math.min(1600,all.length-n));if(r<0)throw new Exception("Ошибка микрофона");n+=r;final int left=15-n/16000;main.post(()->status.setText("Говори обычным голосом. Осталось "+left+" сек."));}mic.stop();if(cancelled)return;float[][] vectors=new float[3][];for(int k=0;k<3;k++){float[] pcm=new float[80000];double energy=0;for(int i=0;i<pcm.length;i++){pcm[i]=all[k*80000+i]/32768f;energy+=pcm[i]*pcm[i];}if(Math.sqrt(energy/pcm.length)<.005)throw new Exception("Голос слишком тихий. Поднеси телефон ближе и повтори");vectors[k]=voice.embedding(pcm);}if(OwnerVoice.cosine(vectors[0],vectors[1])<.5||OwnerVoice.cosine(vectors[0],vectors[2])<.5)throw new Exception("Образцы не совпали. Повтори без других голосов рядом");float[] avg=new float[vectors[0].length];for(float[] v:vectors){double norm=0;for(float f:v)norm+=f*f;norm=Math.sqrt(norm);for(int i=0;i<v.length;i++)avg[i]+=v[i]/norm;}OwnerVoice.save(this,avg);getSharedPreferences("voice",0).edit().remove("last_error").apply();main.post(()->{show();VoiceOutput.speak(this,"Голос запомнила. Включи запись и скажи: Помощник, что делать?");});
  }catch(Exception|LinkageError e){final String msg="Не удалось: "+e.getMessage();main.post(()->status.setText(msg));}finally{if(mic!=null){try{mic.stop();}catch(Exception ignored){}mic.release();}main.post(()->enroll.setEnabled(true));}},"voice-enroll").start();}
 public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==1&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)start();}
 protected void onDestroy(){cancelled=true;super.onDestroy();}
}
