package ru.secondmemory;

import android.content.Context;
import java.io.*;
import java.util.zip.*;

final class ModelAssets {
    static File unpack(Context context, String name) throws IOException {
        File root = new File(context.getFilesDir(), "models/" + name);
        File ready = new File(root, ".ready");
        if (ready.isFile()) return root;
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Не удалось создать каталог модели");
        String prefix = root.getCanonicalPath() + File.separator;
        try (ZipInputStream zip = new ZipInputStream(context.getAssets().open(name + ".zip"))) {
            ZipEntry entry;
            byte[] buffer = new byte[65536];
            while ((entry = zip.getNextEntry()) != null) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                String path = entry.getName();
                int slash = path.indexOf('/');
                if (slash < 0 || slash == path.length()-1) continue;
                File target = new File(root, path.substring(slash+1));
                if (!target.getCanonicalPath().startsWith(prefix)) throw new IOException("Недопустимый путь модели");
                if (entry.isDirectory()) { target.mkdirs(); continue; }
                File parent = target.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Нет места для модели");
                try (OutputStream out = new FileOutputStream(target)) {
                    int n;
                    while ((n = zip.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                        out.write(buffer, 0, n);
                    }
                }
            }
        }
        if (!ready.createNewFile()) throw new IOException("Не удалось завершить установку модели");
        return root;
    }
}
