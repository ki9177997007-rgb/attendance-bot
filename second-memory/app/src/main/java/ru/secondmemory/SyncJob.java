package ru.secondmemory;
import android.app.job.*;
import android.content.*;

public class SyncJob extends JobService {
    private Thread work;
    static void schedule(Context c) {
        c.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(404,new ComponentName(c,SyncJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15*60*1000L).setPersisted(true).build());
    }
    @Override public boolean onStartJob(JobParameters p) {
        work=new Thread(()->{SyncEngine.run(getApplicationContext());jobFinished(p,false);},"memory-sync-job");work.start();return true;
    }
    @Override public boolean onStopJob(JobParameters p) { if(work!=null)work.interrupt();return true; }
}
