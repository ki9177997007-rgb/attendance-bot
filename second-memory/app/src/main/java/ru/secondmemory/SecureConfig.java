package ru.secondmemory;
import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import org.json.JSONObject;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;

final class SecureConfig {
    private static final String ALIAS="second-memory-yougile";
    private static javax.crypto.SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)) {
            KeyGenerator generator=KeyGenerator.getInstance("AES","AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (javax.crypto.SecretKey)store.getKey(ALIAS,null);
    }
    static void save(Context c,JSONObject config) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        String iv=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP);
        String body=Base64.encodeToString(cipher.doFinal(config.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        if(!c.getSharedPreferences("connection",0).edit().putString("iv",iv).putString("body",body).commit()) throw new java.io.IOException("Не удалось сохранить подключение");
    }
    static JSONObject load(Context c) throws Exception {
        android.content.SharedPreferences p=c.getSharedPreferences("connection",0);
        String body=p.getString("body",null);if(body==null)return null;
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p.getString("iv",""),Base64.NO_WRAP)));
        return new JSONObject(new String(cipher.doFinal(Base64.decode(body,Base64.NO_WRAP)),StandardCharsets.UTF_8));
    }
    static boolean configured(Context c) { return c.getSharedPreferences("connection",0).contains("body"); }
}
