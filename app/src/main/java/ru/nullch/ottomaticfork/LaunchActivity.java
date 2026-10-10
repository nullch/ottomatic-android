package ru.nullch.ottomaticfork;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Prepares the game data, prompts language selection on first launch, then starts OttoActivity.
 */
public class LaunchActivity extends Activity {
    private static final String TAG = "OttoLaunch";
    private static final String PREFS_NAME = "otto_prefs";

    private static final String[] LANG_NAMES = {
            "English", "Français", "Deutsch", "Español", "Italiano", "Svenska", "Русский"
    };

    private ProgressBar bar;
    private TextView label;
    private int totalFiles = 0, copiedFiles = 0;
    private int selectedLangId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(64, 64, 64, 64);
        label = new TextView(this);
        label.setText("Starting Otto Matic...");
        label.setGravity(Gravity.CENTER);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        root.addView(label);
        root.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(root);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean langChosen = prefs.getBoolean("language_chosen", false);
        selectedLangId = prefs.getInt("selected_lang", getSystemDefaultLangIndex());

        if (!langChosen) {
            showLanguageDialog();
        } else {
            new Thread(this::prepareAndLaunch, "OttoUnpack").start();
        }
    }

    private int getSystemDefaultLangIndex() {
        String sysLang = Locale.getDefault().getLanguage().toLowerCase(Locale.US);
        switch (sysLang) {
            case "fr": return 1;
            case "de": return 2;
            case "es": return 3;
            case "it": return 4;
            case "sv": return 5;
            case "ru": return 6;
            default: return 0;
        }
    }

    private void showLanguageDialog() {
        int defaultIdx = getSystemDefaultLangIndex();
        new AlertDialog.Builder(this)
                .setTitle(R.string.select_language_title)
                .setSingleChoiceItems(LANG_NAMES, defaultIdx, (dialog, which) -> {
                    selectedLangId = which;
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("language_chosen", true)
                            .putInt("selected_lang", which)
                            .apply();
                    dialog.dismiss();
                    new Thread(this::prepareAndLaunch, "OttoUnpack").start();
                })
                .setCancelable(false)
                .show();
    }

    private String getMarkerName() {
        try {
            long versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            return ".data_unpacked_v" + versionCode + "_v2";
        } catch (Exception e) {
            return ".data_unpacked_v2";
        }
    }

    private void prepareAndLaunch() {
        try {
            File dataDir = findExternalData();
            if (dataDir == null) {
                dataDir = new File(getFilesDir(), "Data");
                File marker = new File(getFilesDir(), getMarkerName());
                if (!marker.exists() || !new File(dataDir, "System/gamecontrollerdb.txt").exists()) {
                    runOnUiThread(() -> label.setText("Unpacking game data (first run only)..."));
                    totalFiles = countAssets(getAssets(), "Data");
                    runOnUiThread(() -> { bar.setIndeterminate(false); bar.setMax(Math.max(totalFiles, 1)); });
                    copyAssetDir(getAssets(), "Data", dataDir);
                    if (!marker.createNewFile()) Log.w(TAG, "couldn't create marker");
                }
            }
            final String path = dataDir.getAbsolutePath();
            runOnUiThread(() -> {
                Intent i = new Intent(this, OttoActivity.class);
                i.putExtra(OttoActivity.EXTRA_DATA_PATH, path);
                i.putExtra("EXTRA_LANG_ID", selectedLangId);
                startActivity(i);
                finish();
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to prepare game data", e);
            runOnUiThread(() -> label.setText("Failed to prepare game data:\n" + e));
        }
    }

    private File findExternalData() {
        File ext = getExternalFilesDir(null);
        if (ext == null) return null;
        File d = new File(ext, "Data");
        return new File(d, "System/gamecontrollerdb.txt").exists() ? d : null;
    }

    private static int countAssets(AssetManager am, String path) throws IOException {
        String[] kids = am.list(path);
        if (kids == null || kids.length == 0) return 1;
        int n = 0;
        for (String k : kids) n += countAssets(am, path + "/" + k);
        return n;
    }

    private void copyAssetDir(AssetManager am, String src, File dst) throws IOException {
        String[] kids = am.list(src);
        if (kids == null || kids.length == 0) {
            File parent = dst.getParentFile();
            if (parent != null) parent.mkdirs();
            try (InputStream in = am.open(src); OutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            copiedFiles++;
            if ((copiedFiles & 7) == 0) {
                final int c = copiedFiles;
                runOnUiThread(() -> bar.setProgress(c));
            }
            return;
        }
        dst.mkdirs();
        for (String k : kids) copyAssetDir(am, src + "/" + k, new File(dst, k));
    }
}
