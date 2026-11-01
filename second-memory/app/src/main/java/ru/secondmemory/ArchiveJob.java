package ru.secondmemory;

import android.app.job.*;
import android.content.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

public class ArchiveJob extends JobService {
 private static final AtomicBoolean busy=new AtomicBoolean();
 static volatile String status="Архив для ChatGPT не подключён. Записи остаются на телефоне.";
 private volatile boolean stopped;private Thread worker;
 static void schedule(Context c){if(!ArchiveConfig.configured(c))return;JobScheduler j=c.getSystemService(JobScheduler.class);if(j.getPendingJob(406)==null)j.schedule(new JobInfo.Builder(406,new ComponentName(c,ArchiveJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(2000).setPersisted(true).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());}
 static void periodic(Context c){if(!ArchiveConfig.configured(c))return;c.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(407,new ComponentName(c,ArchiveJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15*60*1000L).setPersisted(true).build());schedule(c);}
 @Override public boolean onStartJob(JobParameters params){stopped=false;worker=new Thread(()->{
  boolean retry=false;if(!busy.compareAndSet(false,true)){jobFinished(params,true);return;}
  try{
   JSONObject config=ArchiveConfig.load(this);if(config==null){status="Архив отключён. Записи остаются на телефоне.";return;}
   String endpoint=config.getString("endpoint"),token=config.getString("token");ArchiveOutbox box=new ArchiveOutbox(this);int skipped=box.recover(this);
   int sent=0;for(File event:box.pending(endpoint,50)){
    if(stopped||Thread.currentThread().isInterrupted()||!ArchiveConfig.configured(this))return;
    JSONObject current=ArchiveConfig.load(this);if(current==null||!endpoint.equals(current.optString("endpoint"))||!token.equals(current.optString("token")))return;
    JSONObject body=ArchiveOutbox.read(event);JSONObject ack=send(endpoint,token,body);ArchiveEnvelope.verifyAck(body,ack);box.accepted(event,endpoint,body);sent++;
   }
   retry=!box.pending(endpoint,1).isEmpty();status="Архив: подтверждено фрагментов за проход — "+sent+(retry?". Остальные в очереди.":". Очередь передана.")+(skipped>0?" Не разобрано строк: "+skipped+"; оригиналы сохранены.":"")+" Это не подтверждение анализа ChatGPT.";
  }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();retry=true;status="Передача в архив отложена. Записи и очередь сохранены. "+e.getClass().getSimpleName();}
  finally{busy.set(false);if(!stopped)jobFinished(params,retry);}
 },"archive-upload");worker.start();return true;}
 static JSONObject send(String endpoint,String token,JSONObject body)throws Exception{
  ArchiveConfig.validate(endpoint,token);HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
  try{c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setRequestProperty("X-Memory-Token",token);
   byte[] data=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(data.length);try(OutputStream out=c.getOutputStream()){out.write(data);}
   int http=c.getResponseCode();if(http<200||http>=300)throw new IOException("Приёмник ответил "+http);
   try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>16000)throw new IOException("Слишком большой ответ приёмника");}return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}
  }finally{c.disconnect();}
 }
 @Override public boolean onStopJob(JobParameters p){stopped=true;if(worker!=null)worker.interrupt();return true;}
}
