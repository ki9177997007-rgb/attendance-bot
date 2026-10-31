package ru.secondmemory;
import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

final class SyncEngine {
    private static final AtomicBoolean busy=new AtomicBoolean();
    static volatile String status="YouGile не подключён";
    static void kick(Context context) {
        Context app=context.getApplicationContext();
        new Thread(()->run(app),"memory-sync").start();
    }
    static void run(Context context) {
        if(!busy.compareAndSet(false,true))return;
        try(TaskDb db=new TaskDb(context)) {
            JSONObject config=SecureConfig.load(context);
            if(config==null){status="YouGile не подключён · задач в очереди: "+db.pendingCount();return;}
            status="Синхронизация YouGile…";
            YouGileApi api=new YouGileApi(config.getString("key"));
            String company=config.getString("company");
            for(TaskDb.Row row:db.query(true,company)) {
                if(Thread.currentThread().isInterrupted())return;
                // Stable idempotency key survives a crash after the POST but before saving its reply.
                db.sent(row.id,api.send(row,config),company);
                Thread.sleep(350);
            }
            for(TaskDb.Row row:db.query(false,company)) {
                if(Thread.currentThread().isInterrupted())return;
                if(row.dirty){api.call("PUT","/api-v2/tasks/"+row.remote,new JSONObject().put("title",TaskRules.title(row.text)).put("description",YouGileApi.description(row)).put("completed",row.completed));db.clean(row);}
                JSONObject remote=api.call("GET","/api-v2/tasks/"+row.remote,null);
                db.checked(row.id,remote.optBoolean("completed",false));
                Thread.sleep(350);
            }
            ReminderReceiver.restore(context);
            status="YouGile подключён · в очереди: "+db.pendingCount();
        } catch(Exception e) {
            status="YouGile: отправка отложена. "+e.getMessage();
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
        } finally {
            try(TaskDb db=new TaskDb(context)) {
                File dir=new File(context.getFilesDir(),"sessions");dir.mkdirs();db.export(new File(dir,"tasks.txt"));
            } catch(Exception ignored) { }
            busy.set(false);
        }
    }
}
