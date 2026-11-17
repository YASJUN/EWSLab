package xyz.ckkpd.ewsparserapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import xyz.ckkpd.ewsparserapp.core.AudioDecoder;
import xyz.ckkpd.ewsparserapp.core.EwsMessage;
import xyz.ckkpd.ewsparserapp.core.WavReader;

/** One foreground input session; generation IDs discard all stale worker callbacks. */
final class AudioSession implements AutoCloseable {
    interface Listener {
        void onStatus(String text);
        void onMessage(EwsMessage message);
        void onProgress(double db, double seconds);
        void onFinished(String text, boolean error);
    }
    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private final Object recordLock = new Object();
    private AudioRecord recording;
    private Future<?> work;

    AudioSession(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void startMicrophone() {
        stop();
        int token = generation.get();
        listener.onStatus(context.getString(R.string.mic_initializing));
        work = worker.submit(() -> {
            AudioRecord record = null;
            try {
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    throw new SecurityException(context.getString(R.string.permission_denied));
                }
                AudioManager manager = context.getSystemService(AudioManager.class);
                boolean raw = manager != null && "true".equals(manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED));
                int minimum = AudioRecord.getMinBufferSize(48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (minimum <= 0) throw new IllegalStateException(context.getString(R.string.mic_unavailable));
                AudioFormat format = new AudioFormat.Builder().setSampleRate(48000)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build();
                record = new AudioRecord.Builder().setAudioSource(raw ? MediaRecorder.AudioSource.UNPROCESSED : MediaRecorder.AudioSource.DEFAULT)
                        .setAudioFormat(format).setBufferSizeInBytes(Math.max(minimum, 19200)).build();
                synchronized (recordLock) {
                    if (!current(token)) return;
                    if (record.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException(context.getString(R.string.mic_unavailable));
                    recording = record;
                    record.startRecording();
                }
                if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException(context.getString(R.string.mic_start_failed));
                String source = context.getString(raw ? R.string.source_raw : R.string.source_default);
                post(token, () -> listener.onStatus(context.getString(R.string.receiving, source)));
                AudioDecoder decoder = new AudioDecoder(48000, message -> post(token, () -> listener.onMessage(message)));
                short[] buffer = new short[4096];
                long meterAt = 0;
                int emptyReads = 0;
                while (current(token)) {
                    int count = record.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
                    if (!current(token)) break;
                    if (count < 0) throw new IllegalStateException(context.getString(R.string.mic_read_failed, count));
                    if (count == 0) {
                        if (++emptyReads >= 8) throw new IllegalStateException(context.getString(R.string.mic_no_samples));
                        continue;
                    }
                    emptyReads = 0;
                    decoder.accept(buffer, 0, count);
                    long now = SystemClock.elapsedRealtime();
                    if (now - meterAt >= 150) {
                        meterAt = now;
                        double db = dbfs(buffer, count), seconds = decoder.getSamplesProcessed() / 48000.0;
                        post(token, () -> listener.onProgress(db, seconds));
                    }
                }
            } catch (RuntimeException error) {
                fail(token, error);
            } finally {
                synchronized (recordLock) {
                    if (recording == record) recording = null;
                    if (record != null) record.release();
                }
            }
        });
    }

    void importWav(Uri uri) {
        stop();
        int token = generation.get();
        listener.onStatus(context.getString(R.string.wav_opening));
        work = worker.submit(() -> {
            int[] frames = {0};
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException(context.getString(R.string.file_unavailable));
                try (WavReader wav = new WavReader(input)) {
                    int rate = wav.getSampleRate();
                    String description = context.getString(R.string.wav_format, rate, wav.getChannels(), wav.getFrameCount() / (double) rate);
                    post(token, () -> listener.onStatus(description));
                    AudioDecoder decoder = new AudioDecoder(rate, message -> {
                        frames[0]++;
                        post(token, () -> listener.onMessage(message));
                    });
                    short[] buffer = new short[4096];
                    int count;
                    long meterAt = 0;
                    while (current(token) && (count = wav.read(buffer, 0, buffer.length)) != -1) {
                        decoder.accept(buffer, 0, count);
                        long now = SystemClock.elapsedRealtime();
                        if (now - meterAt >= 150) {
                            meterAt = now;
                            double db = dbfs(buffer, count), seconds = decoder.getSamplesProcessed() / (double) rate;
                            post(token, () -> listener.onProgress(db, seconds));
                        }
                    }
                    if (!current(token)) return;
                    decoder.flush();
                    double seconds = decoder.getSamplesProcessed() / (double) rate;
                    post(token, () -> listener.onProgress(-96, seconds));
                    String text = frames[0] == 0 ? context.getString(R.string.wav_no_frames)
                            : context.getString(R.string.wav_done, frames[0]);
                    post(token, () -> listener.onFinished(text, false));
                }
            } catch (IOException | RuntimeException error) {
                fail(token, error);
            }
        });
    }

    void stop() {
        generation.incrementAndGet();
        if (work != null) { work.cancel(true); work = null; }
        synchronized (recordLock) {
            if (recording != null) {
                try { recording.stop(); } catch (IllegalStateException ignored) { /* Worker reports actual capture failures. */ }
            }
        }
    }
    private boolean current(int token) { return generation.get() == token && !Thread.currentThread().isInterrupted(); }
    private void post(int token, Runnable callback) {
        if (generation.get() == token) main.post(() -> { if (generation.get() == token) callback.run(); });
    }
    private void fail(int token, Exception error) {
        String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        post(token, () -> listener.onFinished(context.getString(R.string.task_error, detail), true));
    }
    private static double dbfs(short[] samples, int count) {
        double sum = 0;
        for (int n = 0; n < count; n++) { double sample = samples[n] / 32768.0; sum += sample * sample; }
        return count == 0 ? -96 : Math.max(-96, 10 * Math.log10(Math.max(1e-12, sum / count)));
    }
    @Override public void close() { stop(); worker.shutdownNow(); main.removeCallbacksAndMessages(null); }
}
