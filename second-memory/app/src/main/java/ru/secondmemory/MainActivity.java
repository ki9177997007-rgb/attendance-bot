package ru.secondmemory;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.provider.DocumentsContract;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.Gravity;
import android.widget.*;

/** The service, not the screen, owns the microphone and session. */
public class MainActivity extends Activity {
    private Button toggle;
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        public void run() {
            toggle.setText(RecorderService.running ? "ВЫКЛ" : "ВКЛ");
            toggle.setEnabled(!RecorderService.stopping);
            status.setText(RecorderService.message + "\n\n" + SyncEngine.status + "\nУдерживай кнопку: подключение и записи.");
            handler.postDelayed(this, 500);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        int padding = (int)(24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        toggle = new Button(this);
        toggle.setTextSize(32);
        layout.addView(toggle, new LinearLayout.LayoutParams(-1, padding * 5));
        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, padding, 0, 0);
        status.setTextSize(16);
        layout.addView(status);
        setContentView(layout);
        toggle.setOnClickListener(v -> {
            if (RecorderService.running) {
                Intent stop = new Intent(this, RecorderService.class).setAction("STOP");
                startService(stop);
            } else requestStart();
        });
        toggle.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setTitle("Вторая память")
                .setItems(new String[]{"Подключить YouGile", "Открыть / поделиться записью", "Синхронизировать поручения", "Заново запомнить мой голос"}, (dialog, which) -> {
                    if (which == 0) startActivity(new Intent(this, ConnectActivity.class));
                    else if (which == 1) {
                        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
                        pick.putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri(RecordingsProvider.AUTHORITY, "root"));
                        startActivityForResult(pick, 10);
                    } else if (which == 2) SyncEngine.kick(this);
                    else if (RecorderService.running || RecorderService.stopping) Toast.makeText(this,"Сначала выключи запись",Toast.LENGTH_SHORT).show();
                    else new AlertDialog.Builder(this).setMessage("Сбросить только образец голоса? Записи сохранятся.")
                        .setPositiveButton("Сбросить",(d,w)->{
                            getSharedPreferences("voice",MODE_PRIVATE).edit().clear().apply();
                            RecorderService.message="Образец сброшен. Нажми ВКЛ и говори один.";
                        }).setNegativeButton("Отмена",null).show();
                }).show();
            return true;
        });
        SyncJob.schedule(this);
        SyncEngine.kick(this);
        if (!getPreferences(MODE_PRIVATE).getBoolean("setupShown",false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("setupShown",true).apply();
            startActivity(new Intent(this,ConnectActivity.class));
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==10 && result==RESULT_OK && data!=null && data.getData()!=null) {
            Intent share=new Intent(Intent.ACTION_SEND).setType(getContentResolver().getType(data.getData()));
            share.putExtra(Intent.EXTRA_STREAM,data.getData());share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setClipData(android.content.ClipData.newRawUri("Запись",data.getData()));
            startActivity(Intent.createChooser(share,"Открыть или передать запись"));
        }
    }

    private void requestStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !getPreferences(MODE_PRIVATE).getBoolean("notificationAsked", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notificationAsked", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
            return;
        }
        try { startForegroundService(new Intent(this, RecorderService.class).setAction("START")); }
        catch (RuntimeException e) { RecorderService.message = "Не удалось включить: " + e.getMessage(); }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 2 || (request == 1 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)) requestStart();
        else RecorderService.message = "Для работы разреши доступ к микрофону.";
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
