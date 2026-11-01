package ru.secondmemory;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.os.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
public class BackgroundCaptureTest {
 private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
 private void shell(String cmd)throws Exception{try(ParcelFileDescriptor fd=inst.getUiAutomation().executeShellCommand(cmd);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] buf=new byte[512];while(in.read(buf)!=-1){}}}
 private List<File> wavs(Context c){List<File> out=new ArrayList<>();File[] dirs=new File(c.getFilesDir(),"sessions").listFiles(File::isDirectory);if(dirs!=null)for(File dir:dirs){File[] files=dir.listFiles((d,n)->n.startsWith("audio_")&&n.endsWith(".wav"));if(files!=null)out.addAll(Arrays.asList(files));}return out;}
 @Test public void quickStartKeepsWritingAudioWithScreenOff()throws Exception{Context c=inst.getTargetContext();inst.getUiAutomation().grantRuntimePermission(c.getPackageName(),Manifest.permission.RECORD_AUDIO);if(Build.VERSION.SDK_INT>=33)inst.getUiAutomation().grantRuntimePermission(c.getPackageName(),Manifest.permission.POST_NOTIFICATIONS);Set<File> before=new HashSet<>(wavs(c));Activity a=null;try{
  shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard");a=inst.startActivitySync(new Intent(c,MainActivity.class).setAction("ru.secondmemory.action.RECORD").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();File capture=null;long until=System.currentTimeMillis()+45000;while(System.currentTimeMillis()<until){for(File f:wavs(c))if(!before.contains(f)&&f.length()>16000)capture=f;if(capture!=null)break;Thread.sleep(200);}assertNotNull("Shortcut must start actual WAV capture: "+RecorderService.message,capture);long start=capture.length();shell("input keyevent KEYCODE_SLEEP");Thread.sleep(1500);assertTrue("Microphone foreground service must keep recording with screen off",capture.length()>start+16000);assertTrue(RecorderService.running);
 }finally{shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard");if(RecorderService.running)c.startService(new Intent(c,RecorderService.class).setAction("STOP"));long until=System.currentTimeMillis()+30000;while((RecorderService.running||RecorderService.stopping)&&System.currentTimeMillis()<until)Thread.sleep(100);if(a!=null){Activity close=a;inst.runOnMainSync(close::finish);}}assertFalse("Stop must finish and flush capture",RecorderService.running||RecorderService.stopping);}
}
