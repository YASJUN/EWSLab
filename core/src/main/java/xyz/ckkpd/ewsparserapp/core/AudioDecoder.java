package xyz.ckkpd.ewsparserapp.core;

import java.util.Objects;
import java.util.function.Consumer;

/** Streaming 64-baud FSK receiver with eight continuously searched symbol phases.
 * Not thread-safe: one instance belongs to one capture/import session.
 */
public final class AudioDecoder {
    private static final int PHASES = 8;
    private final int sampleRate;
    private final double period;
    private final Lane[] lanes = new Lane[PHASES];
    private final Consumer<EwsMessage> listener;
    private final Confirmation confirmation = new Confirmation();
    private long samples;
    private Candidate pending;
    private double pendingStart;
    private long lastEmitted = Long.MIN_VALUE / 2;
    private boolean flushed;

    public AudioDecoder(int sampleRate, Consumer<EwsMessage> listener) {
        if (sampleRate < 8000 || sampleRate > 192000) throw new IllegalArgumentException("Sample rate must be 8000..192000 Hz");
        this.sampleRate = sampleRate;
        this.period = sampleRate / 64.0;
        this.listener = Objects.requireNonNull(listener);
        for (int n = 0; n < PHASES; n++) lanes[n] = new Lane(n / (double) PHASES);
    }

    public void accept(short[] input, int offset, int length) {
        Objects.requireNonNull(input);
        if (offset < 0 || length < 0 || offset > input.length - length) throw new IndexOutOfBoundsException();
        if (flushed) throw new IllegalStateException("Decoder already flushed");
        for (int i = offset; i < offset + length; i++) {
            double value = input[i] / 32768.0;
            for (Lane lane : lanes) lane.accept(value);
            samples++;
            if (pending != null && samples - pendingStart > period * 1.25) emit();
        }
    }

    /** Delivers a completed pending body; never synthesizes missing audio or bits. */
    public void flush() {
        if (pending != null) emit();
        flushed = true;
    }

    public long getSamplesProcessed() { return samples; }

    private void candidate(EwsMessage message, double quality) {
        long at = samples + 1;
        if (at - lastEmitted < period * 2) return;
        if (pending == null) {
            pendingStart = at;
            pending = new Candidate(message, quality, at);
        } else if (at - pendingStart <= period * 1.25 && quality > pending.quality) {
            pending = new Candidate(message, quality, at);
        }
    }

    private void emit() {
        Candidate result = pending;
        pending = null;
        lastEmitted = result.at;
        listener.accept(confirmation.accept(result.message, result.at / period, 2.0));
    }

    private static final class Candidate {
        final EwsMessage message;
        final double quality;
        final long at;
        Candidate(EwsMessage message, double quality, long at) {
            this.message = message; this.quality = quality; this.at = at;
        }
    }

    private final class Lane {
        final double phase, coefficient0, coefficient1;
        final FrameParser parser;
        final double[] qualities = new double[96];
        long symbol;
        long start, end;
        int n, qualityIndex;
        double zero1, zero2, one1, one2, energy, qualitySum;

        Lane(double phase) {
            this.phase = phase;
            coefficient0 = 2 * Math.cos(2 * Math.PI * 640 / sampleRate);
            coefficient1 = 2 * Math.cos(2 * Math.PI * 1024 / sampleRate);
            start = Math.round(phase * period);
            end = Math.round((1 + phase) * period);
            parser = new FrameParser(message -> candidate(message, qualitySum / 96));
        }

        void accept(double value) {
            if (samples < start) return;
            double next = value + coefficient0 * zero1 - zero2;
            zero2 = zero1; zero1 = next;
            next = value + coefficient1 * one1 - one2;
            one2 = one1; one1 = next;
            energy += value * value;
            n++;
            if (samples + 1 < end) return;
            double p0 = Math.max(0, zero1 * zero1 + zero2 * zero2 - coefficient0 * zero1 * zero2);
            double p1 = Math.max(0, one1 * one1 + one2 * one2 - coefficient1 * one1 * one2);
            double high = Math.max(p0, p1), low = Math.min(p0, p1);
            double quality = energy == 0 ? 0 : Math.min(1, 2 * high / (n * energy));
            char bit = energy / n > 1e-8 && quality >= 0.50 && high > 3 * low
                    ? (p1 > p0 ? '1' : '0') : '.';
            qualitySum += quality - qualities[qualityIndex];
            qualities[qualityIndex] = quality;
            qualityIndex = (qualityIndex + 1) % qualities.length;
            parser.accept(bit);
            zero1 = zero2 = one1 = one2 = energy = 0;
            n = 0;
            symbol++;
            start = end;
            // Round absolute fractional boundaries, never truncate the per-bit length.
            end = Math.round((symbol + 1 + phase) * period);
        }
    }
}
