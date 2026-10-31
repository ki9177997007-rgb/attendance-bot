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
    public static volatile String message = "Нажми ВКЛ. При первом запуске говори один: приложение запомнит твой голос. Для подключения и записей удерживай кнопку.";
    private volatile boolean cancelled;
    private Thread worker;
    private final Handler main = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wake;
    private final SpeakerProfile enrollment = new SpeakerProfile();
    private double[] owner;
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
        cancelled = false;
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
            message = "Подготавливаю офлайн-модели… При первом запуске это займёт некоторое время.";
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
            File speakerPath = ModelAssets.unpack(this, "speaker-0.4");
            if (cancelled) return;
            String stored = getSharedPreferences("voice", MODE_PRIVATE).getString("embedding", null);
            if (stored != null) owner = vector(new JSONArray(stored));
            sessionId = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
            session = new File(getFilesDir(), "sessions/" + sessionId);
            if (!session.mkdirs()) throw new IOException("Не удалось создать запись");
            try (Model model = new Model(modelPath.getAbsolutePath());
                 SpeakerModel speaker = new SpeakerModel(speakerPath.getAbsolutePath());
                 Recognizer recognizer = new Recognizer(model, 16000, speaker)) {
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
                message = prompt();
                byte[] buffer = new byte[6400];
                while (!cancelled) {
                    int length = microphone.read(buffer, 0, buffer.length);
                    if (length < 0) throw new IOException("Ошибка микрофона: " + length);
                    if (length == 0) continue;
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastSync > 60000) { lastSync = now; SyncEngine.kick(this); }
                    wav.write(buffer, length);
                    if (recognizer.acceptWaveForm(buffer, length)) result(recognizer.getResult());
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
            if (!failed) message = "Выключено. Запись и текст сохранены на телефоне." +
                (owner == null ? " Обучение голосу ещё не закончено." : " Образец голоса сохранён.") + exported;
            main.post(() -> { worker = null; running = false; stopping = false; AiJob.schedule(this); ReminderReceiver.restore(this); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); });
        }
    }
    private WavWriter nextWav() throws IOException {
        return new WavWriter(new File(session, String.format(Locale.US, "audio_%03d.wav", ++chunks)));
    }
    private String prompt() {
        return owner == null ? "Запоминаю твой голос: " + enrollment.count() + "/3. Говори один. Произнеси три фразы по 5–10 секунд с паузами между ними."
            : "Включено. Речь сохраняется офлайн. Похожие на твой голос реплики — приоритетные. Определение голоса может ошибаться.";
    }
    private static double[] vector(JSONArray values) throws JSONException {
        double[] vector = new double[values.length()];
        for (int i = 0; i < vector.length; i++) vector[i] = values.getDouble(i);
        return vector;
    }
    private void result(String raw) throws Exception {
        JSONObject data = new JSONObject(raw);
        String text = data.optString("text", "").trim();
        if (text.isEmpty()) return;
        double[] voice = data.has("spk") ? vector(data.getJSONArray("spk")) : null;
        int frames = data.optInt("spk_frames", 0);
        String label;
        if (owner == null) {
            label = "ОБУЧЕНИЕ";
            enrollment.add(voice, frames);
            if (enrollment.count() >= 3) {
                owner = enrollment.vector();
                JSONArray saved = new JSONArray();
                for (double value : owner) saved.put(value);
                if (!getSharedPreferences("voice", MODE_PRIVATE).edit().putString("embedding", saved.toString()).commit())
                    throw new IOException("Не удалось сохранить образец голоса");
            }
        } else {
            label = SpeakerProfile.probableOwner(owner, voice, frames) ? "ПРИОРИТЕТ: ПОХОЖ НА ТВОЙ ГОЛОС" : "ГОЛОС НЕ ПОДТВЕРЖДЁН";
        }
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        data.put("recorded_at",System.currentTimeMillis());
        data.put("label", label).put("saved_at", timestamp).put("priority", label.startsWith("ПРИОРИТЕТ"));
        append(new File(session, "transcript.jsonl"), data.toString() + "\n");
        String line = "[" + timestamp + "] [" + label + "] " + text + "\n";
        append(new File(session, "transcript.txt"), line);
        if (label.startsWith("ПРИОРИТЕТ")) append(new File(session, "priority.txt"), line);
        if (!"ОБУЧЕНИЕ".equals(label)) {
            try (TaskDb db = new TaskDb(this)) { db.offer(sessionId, text, label.startsWith("ПРИОРИТЕТ")); ReminderReceiver.restore(this); }
            catch (RuntimeException e) { message = "Текст сохранён, но очередь задач недоступна: " + e.getMessage(); return; }
        }
        message = prompt();
    }
    private void append(File file, String text) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file, true)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
    }
    @Override public void onDestroy() {
        requestStop();
        if (wake != null && wake.isHeld()) wake.release();
        if (worker == null) { running = false; stopping = false; }
        super.onDestroy();
    }
}
