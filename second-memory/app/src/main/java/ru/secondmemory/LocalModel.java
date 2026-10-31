package ru.secondmemory;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import java.io.*;
import java.security.*;

final class LocalModel {
 static final long SIZE=1117320736L;
 static final String SHA="6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e";
 static final String URL="https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/91cad51170dc346986eccefdc2dd33a9da36ead9/qwen2.5-1.5b-instruct-q4_k_m.gguf";
 static File file(Context c){return new File(c.getExternalFilesDir(null),"qwen-1.5b-q4.gguf");}
 static boolean ready(Context c){return file(c).length()==SIZE&&c.getSharedPreferences("ai",0).getBoolean("verified",false);}
 static String status(Context c){
  String importing=c.getSharedPreferences("ai",0).getString("import_uri","");if(!importing.isEmpty())return "Модель из файла: ожидаю копирование и проверку после остановки записи";
  if(ready(c))return "Локальная нейросеть готова";
  long id=c.getSharedPreferences("ai",0).getLong("download",-1);
  if(id==-1)return "Нейросеть не загружена (1,12 ГБ)";
  try(Cursor q=c.getSystemService(DownloadManager.class).query(new DownloadManager.Query().setFilterById(id))){
   if(q!=null&&q.moveToFirst()){
    int state=q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
    if(state==DownloadManager.STATUS_FAILED)return DownloadMessages.error(q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)));
    if(state==DownloadManager.STATUS_PAUSED)return DownloadMessages.paused(q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)));
    if(state==DownloadManager.STATUS_SUCCESSFUL)return file(c).length()==SIZE?"Модель загружена. Нажми «Проверить модель и запустить разбор».":"Файл неполный или не той модели. Нажми «Скачать / повторить загрузку».";
    long n=q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
    return "Загрузка нейросети: "+(n*100/SIZE)+"%";
   }
  }catch(RuntimeException e){return "Системный загрузчик недоступен. Используй скачивание через браузер.";}
  return "Нейросеть не готова — открой её меню";
 }
 static synchronized void download(Context c,boolean wifi)throws IOException{
  if(AiJob.isActive())throw new IOException("Дождись завершения проверки или разбора модели");
  if(!c.getSharedPreferences("ai",0).getString("import_uri","").isEmpty())throw new IOException("Уже выбран файл модели; сначала дождись его проверки");
  if(ready(c)){AiJob.schedule(c);return;}
  DownloadManager dm=c.getSystemService(DownloadManager.class);long old=c.getSharedPreferences("ai",0).getLong("download",-1);
  if(old!=-1)dm.remove(old);
  File f=file(c);if(f.exists()&&!f.delete())throw new IOException("Не удалось заменить незавершённую модель");
  if(f.getParentFile().getUsableSpace()<SIZE+300000000L)throw new IOException("Нужно не меньше 1,5 ГБ свободного места");
  DownloadManager.Request req=new DownloadManager.Request(Uri.parse(URL+"?download=true&request="+System.currentTimeMillis())).addRequestHeader("User-Agent","SecondMemory/0.6 Android").setTitle("Вторая память: локальная нейросеть").setDescription("После загрузки работает без интернета и платного API")
   .setDestinationInExternalFilesDir(c,null,f.getName()).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setAllowedOverMetered(!wifi).setAllowedOverRoaming(false);
  if(wifi)req.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
  long id=dm.enqueue(req);c.getSharedPreferences("ai",0).edit().putLong("download",id).putBoolean("verified",false).remove("last_error").apply();
 }
 static void importPending(Context c)throws Exception{
  String selected=c.getSharedPreferences("ai",0).getString("import_uri","");if(selected.isEmpty())return;
  Uri uri=Uri.parse(selected);File temp=new File(c.getExternalFilesDir(null),"model-import.partial");
  try{
   if(temp.getParentFile().getUsableSpace()<SIZE+100000000L)throw new IOException("Для копирования модели нужно 1,3 ГБ свободного места");
   MessageDigest md=MessageDigest.getInstance("SHA-256");long total=0;
   try(InputStream in=c.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){
    if(in==null)throw new IOException("Выбранный файл недоступен");byte[] b=new byte[1024*1024];int n;
    while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();total+=n;if(total>SIZE)throw new IOException("Это другая модель. Выбери qwen2.5-1.5b-instruct-q4_k_m.gguf");out.write(b,0,n);md.update(b,0,n);AiJob.status="Копирование модели: "+(total*100/SIZE)+"%";}
   }
   StringBuilder digest=new StringBuilder();for(byte b:md.digest())digest.append(String.format(java.util.Locale.US,"%02x",b&255));
   if(total!=SIZE||!SHA.equals(digest.toString()))throw new IOException("Выбранный файл повреждён или относится к другой модели. Скачай его кнопкой «Скачать модель через браузер».");
   long id=c.getSharedPreferences("ai",0).getLong("download",-1);if(id!=-1)c.getSystemService(DownloadManager.class).remove(id);
   if(!temp.renameTo(file(c)))throw new IOException("Не удалось сохранить проверенную модель");
   c.getSharedPreferences("ai",0).edit().remove("import_uri").remove("download").remove("last_error").putBoolean("verified",true).commit();
   c.getContentResolver().releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
  }catch(InterruptedIOException e){throw e;}catch(Exception e){c.getSharedPreferences("ai",0).edit().remove("import_uri").apply();try{c.getContentResolver().releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}throw e;}finally{temp.delete();}
 }
 static boolean verify(Context c)throws Exception{
  if(ready(c))return true;
  File f=file(c);if(f.length()!=SIZE){
   long id=c.getSharedPreferences("ai",0).getLong("download",-1);
   try(Cursor q=c.getSystemService(DownloadManager.class).query(new DownloadManager.Query().setFilterById(id))){if(q!=null&&q.moveToFirst()&&q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))==DownloadManager.STATUS_SUCCESSFUL)throw new IOException("Получен неполный файл модели. Повтори загрузку или выбери файл из браузера.");}
   return false;
  }
  MessageDigest md=MessageDigest.getInstance("SHA-256");
  try(InputStream in=new FileInputStream(f)){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();md.update(b,0,n);}}
  StringBuilder hex=new StringBuilder();for(byte b:md.digest())hex.append(String.format(java.util.Locale.US,"%02x",b&255));
  if(!SHA.equals(hex.toString()))throw new IOException("Модель повреждена. В меню нейросети повтори загрузку.");
  c.getSharedPreferences("ai",0).edit().putBoolean("verified",true).commit();return true;
 }
}
