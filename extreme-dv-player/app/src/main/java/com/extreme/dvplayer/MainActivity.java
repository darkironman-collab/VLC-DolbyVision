package com.extreme.dvplayer;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_VIDEO = 42;
    private static final String[] MODES = {
            "Original stream (device default)",
            "Native Auto (P5/P8)",
            "Prefer Dolby Vision Profile 8 family",
            "Prefer Dolby Vision Profile 5",
            "HDR base-layer fallback"
    };

    private LinearLayout page;
    private LinearLayout nav;
    private EditText linkInput;
    private int currentTab = 0;

    private final ActivityResultLauncher<Uri> folderPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri == null) return;
                try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (Exception ignored) {}
                LibraryStore.addFolder(this, uri.toString());
                showPlaylists();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildShell());
        showHome();
    }

    @Override protected void onResume() {
        super.onResume();
        if (page != null && currentTab == 1) showRecents();
    }

    private View buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(8,10,14));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(22), dp(18), dp(24));
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(4), dp(4), dp(4), dp(6));
        nav.setBackgroundColor(Color.rgb(18,22,29));
        String[] labels = {"⌂\nHome", "◷\nRecents", "☷\nPlaylists", "⇄\nNetwork", "⚙\nSettings"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            Button b = new Button(this);
            b.setText(labels[i]); b.setAllCaps(false); b.setTextSize(11); b.setTextColor(Color.WHITE);
            b.setBackgroundColor(Color.TRANSPARENT); b.setPadding(0,0,0,0);
            b.setOnClickListener(v -> selectTab(index));
            nav.addView(b, new LinearLayout.LayoutParams(0, dp(58), 1f));
        }
        root.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));
        return root;
    }

    private void selectTab(int i) {
        currentTab = i;
        if (i == 0) showHome();
        else if (i == 1) showRecents();
        else if (i == 2) showPlaylists();
        else if (i == 3) showNetwork();
        else showSettingsPage();
    }

    private void clearPage(String title, String subtitle) {
        page.removeAllViews();
        TextView t = text(title, 30, Color.WHITE, Typeface.BOLD); page.addView(t);
        if (subtitle != null) {
            TextView s = text(subtitle, 15, Color.rgb(163,169,183), Typeface.NORMAL);
            LinearLayout.LayoutParams lp = matchWrap(); lp.setMargins(0, dp(4), 0, dp(20)); page.addView(s, lp);
        }
    }

    private void showHome() {
        currentTab = 0;
        clearPage("Extreme DV Player", "Dolby Vision hardware playback · fast online streaming");

        Button local = button("Open local video", true);
        page.addView(local, tallLp());
        local.setOnClickListener(v -> openLocalVideo());

        LinearLayout net = card();
        net.addView(text("Play network link", 21, Color.WHITE, Typeface.BOLD));
        linkInput = new EditText(this); linkInput.setSingleLine(true); linkInput.setTextColor(Color.WHITE); linkInput.setHintTextColor(Color.GRAY);
        linkInput.setHint("https://… / m3u8 / mpd / rtsp"); linkInput.setTextSize(16); linkInput.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams ilp = matchWrap(); ilp.setMargins(0,dp(12),0,dp(10)); net.addView(linkInput, ilp);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        Button paste = button("Paste", false); Button play = button("Play", true);
        row.addView(paste, halfLp(dp(54), true)); row.addView(play, halfLp(dp(54), false)); net.addView(row);
        paste.setOnClickListener(v -> pasteClipboard()); play.setOnClickListener(v -> playText(linkInput.getText().toString()));
        page.addView(net, cardLp());

        LinearLayout dv = card(); dv.addView(text("Dolby Vision mode", 21, Color.WHITE, Typeface.BOLD));
        Spinner spinner = modeSpinner(); dv.addView(spinner, spinnerLp());
        TextView note = text("Default can be changed in Settings. During playback tap HW/DV to switch modes without restarting from the beginning.", 14, Color.rgb(163,169,183), Typeface.NORMAL);
        dv.addView(note); page.addView(dv, cardLp());

        List<LibraryStore.Recent> recent = LibraryStore.getRecents(this);
        TextView recentTitle = text("Recently opened", 20, Color.WHITE, Typeface.BOLD); page.addView(recentTitle);
        if (recent.isEmpty()) page.addView(text("No recent videos yet.", 15, Color.GRAY, Typeface.NORMAL));
        else {
            int count = Math.min(3, recent.size());
            for (int i=0;i<count;i++) page.addView(recentRow(recent.get(i)), smallGapLp());
            Button all = button("View all recents", false); all.setOnClickListener(v -> { currentTab=1; showRecents(); }); page.addView(all, normalButtonLp());
        }
    }

    private void showRecents() {
        currentTab = 1;
        clearPage("Recents", "Resume local and online videos where you stopped");
        List<LibraryStore.Recent> items = LibraryStore.getRecents(this);
        if (items.isEmpty()) { page.addView(text("Nothing played yet.", 16, Color.GRAY, Typeface.NORMAL)); return; }
        for (LibraryStore.Recent r : items) page.addView(recentRow(r), smallGapLp());
        Button clear = button("Clear recents", false); clear.setOnClickListener(v -> { LibraryStore.clearRecents(this); showRecents(); }); page.addView(clear, normalButtonLp());
    }

    private View recentRow(LibraryStore.Recent r) {
        LinearLayout c = card(); c.setOrientation(LinearLayout.HORIZONTAL); c.setGravity(Gravity.CENTER_VERTICAL); c.setPadding(dp(10),dp(10),dp(12),dp(10));
        ImageView thumb = new ImageView(this); thumb.setScaleType(ImageView.ScaleType.CENTER_CROP); thumb.setBackgroundColor(Color.rgb(35,40,50));
        c.addView(thumb, new LinearLayout.LayoutParams(dp(118), dp(68)));
        tryLoadThumbnail(thumb, r.uri);
        LinearLayout txt = new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL); txt.setPadding(dp(12),0,0,0);
        txt.addView(text(r.title == null || r.title.isEmpty() ? "Video" : r.title, 16, Color.WHITE, Typeface.BOLD));
        String meta = badgeText(r) + " · " + formatTime(r.positionMs) + " / " + formatTime(r.durationMs);
        txt.addView(text(meta, 13, Color.rgb(200,255,32), Typeface.NORMAL));
        if (r.sizeBytes > 0) txt.addView(text(formatBytes(r.sizeBytes), 12, Color.GRAY, Typeface.NORMAL));
        c.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        c.setOnClickListener(v -> launchPlayer(Uri.parse(r.uri), r.positionMs));
        return c;
    }

    private void tryLoadThumbnail(ImageView view, String uriText) {
        if (uriText == null || !uriText.startsWith("content://")) return;
        new Thread(() -> {
            try {
                MediaMetadataRetriever mmr = new MediaMetadataRetriever(); mmr.setDataSource(this, Uri.parse(uriText));
                Bitmap b = mmr.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC); mmr.release();
                if (b != null) runOnUiThread(() -> view.setImageBitmap(b));
            } catch (Exception ignored) {}
        }).start();
    }

    private String badgeText(LibraryStore.Recent r) {
        String q = r.width > 0 ? r.width + "×" + r.height : "Video";
        return q + " · " + (r.badge == null || r.badge.isEmpty() ? "VIDEO" : r.badge);
    }

    private void showPlaylists() {
        currentTab = 2;
        clearPage("Playlists", "Create playlists and keep favorite local folders");
        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button newList = button("+ Playlist", true); Button addFolder = button("+ Folder", false);
        actions.addView(newList, halfLp(dp(54), true)); actions.addView(addFolder, halfLp(dp(54), false)); page.addView(actions, cardLp());
        newList.setOnClickListener(v -> promptPlaylist()); addFolder.setOnClickListener(v -> folderPicker.launch(null));

        page.addView(text("Playlists", 19, Color.WHITE, Typeface.BOLD));
        List<String> lists = LibraryStore.getPlaylists(this);
        if (lists.isEmpty()) page.addView(text("No playlists yet.", 15, Color.GRAY, Typeface.NORMAL));
        for (String name : lists) {
            Button b = button("☷  " + name, false); b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); page.addView(b, normalButtonLp());
        }
        TextView foldersTitle = text("Folders", 19, Color.WHITE, Typeface.BOLD); LinearLayout.LayoutParams ftlp=matchWrap(); ftlp.setMargins(0,dp(18),0,dp(6)); page.addView(foldersTitle,ftlp);
        List<String> folders = LibraryStore.getFolders(this);
        if (folders.isEmpty()) page.addView(text("No folders added.", 15, Color.GRAY, Typeface.NORMAL));
        for (String f : folders) page.addView(text("📁  " + shortUri(f), 15, Color.WHITE, Typeface.NORMAL), smallGapLp());
    }

    private void promptPlaylist() {
        EditText e = new EditText(this); e.setHint("Playlist name");
        new AlertDialog.Builder(this).setTitle("New playlist").setView(e).setPositiveButton("Create", (d,w) -> { LibraryStore.addPlaylist(this,e.getText().toString()); showPlaylists(); }).setNegativeButton("Cancel",null).show();
    }

    private void showNetwork() {
        currentTab = 3;
        clearPage("Network", "Direct streams plus saved WebDAV, SMB and FTP connection profiles");
        Button direct = button("Play direct URL", true); direct.setOnClickListener(v -> showDirectUrlDialog()); page.addView(direct, tallLp());
        Button add = button("+ Add network connection", false); add.setOnClickListener(v -> addNetworkDialog()); page.addView(add, normalButtonLp());

        List<LibraryStore.NetworkProfile> profiles = LibraryStore.getNetworks(this);
        if (profiles.isEmpty()) page.addView(text("No saved connections.",15,Color.GRAY,Typeface.NORMAL));
        for (LibraryStore.NetworkProfile p : profiles) {
            LinearLayout c = card(); c.addView(text(p.name + "  ·  " + p.type,17,Color.WHITE,Typeface.BOLD)); c.addView(text(p.address,13,Color.GRAY,Typeface.NORMAL));
            c.setOnClickListener(v -> openNetworkProfile(p)); page.addView(c, cardLp());
        }
        TextView note = text("WebDAV/HTTP direct media URLs can play inside Extreme DV Player. SMB/FTP profiles are saved here for the network browser layer; direct SMB/FTP folder browsing is being kept separate from the Dolby playback engine to avoid unstable decoder/network coupling.", 13, Color.rgb(155,160,173), Typeface.NORMAL);
        page.addView(note);
    }

    private void showDirectUrlDialog() {
        EditText e = new EditText(this); e.setHint("https://… / m3u8 / mpd / rtsp");
        new AlertDialog.Builder(this).setTitle("Direct stream").setView(e).setPositiveButton("Play",(d,w)->playText(e.getText().toString())).setNegativeButton("Cancel",null).show();
    }

    private void addNetworkDialog() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); int p=dp(18); box.setPadding(p,0,p,0);
        Spinner type = new Spinner(this); type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"WebDAV","SMB","FTP"}));
        EditText name=new EditText(this); name.setHint("Name"); EditText address=new EditText(this); address.setHint("Server / URL"); EditText user=new EditText(this); user.setHint("Username (optional)"); EditText pass=new EditText(this); pass.setHint("Password (optional)");
        box.addView(type); box.addView(name); box.addView(address); box.addView(user); box.addView(pass);
        new AlertDialog.Builder(this).setTitle("Network connection").setView(box).setPositiveButton("Save",(d,w)->{
            LibraryStore.NetworkProfile np=new LibraryStore.NetworkProfile(); np.type=String.valueOf(type.getSelectedItem()); np.name=name.getText().toString().trim(); np.address=address.getText().toString().trim(); np.username=user.getText().toString(); np.password=pass.getText().toString();
            if(np.name.isEmpty()) np.name=np.type; LibraryStore.addNetwork(this,np); showNetwork();
        }).setNegativeButton("Cancel",null).show();
    }

    private void openNetworkProfile(LibraryStore.NetworkProfile p) {
        String lower = p.address == null ? "" : p.address.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("rtsp://")) launchPlayer(Uri.parse(p.address),0);
        else Toast.makeText(this, p.type + " profile saved. Enter a direct playable media URL/path for playback.", Toast.LENGTH_LONG).show();
    }

    private void showSettingsPage() {
        currentTab = 4;
        clearPage("Settings", "Playback, Dolby Vision, subtitles, gestures and resume behavior");
        android.content.SharedPreferences sp = getSharedPreferences("player_settings", MODE_PRIVATE);
        page.addView(text("Default Dolby Vision decode mode",18,Color.WHITE,Typeface.BOLD));
        Spinner mode = modeSpinner(); page.addView(mode, spinnerLp());
        mode.setSelection(sp.getInt("default_mode",2));
        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){ public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){sp.edit().putInt("default_mode",pos).apply();} public void onNothingSelected(android.widget.AdapterView<?> p){} });

        addSwitch("Hardware decoder", "Use Android MediaCodec hardware decoding", "hardware", true, sp);
        addSwitch("Resume playback", "Continue from the last saved position", "resume", true, sp);
        addSwitch("Brightness gesture", "Swipe vertically on left side", "brightness_gesture", true, sp);
        addSwitch("Volume gesture", "Swipe vertically on right side", "volume_gesture", true, sp);
        addSwitch("Auto-select external subtitles", "Loaded external subtitle becomes default", "subtitle_auto", true, sp);
        addSwitch("Show buffering indicator", "Display spinner only while player is actually buffering", "buffer_indicator", true, sp);

        TextView stream = text("Streaming cache: 30–120 sec adaptive memory buffer · 15 sec back buffer",14,Color.rgb(200,255,32),Typeface.NORMAL); LinearLayout.LayoutParams slp=matchWrap(); slp.setMargins(0,dp(18),0,0); page.addView(stream,slp);
        Button permissions = button("App permissions",false); permissions.setOnClickListener(v->{Intent i=new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()));startActivity(i);}); page.addView(permissions,normalButtonLp());
    }

    private void addSwitch(String title,String subtitle,String key,boolean def,android.content.SharedPreferences sp){
        LinearLayout c=card(); c.setOrientation(LinearLayout.HORIZONTAL); LinearLayout textBox=new LinearLayout(this); textBox.setOrientation(LinearLayout.VERTICAL); textBox.addView(text(title,16,Color.WHITE,Typeface.BOLD)); textBox.addView(text(subtitle,13,Color.GRAY,Typeface.NORMAL)); c.addView(textBox,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)); Switch sw=new Switch(this); sw.setChecked(sp.getBoolean(key,def)); sw.setOnCheckedChangeListener((b,on)->sp.edit().putBoolean(key,on).apply()); c.addView(sw); page.addView(c,cardLp());
    }

    private Spinner modeSpinner() {
        Spinner s = new Spinner(this); ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, MODES); s.setAdapter(a); s.setSelection(getSharedPreferences("player_settings",MODE_PRIVATE).getInt("default_mode",2)); s.setBackground(roundRect(Color.rgb(36,42,53),1,Color.rgb(200,255,32),14)); return s;
    }

    private void openLocalVideo() { Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("video/*"); startActivityForResult(i,PICK_VIDEO); }

    private void pasteClipboard() { ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE); ClipData clip=cm.getPrimaryClip(); if(clip!=null&&clip.getItemCount()>0){CharSequence v=clip.getItemAt(0).coerceToText(this); linkInput.setText(v); linkInput.setSelection(linkInput.length());} }
    private void playText(String value) { value=value.trim(); if(value.isEmpty()){Toast.makeText(this,"Enter a media URL",Toast.LENGTH_SHORT).show();return;} launchPlayer(Uri.parse(value),0); }

    private void launchPlayer(Uri uri,long position) {
        int m=getSharedPreferences("player_settings",MODE_PRIVATE).getInt("default_mode",2);
        Intent i=new Intent(this,PlayerActivity.class); i.setData(uri); i.putExtra("mode",m); i.putExtra("mode_name",MODES[m]); i.putExtra("resume_position",position); startActivity(i);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,@Nullable Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==PICK_VIDEO&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){try{getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}launchPlayer(data.getData(),0);}}

    private String shortUri(String s){ if(s==null)return ""; return s.length()>55?s.substring(0,52)+"…":s; }
    private String formatTime(long ms){ if(ms<=0)return "00:00"; long sec=ms/1000; return String.format(Locale.US,"%02d:%02d",sec/60,sec%60); }
    private String formatBytes(long b){ if(b>=1_073_741_824L)return String.format(Locale.US,"%.2f GB",b/1073741824f); if(b>=1_048_576)return String.format(Locale.US,"%.1f MB",b/1048576f); return b+" B"; }

    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(18),dp(16),dp(18),dp(16));c.setBackground(roundRect(Color.rgb(22,27,36),1,Color.rgb(52,61,78),18));return c;}
    private Button button(String label,boolean accent){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(16);b.setTypeface(Typeface.DEFAULT,accent?Typeface.BOLD:Typeface.NORMAL);b.setTextColor(accent?Color.BLACK:Color.WHITE);b.setBackground(roundRect(accent?Color.rgb(200,255,32):Color.rgb(37,44,56),0,Color.TRANSPARENT,10));return b;}
    private TextView text(String v,int sp,int color,int style){TextView t=new TextView(this);t.setText(v);t.setTextSize(sp);t.setTextColor(color);t.setTypeface(Typeface.DEFAULT,style);return t;}
    private GradientDrawable roundRect(int fill,int strokeDp,int stroke,int radiusDp){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radiusDp));if(strokeDp>0)g.setStroke(dp(strokeDp),stroke);return g;}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private LinearLayout.LayoutParams cardLp(){LinearLayout.LayoutParams lp=matchWrap();lp.setMargins(0,0,0,dp(14));return lp;}
    private LinearLayout.LayoutParams smallGapLp(){LinearLayout.LayoutParams lp=matchWrap();lp.setMargins(0,0,0,dp(9));return lp;}
    private LinearLayout.LayoutParams tallLp(){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(62));lp.setMargins(0,0,0,dp(14));return lp;}
    private LinearLayout.LayoutParams normalButtonLp(){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54));lp.setMargins(0,dp(8),0,dp(8));return lp;}
    private LinearLayout.LayoutParams halfLp(int h,boolean left){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,h,1f);if(left)lp.setMargins(0,0,dp(6),0);else lp.setMargins(dp(6),0,0,0);return lp;}
    private LinearLayout.LayoutParams spinnerLp(){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(60));lp.setMargins(0,dp(10),0,dp(8));return lp;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
