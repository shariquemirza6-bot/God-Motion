package com.godmotion.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.VideoView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private VideoView videoView;
    private TimelineView timeline;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (videoView.isPlaying()) {
                timeline.setPlayheadMs(videoView.getCurrentPosition());
            }
            handler.postDelayed(this, 50);
        }
    };

    private final ActivityResultLauncher<String> picker =
        registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                videoView.setVideoURI(uri);
                videoView.start();
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        videoView = findViewById(R.id.videoView);
        timeline = findViewById(R.id.timeline);

        videoView.setOnPreparedListener(mp -> timeline.setDuration(mp.getDuration()));
        videoView.setOnClickListener(v -> {
            if (videoView.isPlaying()) videoView.pause(); else videoView.start();
        });
        timeline.setOnScrubListener(ms -> {
            videoView.pause();
            videoView.seekTo((int) ms);
        });

        findViewById(R.id.btnImport).setOnClickListener(v -> picker.launch("video/*"));
        findViewById(R.id.btnText).setOnClickListener(v -> timeline.addClip("Text", 0xFF22C55E));
        findViewById(R.id.btnShape).setOnClickListener(v -> timeline.addClip("Shape", 0xFFF59E0B));
        findViewById(R.id.btnSplit).setOnClickListener(v -> timeline.splitAtPlayhead());
        findViewById(R.id.btnDelete).setOnClickListener(v -> timeline.deleteSelected());

        handler.post(ticker);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }
}
