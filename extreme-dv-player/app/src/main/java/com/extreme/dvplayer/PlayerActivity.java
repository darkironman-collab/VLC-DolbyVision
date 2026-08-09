package com.extreme.dvplayer;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@UnstableApi
public class PlayerActivity extends AppCompatActivity {
    private ExoPlayer player;
    private PlayerView playerView;
    private FrameLayout root;
    private TextView infoView;
    private TextView gestureView;
    private Button resizeButton;

    private Uri mediaUri;
    private Uri subtitleUri;
    private String subtitleMimeType;
    private long resumePositionMs;

    private AudioManager audioManager;
    private float downX;
    private float downY;
    private float startBrightness;
    private int startVolume;
    private boolean verticalGesture;
    private boolean brightnessGesture;
    private boolean volumeGesture;

    private final int[] resizeModes = new int[]{
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
    };
    private final String[] resizeNames = new String[]{"FIT", "CROP", "STRETCH", "WIDTH", "HEIGHT"};
    private int resizeIndex = 0;

    private final ActivityResultLauncher<String[]> subtitlePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                try {
                    getContentResolver().takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) { }
                subtitleUri = uri;
                subtitleMimeType = inferSubtitleMime(uri);
                reloadWithSubtitle();
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);
        getWindow().setNavigationBarColor(android.graphics.Color.BLACK);

        mediaUri = getIntent().getData();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        buildUi();
        hideSystemUi();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(android.graphics.Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setBackgroundColor(android.graphics.Color.BLACK);
        playerView.setUseController(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setResizeMode(resizeModes[resizeIndex]);
        root.addView(playerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        infoView = new TextView(this);
        infoView.setTextColor(android.graphics.Color.WHITE);
        infoView.setTextSize(12f);
        infoView.setPadding(dp(10), dp(8), dp(10), dp(8));
        infoView.setBackgroundColor(0x66000000);
        infoView.setVisibility(View.GONE);
        FrameLayout.LayoutParams infoLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.TOP);
        infoLp.setMargins(dp(12), dp(12), dp(12), dp(12));
        root.addView(infoView, infoLp);

        gestureView = new TextView(this);
        gestureView.setTextColor(android.graphics.Color.WHITE);
        gestureView.setTextSize(18f);
        gestureView.setGravity(Gravity.CENTER);
        gestureView.setPadding(dp(16), dp(10), dp(16), dp(10));
        gestureView.setBackgroundColor(0x88000000);
        gestureView.setVisibility(View.GONE);
        FrameLayout.LayoutParams gestureLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        root.addView(gestureView, gestureLp);

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setGravity(Gravity.CENTER_VERTICAL);
        quick.setPadding(dp(6), dp(6), dp(6), dp(6));
        quick.setBackgroundColor(0x44000000);

        Button sub = smallButton("SUB");
        sub.setOnClickListener(v -> subtitlePicker.launch(new String[]{
                "application/x-subrip", "text/vtt", "text/plain", "application/ttml+xml", "*/*"
        }));
        quick.addView(sub);

        resizeButton = smallButton(resizeNames[resizeIndex]);
        resizeButton.setOnClickListener(v -> cycleResizeMode());
        quick.addView(resizeButton);

        Button info = smallButton("INFO");
        info.setOnClickListener(v -> toggleInfo());
        quick.addView(info);

        FrameLayout.LayoutParams quickLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.TOP);
        quickLp.setMargins(dp(8), dp(8), dp(8), dp(8));
        root.addView(quick, quickLp);

        playerView.setOnTouchListener(this::handleTouch);
        setContentView(root);
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(11f);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(42));
        lp.setMargins(dp(2), 0, dp(2), 0);
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    protected void onStart() {
        super.onStart();
        initializePlayer();
    }

    private void initializePlayer() {
        if (player != null) return;
        if (mediaUri == null) {
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
        player.addListener(new Player.Listener() {
            @Override
            public void onTracksChanged(Tracks tracks) {
                updateInfo();
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) updateInfo();
            }
        });

        player.setMediaItem(buildMediaItem());
        if (resumePositionMs > 0) player.seekTo(resumePositionMs);
        player.prepare();
        player.play();
    }

    private MediaItem buildMediaItem() {
        MediaItem.Builder builder = new MediaItem.Builder().setUri(mediaUri);
        if (subtitleUri != null) {
            MediaItem.SubtitleConfiguration subtitle = new MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                    .setMimeType(subtitleMimeType)
                    .setLanguage("und")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build();
            builder.setSubtitleConfigurations(Collections.singletonList(subtitle));
        }
        return builder.build();
    }

    private void reloadWithSubtitle() {
        if (player == null) return;
        long position = player.getCurrentPosition();
        boolean playWhenReady = player.getPlayWhenReady();
        player.setMediaItem(buildMediaItem(), position);
        player.prepare();
        player.setPlayWhenReady(playWhenReady);
        Toast.makeText(this, "Subtitle loaded", Toast.LENGTH_SHORT).show();
    }

    private String inferSubtitleMime(Uri uri) {
        String name = queryDisplayName(uri).toLowerCase(Locale.ROOT);
        if (name.endsWith(".vtt")) return MimeTypes.TEXT_VTT;
        if (name.endsWith(".ttml") || name.endsWith(".xml")) return MimeTypes.APPLICATION_TTML;
        if (name.endsWith(".ssa") || name.endsWith(".ass")) return MimeTypes.TEXT_SSA;
        return MimeTypes.APPLICATION_SUBRIP;
    }

    private String queryDisplayName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return c.getString(index);
            }
        } catch (Exception ignored) { }
        String p = uri.getLastPathSegment();
        return p == null ? "subtitle.srt" : p;
    }

    private void cycleResizeMode() {
        resizeIndex = (resizeIndex + 1) % resizeModes.length;
        playerView.setResizeMode(resizeModes[resizeIndex]);
        resizeButton.setText(resizeNames[resizeIndex]);
        showGestureText("Screen: " + resizeNames[resizeIndex]);
    }

    private boolean handleTouch(View view, MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                verticalGesture = false;
                brightnessGesture = false;
                volumeGesture = false;
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                startBrightness = lp.screenBrightness;
                if (startBrightness < 0f) startBrightness = 0.5f;
                startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                return false;

            case MotionEvent.ACTION_MOVE:
                float dx = x - downX;
                float dy = y - downY;
                if (!verticalGesture && Math.abs(dy) > dp(24) && Math.abs(dy) > Math.abs(dx) * 1.25f) {
                    verticalGesture = true;
                    if (downX < view.getWidth() / 2f) brightnessGesture = true;
                    else volumeGesture = true;
                }
                if (verticalGesture) {
                    float delta = -dy / Math.max(1f, view.getHeight());
                    if (brightnessGesture) adjustBrightness(delta);
                    if (volumeGesture) adjustVolume(delta);
                    return true;
                }
                return false;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (verticalGesture) {
                    gestureView.postDelayed(() -> gestureView.setVisibility(View.GONE), 700);
                    return true;
                }
                hideSystemUi();
                return false;
        }
        return false;
    }

    private void adjustBrightness(float delta) {
        float value = clamp(startBrightness + delta, 0.02f, 1f);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = value;
        getWindow().setAttributes(lp);
        showGestureText(String.format(Locale.US, "☀ Brightness  %d%%", Math.round(value * 100f)));
    }

    private void adjustVolume(float delta) {
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int value = Math.round(clamp(startVolume + delta * max, 0f, max));
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
        showGestureText(String.format(Locale.US, "🔊 Volume  %d%%", Math.round(value * 100f / Math.max(1, max))));
    }

    private void showGestureText(String text) {
        gestureView.setText(text);
        gestureView.setVisibility(View.VISIBLE);
    }

    private void toggleInfo() {
        if (infoView.getVisibility() == View.VISIBLE) {
            infoView.setVisibility(View.GONE);
        } else {
            updateInfo();
            infoView.setVisibility(View.VISIBLE);
        }
    }

    private void updateInfo() {
        if (player == null) return;
        Format video = null;
        Format audio = null;
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSelected(i)) continue;
                Format f = group.getTrackFormat(i);
                if (group.getType() == C.TRACK_TYPE_VIDEO && video == null) video = f;
                if (group.getType() == C.TRACK_TYPE_AUDIO && audio == null) audio = f;
            }
        }

        StringBuilder s = new StringBuilder();
        s.append("EXTREME DV INFO\n");
        if (video != null) {
            s.append("Video: ").append(video.width).append('×').append(video.height);
            if (video.frameRate > 0) s.append(String.format(Locale.US, "  %.2f fps", video.frameRate));
            s.append('\n');
            s.append("Video codec: ").append(nonNull(video.codecs, video.sampleMimeType)).append('\n');
            s.append("Video bitrate: ").append(formatBitrate(video.averageBitrate, video.peakBitrate)).append('\n');
            s.append("HDR/DV: ").append(describeDolbyVision(video)).append('\n');
            s.append("DV decoder preference: c2.dolby.decoder.hevc\n");
        } else {
            s.append("Video: waiting for track info…\n");
        }

        if (audio != null) {
            s.append("Audio: ").append(nonNull(audio.codecs, audio.sampleMimeType)).append('\n');
            s.append("Audio bitrate: ").append(formatBitrate(audio.averageBitrate, audio.peakBitrate)).append('\n');
            if (audio.channelCount > 0) s.append("Channels: ").append(audio.channelCount).append('\n');
            if (audio.sampleRate > 0) s.append("Sample rate: ").append(audio.sampleRate).append(" Hz\n");
        }
        if (subtitleUri != null) s.append("External subtitle: ON\n");
        infoView.setText(s.toString().trim());
    }

    private String describeDolbyVision(Format f) {
        String codec = f.codecs == null ? "" : f.codecs.toLowerCase(Locale.ROOT);
        if (codec.contains("dvhe.05") || codec.contains("dvh1.05")) return "Dolby Vision Profile 5";
        if (codec.contains("dvhe.07") || codec.contains("dvh1.07")) return "Dolby Vision Profile 7";
        if (codec.contains("dvhe.08") || codec.contains("dvh1.08")) {
            return "Dolby Vision Profile 8 (8.x; 8.4 sub-profile requires bitstream metadata check)";
        }
        if (codec.contains("dvav.09")) return "Dolby Vision Profile 9";
        if (codec.contains("dav1") || codec.contains("dva1")) return "Dolby Vision AV1 / Profile 10 family";
        if (MimeTypes.VIDEO_DOLBY_VISION.equals(f.sampleMimeType)) return "Dolby Vision (profile not declared in codec string)";
        if (f.colorInfo != null && f.colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084) return "HDR10 / PQ";
        if (f.colorInfo != null && f.colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG) return "HLG HDR";
        return "SDR / unknown";
    }

    private String formatBitrate(int average, int peak) {
        int value = average > 0 ? average : peak;
        if (value <= 0) return "not declared";
        if (value >= 1_000_000) return String.format(Locale.US, "%.2f Mbps", value / 1_000_000f);
        return String.format(Locale.US, "%.0f kbps", value / 1000f);
    }

    private String nonNull(String primary, String fallback) {
        if (primary != null && !primary.isEmpty()) return primary;
        if (fallback != null && !fallback.isEmpty()) return fallback;
        return "unknown";
    }

    private MediaCodecSelector dolbyFirstSelector() {
        return (mimeType, requiresSecureDecoder, requiresTunnelingDecoder) -> {
            List<MediaCodecInfo> original = MediaCodecSelector.DEFAULT.getDecoderInfos(
                    mimeType, requiresSecureDecoder, requiresTunnelingDecoder);
            if (!MimeTypes.VIDEO_DOLBY_VISION.equals(mimeType) || original.size() < 2) return original;
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    protected void onStop() {
        if (player != null) {
            resumePositionMs = player.getCurrentPosition();
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
        super.onStop();
    }
}
