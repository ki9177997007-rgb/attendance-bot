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
        super.onCreate(b);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout l=Ui.column(this);ScrollView scroll=new ScrollView(this);scroll.addView(l);setContentView(scroll);ScreenLayout.apply(this,l);Ui.title(l,"Подключить YouGile","Задачи из разговоров — на твоей доске");LinearLayout form=Ui.card(l);
        status=Ui.label(this,"Введи логин и пароль YouGile. Приложение само создаст отдельный проект и подключит очередь задач.",16);form.addView(status);
        login=new EditText(this);login.setHint("Логин YouGile");login.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);form.addView(login);
        password=new EditText(this);password.setHint("Пароль YouGile");password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);form.addView(password);password.setSaveEnabled(false);password.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        connect=Ui.button(form,"Войти и подключить",true,()->{});Ui.button(l,"Позже — работать офлайн",false,this::finish);
        LinearLayout note=Ui.card(l);note.addView(Ui.label(this,"Пароль не сохраняется. Передаются только поручения; аудио и полные разговоры остаются на телефоне.\n\nЕсли входишь через Яндекс ID, здесь нужен отдельный пароль YouGile. Пароль Яндекса не вводи.",14));
        connect.setOnClickListener(v->companies());
    }
    private void companies() {
        String user=login.getText().toString().trim(),pass=password.getText().toString();
        if(user.isEmpty()||pass.isEmpty()){status.setText("Введи логин и пароль YouGile.");return;}
        password.setText("");connect.setEnabled(false);status.setText("Проверяю вход в YouGile…");
        new Thread(()->{
            try {
                JSONObject credentials=new JSONObject().put("login",user).put("password",pass);
                JSONArray companies=new YouGileAuth(new YouGileApi(null)).companies(user,pass);
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
                try { key=new YouGileAuth(new YouGileApi(null)).createKey(credentials.getString("login"),credentials.getString("password"),companyId); }
                finally { credentials.remove("password"); }
                YouGileApi api=new YouGileApi(key);JSONObject who=api.call("GET","/api-v2/users/me",null);JSONObject config=api.setup().put("company",companyId).put("companyName",company.optString("name",company.optString("title",""))).put("accountName",who.optString("realName",who.optString("email","")));
                SecureConfig.save(this,config);SyncJob.schedule(this);SyncEngine.kick(this);
                runOnUiThread(()->{Toast.makeText(this,"YouGile подключён. Проект «Вторая память» готов.",Toast.LENGTH_LONG).show();finish();});
            }catch(Exception e){fail(e);}
        },"yougile-connect").start();
    }
    private void fail(Exception e) {
        runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){connect.setEnabled(true);status.setText("Подключение не завершено: "+e.getMessage()+". Записи на телефоне сохранены. Введи пароль ещё раз для повтора.");}});
    }
}
