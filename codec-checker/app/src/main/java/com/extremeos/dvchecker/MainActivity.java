package com.extremeos.dvchecker;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextView output;
    private String lastReport = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(Color.rgb(16,16,16));

        TextView title = new TextView(this);
        title.setText("Dolby Vision Codec Checker");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setPadding(0,0,0,dp(10));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Reads Android MediaCodec runtime capabilities. No root or storage permission required.");
        sub.setTextColor(Color.LTGRAY);
        sub.setTextSize(14);
        sub.setPadding(0,0,0,dp(12));
        root.addView(sub);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        Button refresh = new Button(this);
        refresh.setText("Scan");
        refresh.setOnClickListener(v -> scan());
        buttons.addView(refresh, new LinearLayout.LayoutParams(0, dp(52), 1));

        Button copy = new Button(this);
        copy.setText("Copy report");
        copy.setOnClickListener(v -> copyReport());
        buttons.addView(copy, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(buttons);

        ScrollView scroll = new ScrollView(this);
        output = new TextView(this);
        output.setTextColor(Color.WHITE);
        output.setTextSize(14);
        output.setTextIsSelectable(true);
        output.setPadding(0,dp(14),0,dp(30));
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        scan();
    }

    private void scan() {
        StringBuilder sb = new StringBuilder();
        sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append('\n');
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n\n");

        MediaCodecInfo[] codecs = new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();
        List<MediaCodecInfo> dv = new ArrayList<>();
        for (MediaCodecInfo info : codecs) {
            for (String type : info.getSupportedTypes()) {
                if ("video/dolby-vision".equalsIgnoreCase(type)) {
                    dv.add(info);
                    break;
                }
            }
        }

        sb.append("Dolby Vision codecs found: ").append(dv.size()).append("\n\n");
        if (dv.isEmpty()) {
            sb.append("NO video/dolby-vision codec is advertised by MediaCodecList.\n");
            lastReport = sb.toString();
            output.setText(lastReport);
            return;
        }

        boolean p5=false, p7=false, p8=false, p9=false, p10=false;

        for (MediaCodecInfo info : dv) {
            sb.append("==============================\n");
            sb.append(info.isEncoder() ? "ENCODER: " : "DECODER: ").append(info.getName()).append('\n');
            if (Build.VERSION.SDK_INT >= 29) {
                sb.append("Hardware accelerated: ").append(info.isHardwareAccelerated()).append('\n');
                sb.append("Software only: ").append(info.isSoftwareOnly()).append('\n');
                sb.append("Vendor: ").append(info.isVendor()).append('\n');
            }

            try {
                MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType("video/dolby-vision");
                sb.append("Secure playback: ").append(caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback)).append('\n');
                sb.append("Adaptive playback: ").append(caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback)).append('\n');

                if (caps.getVideoCapabilities() != null) {
                    MediaCodecInfo.VideoCapabilities v = caps.getVideoCapabilities();
                    sb.append("Width range: ").append(v.getSupportedWidths()).append('\n');
                    sb.append("Height range: ").append(v.getSupportedHeights()).append('\n');
                    sb.append("Bitrate range: ").append(v.getBitrateRange()).append(" bps\n");
                    sb.append("Frame-rate range: ").append(v.getSupportedFrameRates()).append('\n');
                }

                sb.append("\nAdvertised DV profile/level pairs:\n");
                if (caps.profileLevels == null || caps.profileLevels.length == 0) {
                    sb.append("  (none exposed by this codec)\n");
                } else {
                    for (MediaCodecInfo.CodecProfileLevel pl : caps.profileLevels) {
                        String pn = profileName(pl.profile);
                        String ln = levelName(pl.level);
                        sb.append("  • ").append(pn).append(" [").append(pl.profile).append("]")
                          .append("  |  ").append(ln).append(" [").append(pl.level).append("]\n");
                        if (pl.profile == 32) p5 = true;
                        if (pl.profile == 128) p7 = true;
                        if (pl.profile == 256) p8 = true;
                        if (pl.profile == 512) p9 = true;
                        if (pl.profile == 1024) p10 = true;
                    }
                }
            } catch (Throwable t) {
                sb.append("Capability query error: ").append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append('\n');
            }
            sb.append('\n');
        }

        sb.append("==============================\n");
        sb.append("QUICK PROFILE SUMMARY\n");
        sb.append("Profile 5  (dvhe.05): ").append(mark(p5)).append('\n');
        sb.append("Profile 7  (dvhe.07): ").append(mark(p7)).append('\n');
        sb.append("Profile 8  (dvhe.08): ").append(mark(p8)).append('\n');
        sb.append("Profile 9  (dvav.09): ").append(mark(p9)).append('\n');
        sb.append("Profile 10 (AV1 DV):  ").append(mark(p10)).append('\n');
        sb.append("\nNote: Profile 8.4 is a Profile-8 compatibility variant. Android's MediaCodec API usually advertises Profile 8, not the 8.4 sub-variant. Actual P8.4 playback still needs a real sample test.\n");

        lastReport = sb.toString();
        output.setText(lastReport);
    }

    private String mark(boolean yes) { return yes ? "YES ✓" : "NOT ADVERTISED"; }

    private String profileName(int p) {
        switch (p) {
            case 1: return "dvav.per (deprecated)";
            case 2: return "dvav.pen (deprecated)";
            case 4: return "dvhe.der (deprecated)";
            case 8: return "dvhe.den (deprecated)";
            case 16: return "Profile 4 / dvhe.04";
            case 32: return "Profile 5 / dvhe.05";
            case 64: return "dvhe.dth (deprecated profile 6)";
            case 128: return "Profile 7 / dvhe.07";
            case 256: return "Profile 8 / dvhe.08";
            case 512: return "Profile 9 / dvav.09";
            case 1024: return "Profile 10 / AV1 Dolby Vision";
            default: return String.format(Locale.US, "Unknown DV profile 0x%X", p);
        }
    }

    private String levelName(int l) {
        switch (l) {
            case 1: return "HD 24";
            case 2: return "HD 30";
            case 4: return "FHD 24";
            case 8: return "FHD 30";
            case 16: return "FHD 60";
            case 32: return "UHD 24";
            case 64: return "UHD 30";
            case 128: return "UHD 48";
            case 256: return "UHD 60";
            case 512: return "UHD 120";
            case 1024: return "8K 30";
            case 2048: return "8K 60";
            default: return String.format(Locale.US, "Unknown level 0x%X", l);
        }
    }

    private void copyReport() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("DV Codec Report", lastReport));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
