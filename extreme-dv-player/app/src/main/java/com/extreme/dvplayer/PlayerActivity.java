package com.extreme.dvplayer;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@UnstableApi
public class PlayerActivity extends AppCompatActivity {
    private ExoPlayer player;
    private PlayerView playerView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);
        getWindow().setNavigationBarColor(android.graphics.Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setBackgroundColor(android.graphics.Color.BLACK);
        playerView.setUseController(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        setContentView(playerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        hideSystemUi();
        playerView.setOnClickListener(v -> hideSystemUi());
    }

    @Override
    protected void onStart() {
        super.onStart();
        initializePlayer();
    }

    private void initializePlayer() {
        if (player != null) return;
        Uri uri = getIntent().getData();
        if (uri == null) {
            finish();
            return;
        }

        int mode = getIntent().getIntExtra("mode", 0);
        MediaCodecSelector selector = mode == 3 ? MediaCodecSelector.DEFAULT : dolbyFirstSelector();

        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                .setMediaCodecSelector(selector)
                .setEnableDecoderFallback(true);

        player = new ExoPlayer.Builder(this, renderersFactory).build();
        playerView.setPlayer(player);

        MediaItem item = new MediaItem.Builder().setUri(uri).build();
        player.setMediaItem(item);
        player.prepare();
        player.play();
    }

    private MediaCodecSelector dolbyFirstSelector() {
        return (mimeType, requiresSecureDecoder, requiresTunnelingDecoder) -> {
            List<MediaCodecInfo> original = MediaCodecSelector.DEFAULT.getDecoderInfos(
                    mimeType, requiresSecureDecoder, requiresTunnelingDecoder);
            if (!MimeTypes.VIDEO_DOLBY_VISION.equals(mimeType) || original.size() < 2) {
                return original;
            }
            ArrayList<MediaCodecInfo> sorted = new ArrayList<>(original);
            sorted.sort(Comparator.comparingInt(this::dolbyCodecRank));
            return sorted;
        };
    }

    private int dolbyCodecRank(MediaCodecInfo info) {
        String n = info.name.toLowerCase(Locale.ROOT);
        if (n.startsWith("c2.dolby.decoder.hevc")) return 0;
        if (n.contains("dolby")) return 1;
        if (n.contains("qti") && n.contains("dv")) return 2;
        return 10;
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    protected void onStop() {
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
        super.onStop();
    }
}
