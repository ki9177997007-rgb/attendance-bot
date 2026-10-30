package ru.secondmemory;
import android.app.*;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import org.json.*;

/** Credentials go directly to YouGile over HTTPS. Password is never persisted. */
public class ConnectActivity extends Activity {
    private EditText login,password;
    private Button connect;
    private TextView status;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(36,48,36,36);
        status=new TextView(this);status.setText("Подключение YouGile\n\nВход напрямую в YouGile, без Supabase. Будет создан отдельный проект «Вторая память». Передаются только черновики поручений; аудио и полная расшифровка остаются на телефоне. Пароль не сохраняется.");l.addView(status);
        login=new EditText(this);login.setHint("Логин YouGile");login.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);l.addView(login);
        password=new EditText(this);password.setHint("Пароль YouGile");password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);l.addView(password);
        connect=new Button(this);connect.setText("Войти и подключить");l.addView(connect);
        Button later=new Button(this);later.setText("Позже — работать офлайн");later.setOnClickListener(v->finish());l.addView(later);
        ScrollView scroll=new ScrollView(this);scroll.addView(l);setContentView(scroll);
        connect.setOnClickListener(v->companies());
    }
    private void companies() {
        String user=login.getText().toString().trim(),pass=password.getText().toString();
        if(user.isEmpty()||pass.isEmpty()){status.setText("Введи логин и пароль YouGile.");return;}
        password.setText("");connect.setEnabled(false);status.setText("Проверяю вход в YouGile…");
        new Thread(()->{
            try {
                JSONObject credentials=new JSONObject().put("login",user).put("password",pass);
                JSONArray companies=new YouGileApi(null).call("POST","/api-v2/auth/companies?limit=1000",credentials).getJSONArray("content");
                if(companies.length()==0)throw new Exception("Компании не найдены");
                runOnUiThread(()->choose(companies,credentials));
            } catch(Exception e){fail(e);}
        },"yougile-login").start();
    }
    private void choose(JSONArray companies,JSONObject credentials) {
        if(isFinishing()||isDestroyed())return;
        try {
            if(companies.length()==1){connect(companies.getJSONObject(0),credentials);return;}
            String[] titles=new String[companies.length()];
            for(int i=0;i<titles.length;i++) titles[i]=companies.getJSONObject(i).optString("name",companies.getJSONObject(i).optString("title","Компания"));
            new AlertDialog.Builder(this).setTitle("Выбери компанию YouGile").setItems(titles,(d,i)->{
                try{connect(companies.getJSONObject(i),credentials);}catch(Exception e){fail(e);}
            }).setOnCancelListener(d->{credentials.remove("password");connect.setEnabled(true);}).show();
        }catch(Exception e){fail(e);}
    }
    private void connect(JSONObject company,JSONObject credentials) {
        status.setText("Создаю отдельный проект и подключаю очередь…");
        new Thread(()->{
            try {
                String companyId=company.getString("id");
                credentials.put("companyId",companyId);
                String key;
                try { key=new YouGileApi(null).call("POST","/api-v2/auth/keys",credentials).getString("key"); }
                finally { credentials.remove("password"); }
                JSONObject config=new YouGileApi(key).setup().put("company",companyId);
                SecureConfig.save(this,config);SyncJob.schedule(this);SyncEngine.kick(this);
                runOnUiThread(()->{Toast.makeText(this,"YouGile подключён. Проект «Вторая память» готов.",Toast.LENGTH_LONG).show();finish();});
            }catch(Exception e){fail(e);}
        },"yougile-connect").start();
    }
    private void fail(Exception e) {
        runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){connect.setEnabled(true);status.setText("Подключение не завершено: "+e.getMessage()+". Записи на телефоне сохранены. Введи пароль ещё раз для повтора.");}});
    }
}
