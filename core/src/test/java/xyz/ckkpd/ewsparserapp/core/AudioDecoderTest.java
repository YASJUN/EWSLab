package xyz.ckkpd.ewsparserapp.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class AudioDecoderTest {
    private List<EwsMessage> decode(short[] samples, int rate, int chunk) {
        List<EwsMessage> messages = new ArrayList<>();
        AudioDecoder decoder = new AudioDecoder(rate, messages::add);
        for (int offset = 0; offset < samples.length; offset += chunk) {
            decoder.accept(samples, offset, Math.min(chunk, samples.length - offset));
        }
        decoder.flush();
        return messages;
    }

    private void assertCorrectStarts(List<EwsMessage> messages, int expected, String label) {
        assertEquals(label, expected, messages.size());
        for (EwsMessage message : messages) {
            assertEquals(label, "SECOND", message.kind.name());
            assertEquals(label, SignalFixtures.COMMON, message.areaCode);
            assertEquals(label, 4, message.yearDigit);
            assertEquals(label, 1, message.month);
            assertEquals(label, 1, message.day);
            assertEquals(label, 16, message.hour);
        }
        assertTrue(label + " needs two-block confirmation", messages.get(expected - 1).confirmedBlocks >= 2);
    }

    @Test public void arbitrary48kCaptureOffsetsRecoverAllBodiesWithoutDuplicateLaneEvents() {
        for (int offset : new int[]{0, 1, 150, 374, 749}) {
            short[] pcm = SignalFixtures.pcm(SignalFixtures.start(4), 48000,
                    4800 + offset, 0.32, 0.0, 1.0, 1);
            assertCorrectStarts(decode(pcm, 48000, 997), 4, "offset=" + offset);
        }
    }

    @Test public void fractional44100SampleClockDoesNotTruncateEverySymbol() {
        for (int offset : new int[]{0, 344, 688}) {
            short[] pcm = SignalFixtures.pcm(SignalFixtures.start(10), 44100,
                    4410 + offset, 0.32, 0.0, 1.0, 1);
            assertCorrectStarts(decode(pcm, 44100, 4096), 10, "44.1k offset=" + offset);
        }
    }

    @Test public void lowAmplitudeTonalSignalIsNotRejectedByAnAbsoluteGoertzelThreshold() {
        short[] pcm = SignalFixtures.pcm(SignalFixtures.start(4), 48000,
                5311, 0.008, 0.0, 1.0, 1);
        assertCorrectStarts(decode(pcm, 48000, 751), 4, "low amplitude");
    }

    @Test public void seededBroadbandNoiseDoesNotChangeThePayload() {
        short[] pcm = SignalFixtures.pcm(SignalFixtures.start(4), 48000,
                5513, 0.18, 0.035, 1.0, 4567);
        assertCorrectStarts(decode(pcm, 48000, 1024), 4, "seeded noise");
    }

    @Test public void silenceBetweenEndBlocksAdvancesSymbolClock() {
        short[] pcm = SignalFixtures.pcm(SignalFixtures.end(4), 44100,
                4433, 0.32, 0.0, 1.0, 1);
        List<EwsMessage> messages = decode(pcm, 44100, 4093);
        assertEquals(4, messages.size());
        for (EwsMessage message : messages) {
            assertEquals("END", message.kind.name());
            assertEquals(16, message.hour);
        }
        assertTrue(messages.get(3).confirmedBlocks >= 2);
    }

    @Test public void streamChunkBoundariesCannotChangeDecodedMessages() {
        short[] pcm = SignalFixtures.pcm(SignalFixtures.start(3), 44100,
                5013, 0.32, 0.0, 1.0, 1);
        List<EwsMessage> baseline = decode(pcm, 44100, pcm.length);
        assertCorrectStarts(baseline, 3, "whole buffer");
        for (int chunk : new int[]{1, 257, 1000, 4096}) {
            List<EwsMessage> messages = decode(pcm, 44100, chunk);
            assertCorrectStarts(messages, 3, "chunk=" + chunk);
            for (int index = 0; index < messages.size(); index++) {
                assertEquals(baseline.get(index).confirmedBlocks, messages.get(index).confirmedBlocks);
            }
        }
    }

    @Test public void continuousPhaseSearchToleratesPlusOrMinus1000ppmClockError() {
        for (double ratio : new double[]{0.999, 1.001}) {
            short[] pcm = SignalFixtures.pcm(SignalFixtures.start(10), 48000,
                    5107, 0.32, 0.0, ratio, 1);
            assertCorrectStarts(decode(pcm, 48000, 1021), 10, "clock ratio=" + ratio);
        }
    }

    @Test public void brokenFirstBlockDoesNotPreventLaterGoodBlocksFromSynchronizing() {
        String symbols = "1100" + SignalFixtures.flip(SignalFixtures.START_BODY, 35)
                + SignalFixtures.START_BODY.repeat(3);
        short[] pcm = SignalFixtures.pcm(symbols, 48000, 5251, 0.32, 0.0, 1.0, 1);
        assertCorrectStarts(decode(pcm, 48000, 2048), 3, "damaged first block");
    }

    @Test public void silenceNoiseAndSingleCarrierNeverBecomeConfirmedWarnings() {
        short[] noise = SignalFixtures.pcm(".".repeat(500), 48000, 5237, 0.0, 0.08, 1.0, 9876);
        assertTrue(decode(noise, 48000, 1024).isEmpty());
        short[] carrier = SignalFixtures.pcm("1".repeat(500), 44100, 4433, 0.32, 0.0, 1.0, 1);
        assertTrue(decode(carrier, 44100, 1024).isEmpty());
    }
}
