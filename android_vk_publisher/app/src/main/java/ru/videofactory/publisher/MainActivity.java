package ru.videofactory.publisher;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int PICK_VIDEO = 100;
    private Uri pendingVideo;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 48, 32, 32);
        TextView status = new TextView(this);
        status.setText("VK Publisher • прототип\nСообщество: Подарочный переполох (242557906)\nАвтопубликация: выключена\nВыбери MP4 для безопасного теста.");
        layout.addView(status);
        Button choose = new Button(this);
        choose.setText("Выбрать MP4");
        choose.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("video/mp4");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, PICK_VIDEO);
        });
        layout.addView(choose);
        Button share = new Button(this);
        share.setText("Открыть системное меню публикации");
        share.setEnabled(false);
        share.setOnClickListener(v -> {
            if (pendingVideo == null) return;
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("video/mp4");
            send.putExtra(Intent.EXTRA_STREAM, pendingVideo);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Выбери VK Видео"));
        });
        layout.addView(share);
        setContentView(layout);
        this.shareButton = share;
    }
    private Button shareButton;
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_VIDEO && resultCode == RESULT_OK && data != null) {
            pendingVideo = data.getData();
            shareButton.setEnabled(pendingVideo != null);
        }
    }
}
