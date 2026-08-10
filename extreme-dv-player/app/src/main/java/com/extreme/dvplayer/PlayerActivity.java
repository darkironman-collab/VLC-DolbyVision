package com.extreme.dvplayer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
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
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
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
    private static final String[] MODES = {
            "Original stream (device default)",
            "Native Auto (P5/P8)",
            "Prefer Dolby Vision Profile 8 family",
            "Prefer Dolby Vision Profile 5",
            "HDR base-layer fallback"
    };

    private ExoPlayer player;
    private PlayerView playerView;
    private FrameLayout root;
    private TextView infoView;
    private TextView gestureView;
    private Button resizeButton;
    private Button hardwareButton;

    private Uri mediaUri;
    private Uri subtitleUri;
    private String subtitleMimeType;
    private long resumePositionMs;
    private int currentMode = 2;
    private String modeName = MODES[2];
    private boolean pendingPlayWhenReady = true;

    private AudioManager audioManager;
    private SharedPreferences settings;
    private float downX, downY, startBrightness;
    private int startVolume;
    private boolean verticalGesture, brightnessGesture, volumeGesture;

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
                try { getContentResolver().takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
                subtitleUri = uri;
                subtitleMimeType = inferSubtitleMime(uri);
                reloadWithSubtitle();
            });

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);
        getWindow().setNavigationBarColor(android.graphics.Color.BLACK);
        mediaUri = getIntent().getData();
        currentMode = getIntent().getIntExtra("mode", 2);
        if (currentMode < 0 || currentMode >= MODES.length) currentMode = 2;
        modeName = MODES[currentMode];
        settings = getSharedPreferences("player_settings", MODE_PRIVATE);
        if (settings.getBoolean("resume", true)) resumePositionMs = getIntent().getLongExtra("resume_position", 0L);
        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        buildUi(); hideSystemUi();
    }

    private void buildUi() {
        root = new FrameLayout(this); root.setBackgroundColor(android.graphics.Color.BLACK);
        playerView = new PlayerView(this); playerView.setBackgroundColor(android.graphics.Color.BLACK); playerView.setUseController(true);
        playerView.setShowBuffering(settings.getBoolean("buffer_indicator", true) ? PlayerView.SHOW_BUFFERING_WHEN_PLAYING : PlayerView.SHOW_BUFFERING_NEVER);
        playerView.setResizeMode(resizeModes[resizeIndex]);
        root.addView(playerView,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));

        infoView = new TextView(this); infoView.setTextColor(android.graphics.Color.WHITE); infoView.setTextSize(12f); infoView.setPadding(dp(10),dp(8),dp(10),dp(8)); infoView.setBackgroundColor(0x66000000); infoView.setVisibility(View.GONE);
        FrameLayout.LayoutParams ilp=new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,FrameLayout.LayoutParams.WRAP_CONTENT,Gravity.START|Gravity.TOP); ilp.setMargins(dp(12),dp(12),dp(12),dp(12)); root.addView(infoView,ilp);

        gestureView=new TextView(this); gestureView.setTextColor(android.graphics.Color.WHITE); gestureView.setTextSize(18f); gestureView.setGravity(Gravity.CENTER); gestureView.setPadding(dp(16),dp(10),dp(16),dp(10)); gestureView.setBackgroundColor(0x88000000); gestureView.setVisibility(View.GONE);
        root.addView(gestureView,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,FrameLayout.LayoutParams.WRAP_CONTENT,Gravity.CENTER));

        LinearLayout quick=new LinearLayout(this); quick.setOrientation(LinearLayout.HORIZONTAL); quick.setGravity(Gravity.CENTER_VERTICAL); quick.setPadding(dp(4),dp(4),dp(4),dp(4)); quick.setBackgroundColor(0x33000000);
        Button audio=smallButton("♫"); audio.setTextSize(20f); audio.setOnClickListener(v->showAudioTracks()); quick.addView(audio);
        Button sub=smallButton("SUB"); sub.setOnClickListener(v->subtitlePicker.launch(new String[]{"application/x-subrip","text/vtt","text/plain","application/ttml+xml","*/*"})); quick.addView(sub);
        hardwareButton=smallButton("HW/DV"); hardwareButton.setOnClickListener(v->showDecoderModes()); quick.addView(hardwareButton);
        Button more=smallButton("⋮"); more.setTextSize(22f); more.setOnClickListener(v->showMoreMenu()); quick.addView(more);
        FrameLayout.LayoutParams qlp=new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,FrameLayout.LayoutParams.WRAP_CONTENT,Gravity.END|Gravity.TOP); qlp.setMargins(dp(8),dp(8),dp(8),dp(8)); root.addView(quick,qlp);

        resizeButton=smallButton("▣ "+resizeNames[resizeIndex]); resizeButton.setOnClickListener(v->cycleResizeMode());
        FrameLayout.LayoutParams rlp=new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,dp(44),Gravity.END|Gravity.BOTTOM); rlp.setMargins(dp(8),dp(8),dp(14),dp(16)); root.addView(resizeButton,rlp);
        playerView.setOnTouchListener(this::handleTouch); setContentView(root);
    }

    private Button smallButton(String text){Button b=new Button(this);b.setText(text);b.setTextColor(android.graphics.Color.WHITE);b.setTextSize(12f);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(10),0,dp(10),0);b.setBackgroundColor(0x44000000);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,dp(42));lp.setMargins(dp(2),0,dp(2),0);b.setLayoutParams(lp);return b;}

    @Override protected void onStart(){super.onStart();initializePlayer();}

    private void initializePlayer(){
        if(player!=null)return; if(mediaUri==null){finish();return;}
        MediaCodecSelector selector=(currentMode==0||currentMode==4)?MediaCodecSelector.DEFAULT:dolbyFirstSelector();
        DefaultRenderersFactory rf=new DefaultRenderersFactory(this).setMediaCodecSelector(selector).setEnableDecoderFallback(true);
        DefaultLoadControl lc=new DefaultLoadControl.Builder()
                .setBufferDurationsMs(30_000,120_000,750,5_000)
                .setBackBuffer(15_000,true)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
        player=new ExoPlayer.Builder(this,rf).setLoadControl(lc).build(); playerView.setPlayer(player);
        player.addListener(new Player.Listener(){
            @Override public void onTracksChanged(Tracks tracks){updateInfo();saveRecent();}
            @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_READY){updateInfo();saveRecent();}}
        });
        player.setMediaItem(buildMediaItem()); if(resumePositionMs>0)player.seekTo(resumePositionMs); player.prepare(); player.setPlayWhenReady(pendingPlayWhenReady);
    }

    private void showDecoderModes(){
        new AlertDialog.Builder(this).setTitle("Dolby Vision decode mode").setSingleChoiceItems(MODES,currentMode,(d,which)->{d.dismiss();switchDecoderMode(which);}).setNegativeButton("Cancel",null).show();
    }

    private void switchDecoderMode(int newMode){
        if(newMode==currentMode)return; long pos=player==null?resumePositionMs:player.getCurrentPosition(); boolean play=player==null||player.getPlayWhenReady();
        resumePositionMs=pos; pendingPlayWhenReady=play; if(player!=null){playerView.setPlayer(null);player.release();player=null;}
        currentMode=newMode; modeName=MODES[newMode]; hardwareButton.setText("HW/DV"); showGestureText("Decoder: "+modeName); initializePlayer();
    }

    private MediaItem buildMediaItem(){MediaItem.Builder b=new MediaItem.Builder().setUri(mediaUri);if(subtitleUri!=null){MediaItem.SubtitleConfiguration s=new MediaItem.SubtitleConfiguration.Builder(subtitleUri).setMimeType(subtitleMimeType).setLanguage("und").setSelectionFlags(settings.getBoolean("subtitle_auto",true)?C.SELECTION_FLAG_DEFAULT:0).build();b.setSubtitleConfigurations(Collections.singletonList(s));}return b.build();}
    private void reloadWithSubtitle(){if(player==null)return;long p=player.getCurrentPosition();boolean play=player.getPlayWhenReady();player.setMediaItem(buildMediaItem(),p);player.prepare();player.setPlayWhenReady(play);Toast.makeText(this,"Subtitle loaded",Toast.LENGTH_SHORT).show();}
    private String inferSubtitleMime(Uri uri){String n=queryDisplayName(uri).toLowerCase(Locale.ROOT);if(n.endsWith(".vtt"))return MimeTypes.TEXT_VTT;if(n.endsWith(".ttml")||n.endsWith(".xml"))return MimeTypes.APPLICATION_TTML;if(n.endsWith(".ssa")||n.endsWith(".ass"))return MimeTypes.TEXT_SSA;return MimeTypes.APPLICATION_SUBRIP;}
    private String queryDisplayName(Uri uri){try(Cursor c=getContentResolver().query(uri,null,null,null,null)){if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0)return c.getString(i);}}catch(Exception ignored){}String p=uri.getLastPathSegment();return p==null?"Video":p;}

    private void showAudioTracks(){
        if(player==null)return;List<Tracks.Group> groups=new ArrayList<>();List<Integer> idx=new ArrayList<>();List<String> labels=new ArrayList<>();int selected=-1;
        for(Tracks.Group g:player.getCurrentTracks().getGroups()){if(g.getType()!=C.TRACK_TYPE_AUDIO)continue;for(int i=0;i<g.length;i++){Format f=g.getTrackFormat(i);String l=f.label;if(l==null||l.isEmpty())l=f.language;if(l==null||l.isEmpty())l="Audio "+(labels.size()+1);if(f.channelCount>0)l+=" · "+f.channelCount+"ch";if(f.codecs!=null)l+=" · "+f.codecs;groups.add(g);idx.add(i);labels.add(l);if(g.isTrackSelected(i))selected=labels.size()-1;}}
        if(labels.isEmpty()){Toast.makeText(this,"No alternate audio tracks",Toast.LENGTH_SHORT).show();return;}final int checked=selected;
        new AlertDialog.Builder(this).setTitle("Audio track").setSingleChoiceItems(labels.toArray(new String[0]),checked,(d,w)->{Tracks.Group g=groups.get(w);TrackSelectionOverride o=new TrackSelectionOverride(g.getMediaTrackGroup(),idx.get(w));player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).setOverrideForType(o).build());d.dismiss();updateInfo();}).setNegativeButton("Cancel",null).show();
    }

    private void showMoreMenu(){String[] items={infoView.getVisibility()==View.VISIBLE?"Hide video info":"Show video info","Load external subtitle","Change screen size","Change Dolby Vision decode mode"};new AlertDialog.Builder(this).setTitle("Playback options").setItems(items,(d,w)->{if(w==0)toggleInfo();else if(w==1)subtitlePicker.launch(new String[]{"application/x-subrip","text/vtt","text/plain","application/ttml+xml","*/*"});else if(w==2)cycleResizeMode();else showDecoderModes();}).show();}
    private void cycleResizeMode(){resizeIndex=(resizeIndex+1)%resizeModes.length;playerView.setResizeMode(resizeModes[resizeIndex]);resizeButton.setText("▣ "+resizeNames[resizeIndex]);showGestureText("Screen: "+resizeNames[resizeIndex]);}

    private boolean handleTouch(View view, MotionEvent event){float x=event.getX(),y=event.getY();switch(event.getActionMasked()){
        case MotionEvent.ACTION_DOWN:downX=x;downY=y;verticalGesture=false;brightnessGesture=false;volumeGesture=false;WindowManager.LayoutParams lp=getWindow().getAttributes();startBrightness=lp.screenBrightness;if(startBrightness<0)startBrightness=.5f;startVolume=audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);return false;
        case MotionEvent.ACTION_MOVE:float dx=x-downX,dy=y-downY;if(!verticalGesture&&Math.abs(dy)>dp(24)&&Math.abs(dy)>Math.abs(dx)*1.25f){verticalGesture=true;if(downX<view.getWidth()/2f)brightnessGesture=true;else volumeGesture=true;}if(verticalGesture){float delta=-dy/Math.max(1f,view.getHeight());if(brightnessGesture&&settings.getBoolean("brightness_gesture",true))adjustBrightness(delta);if(volumeGesture&&settings.getBoolean("volume_gesture",true))adjustVolume(delta);return true;}return false;
        case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:if(verticalGesture){gestureView.postDelayed(()->gestureView.setVisibility(View.GONE),700);return true;}hideSystemUi();return false;}return false;}
    private void adjustBrightness(float d){float v=clamp(startBrightness+d,.02f,1f);WindowManager.LayoutParams lp=getWindow().getAttributes();lp.screenBrightness=v;getWindow().setAttributes(lp);showGestureText(String.format(Locale.US,"☀ Brightness %d%%",Math.round(v*100)));}
    private void adjustVolume(float d){int max=audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);int v=Math.round(clamp(startVolume+d*max,0,max));audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,v,0);showGestureText(String.format(Locale.US,"🔊 Volume %d%%",Math.round(v*100f/Math.max(1,max))));}
    private void showGestureText(String t){gestureView.setText(t);gestureView.setVisibility(View.VISIBLE);}
    private void toggleInfo(){if(infoView.getVisibility()==View.VISIBLE)infoView.setVisibility(View.GONE);else{updateInfo();infoView.setVisibility(View.VISIBLE);}}

    private Format selectedFormat(int type){if(player==null)return null;for(Tracks.Group g:player.getCurrentTracks().getGroups())if(g.getType()==type)for(int i=0;i<g.length;i++)if(g.isTrackSelected(i))return g.getTrackFormat(i);return null;}
    private void updateInfo(){if(player==null)return;Format v=selectedFormat(C.TRACK_TYPE_VIDEO),a=selectedFormat(C.TRACK_TYPE_AUDIO);StringBuilder s=new StringBuilder("EXTREME DV INFO\n");s.append("Mode: ").append(modeName).append('\n');s.append("Buffer: 30–120s adaptive\n");if(v!=null){s.append("Video: ").append(v.width).append('×').append(v.height);if(v.frameRate>0)s.append(String.format(Locale.US," %.2f fps",v.frameRate));s.append('\n').append("Codec: ").append(nonNull(v.codecs,v.sampleMimeType)).append('\n').append("Video bitrate: ").append(formatBitrate(v.averageBitrate,v.peakBitrate)).append('\n').append("HDR/DV: ").append(describeDolbyVision(v)).append('\n');}if(a!=null){s.append("Audio: ").append(nonNull(a.codecs,a.sampleMimeType)).append('\n').append("Audio bitrate: ").append(formatBitrate(a.averageBitrate,a.peakBitrate)).append('\n');if(a.channelCount>0)s.append("Channels: ").append(a.channelCount).append('\n');if(a.sampleRate>0)s.append("Sample rate: ").append(a.sampleRate).append(" Hz\n");}if(subtitleUri!=null)s.append("External subtitle: ON\n");infoView.setText(s.toString().trim());}

    private void saveRecent(){if(player==null||mediaUri==null)return;LibraryStore.Recent r=new LibraryStore.Recent();r.uri=mediaUri.toString();r.title=queryDisplayName(mediaUri);r.durationMs=Math.max(0,player.getDuration());r.positionMs=Math.max(0,player.getCurrentPosition());r.lastPlayed=System.currentTimeMillis();Format v=selectedFormat(C.TRACK_TYPE_VIDEO);if(v!=null){r.width=v.width;r.height=v.height;r.badge=shortBadge(v);}try(Cursor c=getContentResolver().query(mediaUri,new String[]{OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.SIZE);if(i>=0)r.sizeBytes=c.getLong(i);}}catch(Exception ignored){}LibraryStore.saveRecent(this,r);}
    private String shortBadge(Format f){String d=describeDolbyVision(f);if(d.startsWith("Dolby Vision Profile 5"))return "DV P5";if(d.startsWith("Dolby Vision Profile 7"))return "DV P7";if(d.startsWith("Dolby Vision Profile 8"))return "DV P8";if(d.startsWith("HDR10"))return "HDR10";if(d.startsWith("HLG"))return "HLG";return "SDR";}
    private String describeDolbyVision(Format f){String c=f.codecs==null?"":f.codecs.toLowerCase(Locale.ROOT);if(c.contains("dvhe.05")||c.contains("dvh1.05"))return "Dolby Vision Profile 5";if(c.contains("dvhe.07")||c.contains("dvh1.07"))return "Dolby Vision Profile 7";if(c.contains("dvhe.08")||c.contains("dvh1.08"))return "Dolby Vision Profile 8 (8.x)";if(c.contains("dvav.09"))return "Dolby Vision Profile 9";if(MimeTypes.VIDEO_DOLBY_VISION.equals(f.sampleMimeType))return "Dolby Vision";if(f.colorInfo!=null&&f.colorInfo.colorTransfer==C.COLOR_TRANSFER_ST2084)return "HDR10 / PQ";if(f.colorInfo!=null&&f.colorInfo.colorTransfer==C.COLOR_TRANSFER_HLG)return "HLG HDR";return "SDR / unknown";}
    private String formatBitrate(int avg,int peak){int v=avg>0?avg:peak;if(v<=0)return "not declared";return v>=1_000_000?String.format(Locale.US,"%.2f Mbps",v/1_000_000f):String.format(Locale.US,"%.0f kbps",v/1000f);}
    private String nonNull(String a,String b){if(a!=null&&!a.isEmpty())return a;if(b!=null&&!b.isEmpty())return b;return "unknown";}

    private MediaCodecSelector dolbyFirstSelector(){return(mime,secure,tunnel)->{List<MediaCodecInfo> original=MediaCodecSelector.DEFAULT.getDecoderInfos(mime,secure,tunnel);if(!MimeTypes.VIDEO_DOLBY_VISION.equals(mime)||original.size()<2)return original;ArrayList<MediaCodecInfo> sorted=new ArrayList<>(original);sorted.sort(Comparator.comparingInt(this::dolbyCodecRank));return sorted;};}
    private int dolbyCodecRank(MediaCodecInfo i){String n=i.name.toLowerCase(Locale.ROOT);if(n.startsWith("c2.dolby.decoder.hevc"))return 0;if(n.contains("dolby"))return 1;if(n.contains("qti")&&n.contains("dv"))return 2;return 10;}
    private void hideSystemUi(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);} private float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}

    @Override protected void onStop(){if(player!=null){saveRecent();resumePositionMs=player.getCurrentPosition();pendingPlayWhenReady=player.getPlayWhenReady();playerView.setPlayer(null);player.release();player=null;}super.onStop();}
}
