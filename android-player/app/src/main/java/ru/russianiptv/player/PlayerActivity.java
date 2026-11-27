package ru.russianiptv.player;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.*;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.util.concurrent.Executors;

public class PlayerActivity extends Activity {
    private ExoPlayer player;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String name = getIntent().getStringExtra("name"); String url = getIntent().getStringExtra("url");
        String id = getIntent().getStringExtra("id"); String epg = getIntent().getStringExtra("epg");
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xff101114);
        TextView title = new TextView(this); title.setText(name); title.setTextColor(0xffffffff); title.setTextSize(20); title.setPadding(16, 12, 16, 8); root.addView(title);
        PlayerView view = new PlayerView(this); view.setUseController(true); root.addView(view, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView program = new TextView(this); program.setTextColor(0xffeeeeee); program.setPadding(16, 8, 16, 8); root.addView(program);
        setContentView(root);
        player = new ExoPlayer.Builder(this).build(); view.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(url)); player.prepare(); player.play();
        if (epg != null && id != null && !id.isEmpty()) Executors.newSingleThreadExecutor().execute(() -> {
            String text = EpgRepository.nowAndNext(epg, id);
            runOnUiThread(() -> { if (!text.isEmpty()) program.setText(text); });
        });
    }
    @Override protected void onStop() { super.onStop(); if (player != null) { player.pause(); player.release(); player = null; } }
}
