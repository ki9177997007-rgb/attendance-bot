package ru.secondmemory;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import org.json.*;
import org.vosk.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class RecorderService extends Service {
    public static volatile boolean running, stopping;
    public static volatile String message = "Готово к записи. Нажми ВКЛ.";
    public static volatile String transcript="",partial="";
    public static volatile int phraseCount,micLevel;
    private volatile boolean cancelled;
    private Thread worker;
    private final Handler main = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wake;
    private File session;
    private String sessionId;
    private int chunks;
    private long lastSync;
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if ("STOP".equals(intent.getAction())) {
            requestStop();
            if (worker == null) stopSelf();
            return START_NOT_STICKY;
        }
        if (running || stopping) return START_NOT_STICKY;
        cancelled = false;partial="";phraseCount=0;micLevel=0;chunks=0;session=null;
        transcript=getSharedPreferences("display",MODE_PRIVATE).getString("recent","");
        running = true;
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel("memory", "Вторая память", NotificationManager.IMPORTANCE_LOW));
            PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, RecorderService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            startForeground(1, new Notification.Builder(this, "memory")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Вторая память включена")
                .setContentText("Микрофон активен при распознавании. Нажми ВЫКЛ для остановки.")
                .setContentIntent(open).setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "ВЫКЛ", stop).build());
            wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SecondMemory:recognition");
            wake.acquire();
            message = "Открываю распознавание речи… При первом запуске распаковывается встроенная модель.";
            worker = new Thread(this::capture, "memory-capture");
            worker.start();
        } catch (Exception e) {
            message = "Не удалось запустить: " + e.getMessage();
            running = false;
            stopSelf();
        }
        return START_NOT_STICKY;
    }
    private void requestStop() {
        cancelled = true;
        if (worker != null) { stopping = true; message = "Сохраняю запись…"; worker.interrupt(); }
    }
    private void capture() {
        AudioRecord microphone = null;
        WavWriter wav = null;
        boolean failed = false;
        try {
            File modelPath = ModelAssets.unpack(this, "ru-small-0.22");
            if (cancelled) return;
            sessionId = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
            session = new File(getFilesDir(), "sessions/" + sessionId);
            if (!session.mkdirs()) throw new IOException("Не удалось создать запись");
            try (Model model = new Model(modelPath.getAbsolutePath());
                 Recognizer recognizer = new Recognizer(model, 16000)) {
                recognizer.setWords(true);
                int minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (minimum <= 0) throw new IOException("Микрофон не поддерживает запись 16 кГц");
                if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) throw new IOException("Нет разрешения на микрофон");
                microphone = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(32000, minimum * 2));
                if (microphone.getState() != AudioRecord.STATE_INITIALIZED) throw new IOException("Микрофон недоступен");
                if (cancelled) return;
                microphone.startRecording();
                if (microphone.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IOException("Не удалось включить микрофон");
                wav = nextWav();
                message = "Запись включена. Распознаю все голоса.";
                byte[] buffer = new byte[6400];
                long lastPartial=0;
                while (!cancelled) {
                    int length = microphone.read(buffer, 0, buffer.length);
                    if (length < 0) throw new IOException("Ошибка микрофона: " + length);
                    if (length == 0) continue;
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastSync > 60000) { lastSync = now; SyncEngine.kick(this); }
                    wav.write(buffer, length);
                    long energy=0;for(int i=0;i+1<length;i+=2){int sample=(short)((buffer[i]&255)|(buffer[i+1]<<8));energy+=(long)sample*sample;}micLevel=Math.min(100,(int)(Math.sqrt(energy/(double)Math.max(1,length/2))/100));
                    if (recognizer.acceptWaveForm(buffer, length)) result(recognizer.getResult());
                    else if(now-lastPartial>=500){partial=new JSONObject(recognizer.getPartialResult()).optString("partial","");lastPartial=now;}
                    if (wav.size() >= 16000 * 2 * 300) { wav.close(); wav = nextWav(); }
                }
                result(recognizer.getFinalResult());
            }
        } catch (Exception e) {
            if (!(cancelled && e instanceof InterruptedIOException)) {
                failed = true;
                message = "Ошибка: " + e.getMessage() + ". Сохранённые фрагменты оставлены на телефоне.";
            }
        } finally {
            if (microphone != null) {
                try { microphone.stop(); } catch (RuntimeException ignored) { }
                microphone.release();
            }
            if (wav != null) try { wav.close(); } catch (IOException e) {
                failed = true; message = "Не удалось завершить аудиофайл: " + e.getMessage();
            }
            String exported = "";
            if (session != null) {
                try { exported = " " + ExportFiles.session(this, session); }
                catch (IOException e) { exported = " Копия в Загрузках не создана; оригиналы доступны через меню записей."; }
            }
            SyncEngine.kick(this);
            partial="";micLevel=0;
            if (!failed) message = "Выключено. Сохранено фраз: "+phraseCount+". Запись сохранена на телефоне."+exported;
            main.post(() -> { worker = null; running = false; stopping = false; AiJob.schedule(this); ReminderReceiver.restore(this); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); });
        }
    }
    private WavWriter nextWav() throws IOException {
        return new WavWriter(new File(session, String.format(Locale.US, "audio_%03d.wav", ++chunks)));
    }
    private void result(String raw) throws Exception {
        partial="";String line=TranscriptStore.save(this,session,sessionId,new JSONObject(raw));
        if(!line.isEmpty()){phraseCount++;transcript=getSharedPreferences("display",MODE_PRIVATE).getString("recent","");message="Запись включена. Распознанный текст сохраняется.";}
    }
    @Override public void onDestroy() {
        requestStop();
        if (wake != null && wake.isHeld()) wake.release();
        if (worker == null) { running = false; stopping = false; }
        super.onDestroy();
    }
}
