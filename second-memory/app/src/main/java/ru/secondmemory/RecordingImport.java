package ru.secondmemory;
import android.content.Context;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import java.io.*;
import java.util.UUID;

final class RecordingImport {
 static File copy(Context context,Uri uri)throws IOException{
  String name="";try(Cursor cursor=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);}
  if(name==null||!name.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(txt|wav)$"))throw new IOException("Выбери файлы TXT или WAV из меню записей версии 06");
  name=new File(name).getName();File dir=new File(context.getFilesDir(),"sessions/import-"+UUID.randomUUID());if(!dir.mkdirs())throw new IOException("Не удалось создать папку импорта");
  File result=new File(dir,name);boolean ok=false;
  try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(result)){
   if(in==null)throw new IOException("Нет доступа к выбранному файлу");byte[] buffer=new byte[65536];int n;long total=0;
   while((n=in.read(buffer))!=-1){total+=n;if(total>2L*1024*1024*1024)throw new IOException("Файл больше 2 ГБ");out.write(buffer,0,n);}ok=true;
  }finally{if(!ok){result.delete();dir.delete();}}
  return result;
 }
}
