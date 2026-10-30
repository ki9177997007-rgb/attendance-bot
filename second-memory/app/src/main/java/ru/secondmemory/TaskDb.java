package ru.secondmemory;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class TaskDb extends SQLiteOpenHelper {
    TaskDb(Context c) { super(c, "tasks.db", null, 1); }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tasks (id TEXT PRIMARY KEY, session TEXT NOT NULL, text TEXT NOT NULL, priority INTEGER NOT NULL, created INTEGER NOT NULL, remote TEXT, company TEXT, completed INTEGER NOT NULL DEFAULT 0, checked INTEGER NOT NULL DEFAULT 0)");
    }
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { throw new IllegalStateException("Unsupported database migration"); }
    void offer(String session, String text, boolean priority) {
        if (!TaskRules.candidate(text)) return;
        String id = UUID.nameUUIDFromBytes((session+"\n"+TaskRules.normalize(text)).getBytes(StandardCharsets.UTF_8)).toString();
        ContentValues v=new ContentValues();
        v.put("id",id);v.put("session",session);v.put("text",text);v.put("priority",priority?1:0);v.put("created",System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("tasks",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    static final class Row {
        String id,session,text,remote,company; boolean priority,completed; long created;
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
        r.completed=c.getInt(c.getColumnIndexOrThrow("completed"))!=0;r.created=c.getLong(c.getColumnIndexOrThrow("created"));return r;
    }
    void sent(String id,String remote,String company) {
        ContentValues v=new ContentValues();v.put("remote",remote);v.put("company",company);
        getWritableDatabase().update("tasks",v,"id=?",new String[]{id});
    }
    void checked(String id,boolean done) {
        ContentValues v=new ContentValues();v.put("completed",done?1:0);v.put("checked",System.currentTimeMillis());
        getWritableDatabase().update("tasks",v,"id=?",new String[]{id});
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
                w.write("Запись: "+r.session+"; "+(r.remote==null?"ожидает отправки":"YouGile ID: "+r.remote)+"\n\n");
            }
        }
        if(!tmp.renameTo(destination)) throw new IOException("Не удалось сохранить список задач");
    }
}
