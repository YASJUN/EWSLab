package xyz.ckkpd.ewsparserapp.core;

import java.util.Random;

/** Independent Notification No. 405 vectors; never calls the production encoder. */
final class SignalFixtures {
    static final String PREFIX = "1100";
    static final String FIXED_II = "1111000110010010";
    static final String FIXED_I = "0000111001101101";
    static final String COMMON = "001101001101";
    // 2024-01-01 16h JST, common area. Published six-word vector:
    // F192 8D34 F192 5044 F192 6214.
    static final String START_BODY = FIXED_II + "1000110100110100"
            + FIXED_II + "0101000001000100" + FIXED_II + "0110001000010100";
    static final String END_BODY = FIXED_I + "0100110100110111"
            + FIXED_I + "1001000001000111" + FIXED_I + "1010001000010111";

    private SignalFixtures() { }

    static String start(int blocks) {
        return PREFIX + START_BODY.repeat(blocks);
    }

    static String end(int blocks) {
        return ("0011" + END_BODY + ".".repeat(92)).repeat(blocks);
    }

    static String replace(String value, int offset, String replacement) {
        return value.substring(0, offset) + replacement + value.substring(offset + replacement.length());
    }

    static String flip(String value, int offset) {
        return replace(value, offset, value.charAt(offset) == '0' ? "1" : "0");
    }

    /** rateRatio changes both symbol and carrier clocks, like a sample-clock error. */
    static short[] pcm(String symbols, int sampleRate, int delaySamples, double amplitude,
                       double noiseAmplitude, double rateRatio, long seed) {
        int signalSamples = (int) Math.ceil(symbols.length() * sampleRate / (64.0 * rateRatio));
        short[] output = new short[delaySamples + signalSamples + sampleRate / 8];
        Random random = new Random(seed);
        double phase = 0.37;
        for (int n = 0; n < output.length; n++) {
            double value = noiseAmplitude * (random.nextDouble() * 2.0 - 1.0);
            int signalIndex = n - delaySamples;
            if (signalIndex >= 0 && signalIndex < signalSamples) {
                int bit = (int) Math.floor(signalIndex * 64.0 * rateRatio / sampleRate);
                if (bit < symbols.length() && symbols.charAt(bit) != '.') {
                    double frequency = symbols.charAt(bit) == '1' ? 1024.0 : 640.0;
                    value += amplitude * Math.sin(phase);
                    phase += 2.0 * Math.PI * frequency * rateRatio / sampleRate;
                }
            }
            output[n] = (short) Math.round(Math.max(-1.0, Math.min(1.0, value)) * 32767.0);
        }
        return output;
    }
}
