package ru.secondmemory;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class TaskDb extends SQLiteOpenHelper {
    TaskDb(Context c) { super(c, "tasks.db", null, 3); }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE voice_receipts (id TEXT PRIMARY KEY)");
        db.execSQL("CREATE TABLE tasks (id TEXT PRIMARY KEY, session TEXT NOT NULL, text TEXT NOT NULL, priority INTEGER NOT NULL, created INTEGER NOT NULL, remote TEXT, company TEXT, completed INTEGER NOT NULL DEFAULT 0, checked INTEGER NOT NULL DEFAULT 0, due INTEGER NOT NULL DEFAULT 0, assignee TEXT NOT NULL DEFAULT '', notified INTEGER NOT NULL DEFAULT 0, analyzed INTEGER NOT NULL DEFAULT 0, dirty INTEGER NOT NULL DEFAULT 0)");
    }
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { if(oldVersion<2){
 db.execSQL("ALTER TABLE tasks ADD COLUMN due INTEGER NOT NULL DEFAULT 0");
 db.execSQL("ALTER TABLE tasks ADD COLUMN assignee TEXT NOT NULL DEFAULT ''");
 db.execSQL("ALTER TABLE tasks ADD COLUMN notified INTEGER NOT NULL DEFAULT 0");
 db.execSQL("ALTER TABLE tasks ADD COLUMN analyzed INTEGER NOT NULL DEFAULT 0");
 db.execSQL("ALTER TABLE tasks ADD COLUMN dirty INTEGER NOT NULL DEFAULT 0");
 } if(oldVersion<3)db.execSQL("CREATE TABLE IF NOT EXISTS voice_receipts (id TEXT PRIMARY KEY)"); }
    boolean received(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM voice_receipts WHERE id=?",new String[]{id})){return c.moveToFirst();}}
    void receipt(String id){ContentValues v=new ContentValues();v.put("id",id);getWritableDatabase().insertWithOnConflict("voice_receipts",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    void voiceReminder(String id,String text,long due){ContentValues v=new ContentValues();v.put("id",id);v.put("session","voice");v.put("text",text);v.put("priority",1);v.put("created",System.currentTimeMillis());v.put("due",due);v.put("analyzed",1);getWritableDatabase().insertWithOnConflict("tasks",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    String addReminder(String text,long due){String id=UUID.randomUUID().toString();ContentValues v=new ContentValues();v.put("id",id);v.put("session","manual");v.put("text",text);v.put("priority",1);v.put("created",System.currentTimeMillis());v.put("due",due);v.put("analyzed",1);getWritableDatabase().insertOrThrow("tasks",null,v);return id;}
    void offer(String session,String text,boolean priority){offer(session,text,priority,System.currentTimeMillis());}
    void offer(String session, String text, boolean priority,long recorded) {
        if (!TaskRules.candidate(text)) return;
        String id = UUID.nameUUIDFromBytes((session+"\n"+TaskRules.normalize(text)).getBytes(StandardCharsets.UTF_8)).toString();
        ContentValues v=new ContentValues();
        v.put("id",id);v.put("session",session);v.put("text",text);v.put("priority",priority?1:0);v.put("created",recorded);v.put("due",DueParser.parse(text,recorded,java.time.ZoneId.systemDefault()));
        getWritableDatabase().insertWithOnConflict("tasks",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    static final class Row {
        String id,session,text,remote,company; boolean priority,completed,notified,dirty; long created,due; String assignee;
    }
    List<Row> query(boolean pending,String company) {
        List<Row> list=new ArrayList<>();
        String where=pending?"remote IS NULL":"remote IS NOT NULL AND company=?";
        String[] args=pending?null:new String[]{company};
        try(Cursor c=getReadableDatabase().query("tasks",null,where,args,null,null,pending?"priority DESC, created ASC":"checked ASC","30")) {
            while(c.moveToNext()) list.add(row(c));
        }
        return list;
    }
    private Row row(Cursor c) {
        Row r=new Row();r.id=c.getString(c.getColumnIndexOrThrow("id"));r.session=c.getString(c.getColumnIndexOrThrow("session"));
        r.text=c.getString(c.getColumnIndexOrThrow("text"));r.remote=c.getString(c.getColumnIndexOrThrow("remote"));
        r.company=c.getString(c.getColumnIndexOrThrow("company"));r.priority=c.getInt(c.getColumnIndexOrThrow("priority"))!=0;
        r.completed=c.getInt(c.getColumnIndexOrThrow("completed"))!=0;r.created=c.getLong(c.getColumnIndexOrThrow("created"));r.due=c.getLong(c.getColumnIndexOrThrow("due"));r.assignee=c.getString(c.getColumnIndexOrThrow("assignee"));r.notified=c.getInt(c.getColumnIndexOrThrow("notified"))!=0;r.dirty=c.getInt(c.getColumnIndexOrThrow("dirty"))!=0;return r;
    }
    void sent(String id,String remote,String company) {
        ContentValues v=new ContentValues();v.put("remote",remote);v.put("company",company);
        getWritableDatabase().update("tasks",v,"id=?",new String[]{id});
    }
    void checked(String id,boolean done) {
        ContentValues v=new ContentValues();v.put("completed",done?1:0);v.put("checked",System.currentTimeMillis());
        getWritableDatabase().update("tasks",v,"id=? AND dirty=0",new String[]{id});
    }
    List<Row> all(){List<Row> rows=new ArrayList<>();try(Cursor c=getReadableDatabase().query("tasks",null,null,null,null,null,"completed ASC, priority DESC, created DESC")){while(c.moveToNext())rows.add(row(c));}return rows;}
    Row get(String id){try(Cursor c=getReadableDatabase().query("tasks",null,"id=?",new String[]{id},null,null,null)){return c.moveToFirst()?row(c):null;}}
    void notified(String id){ContentValues v=new ContentValues();v.put("notified",1);getWritableDatabase().update("tasks",v,"id=?",new String[]{id});}
    void edit(String id,String text,String assignee,long due,boolean done){ContentValues v=new ContentValues();v.put("text",text);v.put("assignee",assignee);v.put("due",due);v.put("completed",done?1:0);v.put("notified",0);v.put("dirty",1);v.put("analyzed",1);getWritableDatabase().update("tasks",v,"id=?",new String[]{id});}
    void clean(Row r){ContentValues v=new ContentValues();v.put("dirty",0);getWritableDatabase().update("tasks",v,"id=? AND text=? AND assignee=? AND due=? AND completed=?",new String[]{r.id,r.text,r.assignee,String.valueOf(r.due),r.completed?"1":"0"});}
    void semantic(String session,String source,String action,String assignee,long due,boolean priority,long created){
     String id=UUID.nameUUIDFromBytes((session+"\n"+TaskRules.normalize(source)).getBytes(StandardCharsets.UTF_8)).toString();
     ContentValues v=new ContentValues();v.put("id",id);v.put("session",session);v.put("text",action);v.put("assignee",assignee);v.put("due",due);v.put("created",created);v.put("priority",priority?1:0);v.put("analyzed",1);
     long inserted=getWritableDatabase().insertWithOnConflict("tasks",null,v,SQLiteDatabase.CONFLICT_IGNORE);
     if(inserted==-1){v.remove("id");v.remove("created");v.put("dirty",1);getWritableDatabase().update("tasks",v,"id=? AND analyzed=0 AND completed=0",new String[]{id});}
    }
    int pendingCount() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM tasks WHERE remote IS NULL",null)) { c.moveToFirst();return c.getInt(0); }
    }
    void export(File destination) throws IOException {
        File tmp=new File(destination.getPath()+".tmp");
        try(Writer w=new OutputStreamWriter(new FileOutputStream(tmp),StandardCharsets.UTF_8);
            Cursor c=getReadableDatabase().query("tasks",null,null,null,null,null,"priority DESC, created ASC")) {
            w.write("ПОРУЧЕНИЯ — черновики, выделенные по явным речевым командам\n\n");
            while(c.moveToNext()) {
                Row r=row(c);
                w.write((r.completed?"[ГОТОВО] ":"[ОТКРЫТО] ")+(r.priority?"[ПРИОРИТЕТ] ":"[ПРОВЕРИТЬ ГОЛОС] ")+r.text+"\n");
                w.write("Исполнитель: "+r.assignee+"; срок: "+(r.due==0?"не задан":new java.util.Date(r.due))+"\n");
                w.write("Запись: "+r.session+"; "+(r.remote==null?"ожидает отправки":"YouGile ID: "+r.remote)+"\n\n");
            }
        }
        if(!tmp.renameTo(destination)) throw new IOException("Не удалось сохранить список задач");
    }
}
