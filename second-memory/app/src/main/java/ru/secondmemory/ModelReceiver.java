package ru.secondmemory;
import android.app.DownloadManager;
import android.content.*;
public class ModelReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){long id=i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-2);if(id==c.getSharedPreferences("ai",0).getLong("download",-1))AiJob.schedule(c);}
}
