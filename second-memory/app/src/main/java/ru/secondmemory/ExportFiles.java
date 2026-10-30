package ru.secondmemory;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.*;

final class ExportFiles {
    static String session(Context c,File session)throws IOException {
        if(Build.VERSION.SDK_INT<29)return "Записи доступны через файловый выбор → Вторая память.";
        for(String name:new String[]{"transcript.txt","priority.txt"}) {
            File file=new File(session,name);if(!file.isFile())continue;
            ContentValues values=new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME,session.getName()+"_"+name);
            values.put(MediaStore.Downloads.MIME_TYPE,"text/plain");
            values.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/VtorayaPamyat");
            values.put(MediaStore.Downloads.IS_PENDING,1);
            Uri uri=c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
            if(uri==null)throw new IOException("Не удалось создать файл в Загрузках");
            try {
                try(InputStream in=new FileInputStream(file);OutputStream out=c.getContentResolver().openOutputStream(uri)) {
                    if(out==null)throw new IOException("Не удалось открыть файл в Загрузках");
                    byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                }
                values.clear();values.put(MediaStore.Downloads.IS_PENDING,0);c.getContentResolver().update(uri,values,null,null);
            }catch(IOException e){c.getContentResolver().delete(uri,null,null);throw e;}
        }
        return "Текст: Загрузки → VtorayaPamyat.";
    }
}
