package ru.secondmemory;
import android.content.*;
import android.media.AudioAttributes;
import android.os.*;
import android.speech.tts.*;
import java.util.*;
/** Offline Android TTS; the capture timeline marks spoken audio to prevent feedback. */
final class VoiceOutput {
 private static TextToSpeech tts;private static boolean ready;private static final Handler main=new Handler(Looper.getMainLooper());
 private static final ArrayDeque<String> waiting=new ArrayDeque<>();private static final ArrayDeque<long[]> intervals=new ArrayDeque<>();private static long active;
 static volatile String status="Голос ещё не проверен";static volatile long followUntil;
 static void init(Context c){main.post(()->{if(tts!=null)return;Context app=c.getApplicationContext();tts=new TextToSpeech(app,result->{ready=false;if(result==TextToSpeech.SUCCESS){Set<Voice> voices=tts.getVoices();Voice chosen=null;if(voices!=null)for(Voice v:voices)if("ru".equals(v.getLocale().getLanguage())&&!v.isNetworkConnectionRequired()&&(chosen==null||v.getQuality()>chosen.getQuality()))chosen=v;if(chosen!=null){tts.setVoice(chosen);ready=true;tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){public void onStart(String id){synchronized(intervals){active=System.currentTimeMillis();}}public void onDone(String id){ended();}public void onError(String id){ended();status="Не удалось озвучить ответ";}});}}status=ready?"Русский голос готов, без интернета":"Нужен русский офлайн-голос в настройках синтеза речи Android";while(!waiting.isEmpty()){String s=waiting.removeFirst();if(ready)play(s);}});});}
 private static void ended(){synchronized(intervals){if(active>0){intervals.add(new long[]{active,System.currentTimeMillis()+700});active=0;while(intervals.size()>100)intervals.removeFirst();}followUntil=System.currentTimeMillis()+20000;}}
 static void speak(Context c,String text){init(c);main.post(()->{c.getSharedPreferences("voice",0).edit().putString("last_answer",text).apply();if(ready)play(text);else if(waiting.size()<3)waiting.addLast(text);});}
 private static void play(String text){tts.speak(text,TextToSpeech.QUEUE_ADD,null,UUID.randomUUID().toString());}
 static void quiet(){main.post(()->{waiting.clear();if(tts!=null)tts.stop();ended();followUntil=0;});}
 static boolean echo(long start,long duration){long end=start+duration;long overlap=0;synchronized(intervals){for(long[] r:intervals)overlap+=Math.max(0,Math.min(end,r[1])-Math.max(start,r[0]));if(active>0)overlap+=Math.max(0,end-Math.max(start,active));}return duration>0&&overlap>duration/3;}
}
