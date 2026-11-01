package ru.secondmemory;
import android.app.job.*;
import android.content.*;
import java.util.concurrent.atomic.AtomicBoolean;
/** Background fallback. Visible activities use AnalysisService for an immediate start. */
public class AiJob extends JobService {
 static final AtomicBoolean active=new AtomicBoolean();
 static boolean isActive(){return active.get();}
 static volatile String status="Анализ ожидает запуска";
 private AtomicBoolean cancelled;private Thread worker;
 static void schedule(Context c){schedule(c,2000);}
 static void schedule(Context c,long delay){if(c.getSharedPreferences("ai",0).getBoolean("paused",false)||active.get()||AnalysisService.running)return;JobScheduler jobs=c.getSystemService(JobScheduler.class);if(jobs.getPendingJob(405)!=null)return;int result=jobs.schedule(new JobInfo.Builder(405,new ComponentName(c,AiJob.class)).setMinimumLatency(delay).setPersisted(true).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());status=result==JobScheduler.RESULT_SUCCESS?"В очереди Android. На экране анализа можно начать сразу.":"Android отложил запуск. Открой анализ и нажми «Начать анализ».";}
 static void pauseForSpeech(){if(active.get()&&RecorderService.speechPending>2)try{LocalAi.cancel();}catch(LinkageError ignored){}}
 public boolean onStartJob(JobParameters params){AtomicBoolean stop=new AtomicBoolean();cancelled=stop;worker=new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);boolean more=false;try{if(!getSharedPreferences("ai",0).getBoolean("paused",false)&&!AnalysisService.running)more=AnalysisWork.runPass(this,stop::get,null);}finally{if(!stop.get()){jobFinished(params,false);if(more)new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->schedule(getApplicationContext(),30000),500);}}},"scheduled-analysis");worker.start();return true;}
 public boolean onStopJob(JobParameters p){if(cancelled!=null)cancelled.set(true);if(worker!=null)worker.interrupt();if(AnalysisWork.owner==worker)try{LocalAi.cancel();}catch(LinkageError ignored){}return true;}
}
