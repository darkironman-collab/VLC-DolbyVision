package com.extreme.dvplayer;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_VIDEO = 42;
    private static final String[] MODES = {
            "Original stream (device default)",
            "Native Auto (P5/P8)",
            "Prefer Dolby Vision Profile 8 family",
            "Prefer Dolby Vision Profile 5",
            "HDR base-layer fallback"
    };

    private EditText linkInput;
    private Spinner modeSpinner;
    private TextView activeMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    private View buildUi() {
        int pad = dp(24);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(9, 11, 15));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(26), pad, dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("Extreme DV Player", 38, Color.WHITE, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, lpMatchWrap());

        TextView sub = text("Dolby Vision hardware playback  ·  Profile 8 family first", 18, Color.rgb(177, 181, 194), Typeface.NORMAL);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams subLp = lpMatchWrap();
        subLp.setMargins(0, dp(6), 0, dp(30));
        root.addView(sub, subLp);

        LinearLayout networkCard = card();
        networkCard.addView(text("Play network link", 24, Color.WHITE, Typeface.BOLD));
        linkInput = new EditText(this);
        linkInput.setSingleLine(true);
        linkInput.setTextColor(Color.WHITE);
        linkInput.setHintTextColor(Color.GRAY);
        linkInput.setHint("https://... video / m3u8 / mpd / rtsp");
        linkInput.setTextSize(18);
        linkInput.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams inputLp = lpMatchWrap();
        inputLp.setMargins(0, dp(20), 0, dp(14));
        networkCard.addView(linkInput, inputLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button paste = button("Paste link", false);
        Button play = button("Play link", true);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(58), 1f);
        half.setMargins(0, 0, dp(8), 0);
        row.addView(paste, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(58), 1f);
        half2.setMargins(dp(8), 0, 0, 0);
        row.addView(play, half2);
        networkCard.addView(row);

        TextView support = text("Supports direct HTTP/HTTPS, HLS, DASH and RTSP links", 16, Color.rgb(177, 181, 194), Typeface.NORMAL);
        LinearLayout.LayoutParams supportLp = lpMatchWrap();
        supportLp.setMargins(0, dp(16), 0, 0);
        networkCard.addView(support, supportLp);
        root.addView(networkCard, cardLp());

        paste.setOnClickListener(v -> pasteClipboard());
        play.setOnClickListener(v -> playUri(linkInput.getText().toString().trim()));

        LinearLayout modeCard = card();
        modeCard.addView(text("Dolby Vision decode mode", 24, Color.WHITE, Typeface.BOLD));
        modeSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, MODES) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(18);
                tv.setPadding(dp(14), 0, dp(14), 0);
                return tv;
            }
        };
        modeSpinner.setAdapter(adapter);
        modeSpinner.setSelection(2);
        modeSpinner.setBackground(roundRect(Color.rgb(39, 45, 57), 2, Color.rgb(200, 255, 32), 18));
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70));
        spinnerLp.setMargins(0, dp(18), 0, dp(14));
        modeCard.addView(modeSpinner, spinnerLp);

        activeMode = text("Active mode: Prefer Dolby Vision Profile 8 family", 17, Color.rgb(200, 255, 32), Typeface.BOLD);
        modeCard.addView(activeMode);
        TextView note = text("Original stream leaves codec selection to Android unchanged. DV modes prefer the native OPlus/Dolby MediaCodec path; compressed video is not transcoded.", 15, Color.rgb(177,181,194), Typeface.NORMAL);
        LinearLayout.LayoutParams noteLp = lpMatchWrap();
        noteLp.setMargins(0, dp(8), 0, 0);
        modeCard.addView(note, noteLp);
        root.addView(modeCard, cardLp());

        modeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                activeMode.setText("Active mode: " + MODES[position]);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        Button local = button("Open local video", true);
        LinearLayout.LayoutParams big = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68));
        big.setMargins(0, dp(10), 0, dp(14));
        root.addView(local, big);
        local.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("video/*");
            startActivityForResult(i, PICK_VIDEO);
        });

        Button settings = button("All player settings", false);
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62));
        settingsLp.setMargins(0, 0, 0, dp(6));
        root.addView(settings, settingsLp);
        settings.setOnClickListener(v -> showSettings());

        TextView permissions = text("App permissions", 17, Color.rgb(177,181,194), Typeface.NORMAL);
        permissions.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams pLp = lpMatchWrap();
        pLp.setMargins(0, dp(18), 0, 0);
        root.addView(permissions, pLp);
        permissions.setOnClickListener(v -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });
        return scroll;
    }

    private void pasteClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cm.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            CharSequence value = clip.getItemAt(0).coerceToText(this);
            linkInput.setText(value);
            linkInput.setSelection(linkInput.length());
        }
    }

    private void playUri(String value) {
        if (value.isEmpty()) {
            Toast.makeText(this, "Paste or enter a video link", Toast.LENGTH_SHORT).show();
            return;
        }
        launchPlayer(Uri.parse(value));
    }

    private void launchPlayer(Uri uri) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.setData(uri);
        i.putExtra("mode", modeSpinner.getSelectedItemPosition());
        i.putExtra("mode_name", MODES[modeSpinner.getSelectedItemPosition()]);
        startActivity(i);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_VIDEO && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try { getContentResolver().takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
            launchPlayer(data.getData());
        }
    }

    private void showSettings() {
        String[] items = {
                "Original stream / Android default decoder mode",
                "Hardware decoding: ON",
                "Prefer OPlus/Dolby decoder for DV modes: ON",
                "Decoder fallback: ON",
                "Brightness swipe: left side",
                "Volume swipe: right side",
                "External subtitles + audio track selector",
                "Crop / fit / stretch / width / height controls",
                "Transparent media info overlay"
        };
        new AlertDialog.Builder(this)
                .setTitle("Player settings")
                .setItems(items, null)
                .setPositiveButton("OK", null)
                .show();
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(22), dp(22), dp(22), dp(22));
        c.setBackground(roundRect(Color.rgb(23, 28, 37), 1, Color.rgb(55, 64, 82), 22));
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(22));
        return lp;
    }

    private Button button(String label, boolean accent) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(18);
        b.setTypeface(Typeface.DEFAULT, accent ? Typeface.BOLD : Typeface.NORMAL);
        b.setTextColor(accent ? Color.BLACK : Color.WHITE);
        b.setBackground(roundRect(accent ? Color.rgb(200,255,32) : Color.rgb(39,45,57), 0, Color.TRANSPARENT, 10));
        return b;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, style);
        return t;
    }

    private GradientDrawable roundRect(int fill, int strokeDp, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private LinearLayout.LayoutParams lpMatchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
