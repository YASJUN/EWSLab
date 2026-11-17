package xyz.ckkpd.ewsparserapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Locale;

import xyz.ckkpd.ewsparserapp.core.EwsCodes;
import xyz.ckkpd.ewsparserapp.core.EwsMessage;

/** Offline foreground receiver. No network, background service, or automatic audio output. */
public final class MainActivity extends Activity implements AudioSession.Listener {
    private static final int MICROPHONE = 10, WAV = 11;
    private AudioSession session;
    private TextView status, signalState, areaValue, timeValue, flagValue, rawValue, history, levelText;
    private ProgressBar level;
    private Button start, stop, settings;
    private final ArrayDeque<String> rows = new ArrayDeque<>();
    private int frames;
    private boolean active, foreground;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            findViewById(R.id.root).setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        status = findViewById(R.id.status); signalState = findViewById(R.id.signalState);
        areaValue = findViewById(R.id.areaValue); timeValue = findViewById(R.id.timeValue);
        flagValue = findViewById(R.id.flagValue); rawValue = findViewById(R.id.rawValue);
        history = findViewById(R.id.history); levelText = findViewById(R.id.levelText);
        level = findViewById(R.id.level);
        start = findViewById(R.id.startReceive); stop = findViewById(R.id.stopReceive);
        settings = findViewById(R.id.openSettings);
        session = new AudioSession(this, this);
        start.setOnClickListener(view -> requestMicrophone());
        stop.setOnClickListener(view -> stopInput());
        findViewById(R.id.importWav).setOnClickListener(view -> selectWav());
        settings.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
    }

    private void requestMicrophone() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            resetInput(); session.startMicrophone();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            new AlertDialog.Builder(this).setMessage(R.string.permission_help)
                    .setPositiveButton(R.string.continue_label, (dialog, which) -> askPermission())
                    .setNegativeButton(R.string.cancel_label, null).show();
        } else askPermission();
    }
    private void askPermission() { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MICROPHONE); }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != MICROPHONE || !foreground) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            resetInput(); session.startMicrophone();
        } else {
            boolean permanent = !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO);
            onFinished(getString(permanent ? R.string.permission_permanent : R.string.permission_denied), true);
            settings.setVisibility(permanent ? View.VISIBLE : View.GONE);
        }
    }

    @SuppressWarnings("deprecation")
    private void selectWav() {
        stopInput();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave", "application/octet-stream"});
        try { startActivityForResult(intent, WAV); }
        catch (ActivityNotFoundException error) { onFinished(getString(R.string.picker_unavailable), true); }
    }

    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == WAV && result == RESULT_OK && data != null && data.getData() != null) {
            resetInput(); session.importWav(data.getData());
        }
    }

    private void resetInput() {
        frames = 0; rows.clear();
        signalState.setText(R.string.awaiting);
        signalState.setTextColor(getColor(R.color.accent));
        for (TextView field : new TextView[]{areaValue, timeValue, flagValue, rawValue}) field.setText(R.string.dash);
        history.setText(R.string.no_history);
        settings.setVisibility(View.GONE);
        level.setProgress(0); levelText.setText(R.string.level_initial);
        setActive(true);
    }
    private void setActive(boolean value) { active = value; start.setEnabled(!value); stop.setEnabled(value); }
    private void stopInput() {
        session.stop(); setActive(false); level.setProgress(0);
        onStatus(getString(R.string.stopped));
    }
    @Override public void onStatus(String text) { status.setText(text); status.setTextColor(getColor(R.color.accent)); }
    @Override public void onFinished(String text, boolean error) {
        setActive(false); level.setProgress(0);
        status.setText(text); status.setTextColor(getColor(error ? R.color.error : R.color.accent));
    }
    @Override public void onProgress(double db, double seconds) {
        level.setProgress((int) Math.max(0, Math.min(100, (db + 72) / 72 * 100)));
        levelText.setText(getString(R.string.progress_format, db, seconds, frames));
    }
    @Override public void onMessage(EwsMessage message) {
        frames++;
        String kind = getString(message.kind == EwsMessage.Kind.FIRST ? R.string.kind_first
                : message.kind == EwsMessage.Kind.SECOND ? R.string.kind_second : R.string.kind_end);
        signalState.setText(message.confirmedBlocks >= 2
                ? getString(R.string.confirmed_format, message.confirmedBlocks, kind) : getString(R.string.candidate, kind));
        signalState.setTextColor(getColor(message.confirmedBlocks >= 2 ? R.color.confirmed : R.color.accent));
        String hex = String.format(Locale.ROOT, "%03X", Integer.parseInt(message.areaCode, 2));
        areaValue.setText(getString(R.string.area_format, EwsCodes.AREAS.get(message.areaCode), hex, message.areaCode));
        timeValue.setText(getString(R.string.time_format, message.yearDigit, message.month, message.day, message.hour));
        flagValue.setText(getString(R.string.flags_format, message.dateShifted ? 1 : 0, message.hourShifted ? 1 : 0,
                getString(message.prefixVerified ? R.string.verified : R.string.unverified)));
        StringBuilder bits = new StringBuilder();
        for (int at = 0; at < message.bodyBits.length(); at += 16) {
            if (bits.length() > 0) bits.append('\n');
            bits.append(message.bodyBits, at, at + 16);
        }
        rawValue.setText(bits);
        rows.addFirst(getString(R.string.history_row, frames, kind, message.month, message.day, message.hour, message.confirmedBlocks));
        while (rows.size() > 12) rows.removeLast();
        history.setText(String.join("\n\n", rows));
    }
    @Override protected void onStart() { super.onStart(); foreground = true; }
    @Override protected void onStop() {
        foreground = false;
        if (active) stopInput(); else session.stop();
        super.onStop();
    }
    @Override protected void onDestroy() { session.close(); super.onDestroy(); }
}
