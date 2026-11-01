package ru.secondmemory;

import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.net.URI;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Separate from YouGile credentials. No URL or token is bundled in the APK. */
final class ArchiveConfig {
 static final String PREF="archive_connection", ALIAS="second-memory-archive";
 private static javax.crypto.SecretKey key()throws Exception{
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
  if(!ks.containsAlias(ALIAS)){KeyGenerator gen=KeyGenerator.getInstance("AES","AndroidKeyStore");gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();}
  return (javax.crypto.SecretKey)ks.getKey(ALIAS,null);
 }
 static void validate(String endpoint,String token)throws Exception{
  URI u=new URI(endpoint);
  if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getFragment()!=null||u.getQuery()!=null||!u.getPath().endsWith("/sync"))throw new IllegalArgumentException("Нужен HTTPS-адрес приёмника, заканчивающийся на /sync");
  if(token.length()<32||token.length()>256||!token.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("Неверный ключ подключения");
 }
 static void save(Context c,String endpoint,String token)throws Exception{
  validate(endpoint,token);JSONObject data=new JSONObject().put("endpoint",endpoint).put("token",token);
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
  boolean ok=c.getSharedPreferences(PREF,0).edit().putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).putString("body",Base64.encodeToString(cipher.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP)).commit();
  if(!ok)throw new java.io.IOException("Не удалось сохранить подключение");
 }
 static JSONObject load(Context c)throws Exception{
  android.content.SharedPreferences p=c.getSharedPreferences(PREF,0);String body=p.getString("body",null);if(body==null)return null;
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p.getString("iv",""),Base64.NO_WRAP)));
  JSONObject data=new JSONObject(new String(cipher.doFinal(Base64.decode(body,Base64.NO_WRAP)),StandardCharsets.UTF_8));validate(data.getString("endpoint"),data.getString("token"));return data;
 }
 static boolean configured(Context c){return c.getSharedPreferences(PREF,0).contains("body");}
 static void clear(Context c){c.getSharedPreferences(PREF,0).edit().clear().commit();}
}
