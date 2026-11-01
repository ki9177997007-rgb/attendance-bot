package ru.secondmemory;
import android.content.*;
import android.media.AudioAttributes;
import android.os.*;
import android.speech.tts.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
/** Offline Android TTS; the capture timeline marks spoken audio to prevent feedback. */
final class VoiceOutput {
 private static TextToSpeech tts;private static boolean ready;private static final Handler main=new Handler(Looper.getMainLooper());
 private static final class Utterance {final String text;final JSONArray ids;final Context context;Utterance(Context c,String t,JSONArray ids){context=c;text=t;this.ids=ids;}}
 private static final ArrayDeque<Utterance> waiting=new ArrayDeque<>();private static final ConcurrentHashMap<String,Utterance> queued=new ConcurrentHashMap<>();private static final ArrayDeque<long[]> intervals=new ArrayDeque<>();private static long active;
 static volatile String status="Голос ещё не проверен";static volatile long followUntil;
 static void init(Context c){main.post(()->{if(tts!=null)return;Context app=c.getApplicationContext();tts=new TextToSpeech(app,result->{ready=false;if(result==TextToSpeech.SUCCESS){Set<Voice> voices=tts.getVoices();Voice chosen=null;if(voices!=null)for(Voice v:voices)if("ru".equals(v.getLocale().getLanguage())&&!v.isNetworkConnectionRequired()&&(v.getFeatures()==null||!v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))&&(chosen==null||v.getQuality()>chosen.getQuality()))chosen=v;if(chosen!=null){ready=tts.setVoice(chosen)==TextToSpeech.SUCCESS;tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){public void onStart(String id){Utterance u=queued.get(id);if(u!=null&&u.ids!=null)AssistantEngine.remember(u.context,u.ids,System.currentTimeMillis());synchronized(intervals){active=System.currentTimeMillis();}}public void onDone(String id){queued.remove(id);ended();}public void onError(String id){queued.remove(id);ended();status="Не удалось озвучить ответ";}});}}status=ready?"Русский голос готов, без интернета":"Нужен русский офлайн-голос в настройках синтеза речи Android";while(!waiting.isEmpty()){Utterance s=waiting.removeFirst();if(ready)play(s);}});});}
 private static void ended(){synchronized(intervals){if(active>0){intervals.add(new long[]{active,System.currentTimeMillis()+700});active=0;while(intervals.size()>100)intervals.removeFirst();}followUntil=System.currentTimeMillis()+20000;}}
 static void speak(Context c,String text){speakTasks(c,text,null);}
 static void speakTasks(Context c,String text,JSONArray ids){init(c);Context app=c.getApplicationContext();main.post(()->{app.getSharedPreferences("voice",0).edit().putString("last_answer",text).apply();Utterance u=new Utterance(app,text,ids);if(ready)play(u);else if(waiting.size()<3)waiting.addLast(u);});}
 private static void play(Utterance u){String id=UUID.randomUUID().toString();queued.put(id,u);if(tts.speak(u.text,TextToSpeech.QUEUE_ADD,null,id)==TextToSpeech.ERROR){queued.remove(id);status="Не удалось озвучить ответ: проверь русский голос Android";}}
 static void quiet(){main.post(()->{waiting.clear();queued.clear();if(tts!=null)tts.stop();ended();followUntil=0;});}
 static boolean echo(long start,long duration){long end=start+duration;long overlap=0;synchronized(intervals){for(long[] r:intervals)overlap+=Math.max(0,Math.min(end,r[1])-Math.max(start,r[0]));if(active>0)overlap+=Math.max(0,end-Math.max(start,active));}return duration>0&&overlap>duration/3;}
}
