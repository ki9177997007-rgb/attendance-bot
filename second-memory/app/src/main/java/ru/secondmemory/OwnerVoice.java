package ru.secondmemory;
import android.content.*;
import com.k2fsa.sherpa.onnx.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import org.json.*;
/** Local speaker similarity, never used as identity authentication. */
final class OwnerVoice implements AutoCloseable {
 private final SpeakerEmbeddingExtractor extractor;
 static final String HASH="e9848563da86f263117134dfd7ad63c92355b37de492b55e325400c9d9c39012";
 OwnerVoice(Context c)throws Exception{File f=new File(c.getFilesDir(),"speaker.onnx");if(!f.isFile()||f.length()!=26530550){File tmp=new File(f+".tmp");MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=c.getAssets().open("speech/speaker.onnx");FileOutputStream out=new FileOutputStream(tmp)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){out.write(b,0,n);md.update(b,0,n);}out.getFD().sync();}StringBuilder hex=new StringBuilder();for(byte b:md.digest())hex.append(String.format("%02x",b));if(!HASH.equals(hex.toString())){tmp.delete();throw new IOException("Повреждена модель голоса");}if(!tmp.renameTo(f))throw new IOException("Не удалось сохранить модель голоса");}extractor=new SpeakerEmbeddingExtractor(null,new SpeakerEmbeddingExtractorConfig(f.getAbsolutePath(),1,false,"cpu"));}
 float[] embedding(float[] pcm)throws IOException{if(pcm.length<16000)throw new IOException("Слишком короткая речь: скажи полное предложение");double energy=0;for(float x:pcm)energy+=x*x;if(Math.sqrt(energy/pcm.length)<.003)throw new IOException("Слишком тихая речь");OnlineStream s=extractor.createStream();try{s.acceptWaveform(pcm,16000);s.inputFinished();if(!extractor.isReady(s))throw new IOException("Не удалось выделить голос");return extractor.compute(s);}finally{s.release();}}
 static boolean enrolled(Context c){return !c.getSharedPreferences("voice",0).getString("profile","").isEmpty();}
 static float[] load(Context c)throws Exception{JSONArray a=new JSONArray(c.getSharedPreferences("voice",0).getString("profile","[]"));float[] v=new float[a.length()];for(int i=0;i<v.length;i++)v[i]=(float)a.getDouble(i);return v;}
 static double cosine(float[] a,float[] b){if(a.length==0||a.length!=b.length)return -1;double dot=0,aa=0,bb=0;for(int i=0;i<a.length;i++){dot+=a[i]*b[i];aa+=a[i]*a[i];bb+=b[i]*b[i];}return aa>0&&bb>0?dot/Math.sqrt(aa*bb):-1;}
 boolean accepts(Context c,float[] pcm)throws Exception{double score=cosine(load(c),embedding(pcm));c.getSharedPreferences("voice",0).edit().putFloat("last_similarity",(float)score).apply();return score>=.65;}
 static void save(Context c,float[] v)throws Exception{JSONArray a=new JSONArray();for(float f:v)a.put((double)f);if(!c.getSharedPreferences("voice",0).edit().putString("profile",a.toString()).putBoolean("enabled",true).commit())throw new IOException("Не удалось сохранить образец");}
 public void close(){extractor.release();}
}
