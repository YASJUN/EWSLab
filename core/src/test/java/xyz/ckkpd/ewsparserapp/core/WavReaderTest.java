package xyz.ckkpd.ewsparserapp.core;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static org.junit.Assert.*;

public class WavReaderTest {
    private static void le16(ByteArrayOutputStream out, int value) {
        out.write(value & 255);
        out.write((value >>> 8) & 255);
    }

    private static void le32(ByteArrayOutputStream out, long value) {
        for (int shift = 0; shift < 32; shift += 8) out.write((int) (value >>> shift) & 255);
    }

    private static byte[] bytes(String ascii) {
        return ascii.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static byte[] join(byte[]... values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] value : values) out.write(value, 0, value.length);
        return out.toByteArray();
    }

    private static byte[] chunk(String tag, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes(tag), 0, 4);
        le32(out, data.length);
        out.write(data, 0, data.length);
        if (data.length % 2 != 0) out.write(0);
        return out.toByteArray();
    }

    private static byte[] fmt(int rate, int channels, int format, int bits, int align) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        le16(out, format);
        le16(out, channels);
        le32(out, rate);
        le32(out, (long) rate * align);
        le16(out, align);
        le16(out, bits);
        return chunk("fmt ", out.toByteArray());
    }

    private static byte[] riff(byte[]... chunks) {
        byte[] body = join(chunks);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes("RIFF"), 0, 4);
        le32(out, body.length + 4L);
        out.write(bytes("WAVE"), 0, 4);
        out.write(body, 0, body.length);
        return out.toByteArray();
    }

    private static byte[] samples(int... values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int value : values) le16(out, value);
        return out.toByteArray();
    }

    private static byte[] wav(int rate, int channels, int... values) {
        return riff(fmt(rate, channels, 1, 16, channels * 2), chunk("data", samples(values)));
    }

    private static void consume(byte[] data) throws IOException {
        try (WavReader reader = new WavReader(new ByteArrayInputStream(data))) {
            short[] buffer = new short[23];
            while (reader.read(buffer, 0, buffer.length) != -1) { }
        }
    }

    @Test public void monoPcmRetainsSigned16BitSamplesAndHonorsDestinationOffset() throws Exception {
        try (WavReader reader = new WavReader(new ByteArrayInputStream(
                wav(44100, 1, 0, 32767, -32768, 1234, -5678)))) {
            assertEquals(44100, reader.getSampleRate());
            assertEquals(1, reader.getChannels());
            assertEquals(5L, reader.getFrameCount());
            short[] output = new short[7];
            Arrays.fill(output, (short) 99);
            assertEquals(3, reader.read(output, 2, 3));
            assertArrayEquals(new short[]{99, 99, 0, 32767, -32768, 99, 99}, output);
            assertEquals(2, reader.read(output, 0, output.length));
            assertEquals(1234, output[0]);
            assertEquals(-5678, output[1]);
            assertEquals(-1, reader.read(output, 0, output.length));
        }
    }

    @Test public void stereoFramesAreAveragedWithoutOverflowAndNotCountedAsDoubleDuration() throws Exception {
        try (WavReader reader = new WavReader(new ByteArrayInputStream(
                wav(48000, 2, 12000, -4000, -16000, -8000, 32766, 0)))) {
            short[] output = new short[3];
            assertEquals(2, reader.getChannels());
            assertEquals(3L, reader.getFrameCount());
            assertEquals(3, reader.read(output, 0, output.length));
            assertArrayEquals(new short[]{4000, -12000, 16383}, output);
            assertEquals(-1, reader.read(output, 0, output.length));
        }
    }

    @Test public void paddedOddUnknownChunksAreSkipped() throws Exception {
        byte[] file = riff(chunk("JUNK", new byte[]{12, 34, 56}),
                fmt(44100, 1, 1, 16, 2), chunk("LIST", new byte[]{1}),
                chunk("data", samples(4567)));
        try (WavReader reader = new WavReader(new ByteArrayInputStream(file))) {
            short[] output = new short[1];
            assertEquals(1, reader.read(output, 0, 1));
            assertEquals(4567, output[0]);
        }
    }

    @Test public void shortUnderlyingReadsDoNotTruncateSamples() throws Exception {
        InputStream input = new ByteArrayInputStream(wav(44100, 1, 100, 200, 300, 400)) {
            @Override public synchronized int read(byte[] buffer, int offset, int length) {
                return super.read(buffer, offset, Math.min(length, 3));
            }
        };
        try (WavReader reader = new WavReader(input)) {
            short[] output = new short[4];
            assertEquals(4, reader.read(output, 0, output.length));
            assertArrayEquals(new short[]{100, 200, 300, 400}, output);
        }
    }

    @Test public void emptyDataImmediatelyReturnsEndOfStream() throws Exception {
        try (WavReader reader = new WavReader(new ByteArrayInputStream(wav(8000, 1)))) {
            assertEquals(0L, reader.getFrameCount());
            assertEquals(-1, reader.read(new short[1], 0, 1));
        }
    }

    @Test public void sampleRateBoundsAreInclusive() throws Exception {
        consume(wav(8000, 1, 1));
        consume(wav(192000, 1, 1));
        assertThrows(IOException.class, () -> consume(wav(7999, 1, 1)));
        assertThrows(IOException.class, () -> consume(wav(192001, 1, 1)));
    }

    @Test public void compressedFloatWrongBitDepthAndUnsupportedChannelsAreRejected() {
        for (byte[] format : new byte[][]{
                fmt(44100, 1, 3, 32, 4), fmt(44100, 1, 6, 16, 2),
                fmt(44100, 1, 1, 8, 1), fmt(44100, 3, 1, 16, 6)}) {
            assertThrows(IOException.class, () -> consume(riff(format, chunk("data", samples(1, 2, 3)))));
        }
    }

    @Test public void inconsistentBlockAlignmentAndPartialStereoFramesAreRejected() {
        assertThrows(IOException.class, () -> consume(riff(fmt(44100, 2, 1, 16, 2),
                chunk("data", samples(1, 2)))));
        assertThrows(IOException.class, () -> consume(riff(fmt(44100, 2, 1, 16, 4),
                chunk("data", samples(1)))));
    }

    @Test public void dataBeforeFormatIsRejected() {
        assertThrows(IOException.class, () -> consume(riff(chunk("data", samples(1)),
                fmt(44100, 1, 1, 16, 2))));
    }

    @Test public void truncatedHeaderOrSamplePayloadRaisesAnIoError() {
        assertThrows(IOException.class, () -> consume(bytes("RIFF")));
        byte[] complete = wav(44100, 1, 100, 200, 300);
        assertThrows(IOException.class, () -> consume(Arrays.copyOf(complete, complete.length - 1)));
    }

    @Test public void oddUnknownChunkWithoutItsPaddingIsRejected() {
        byte[] malformed = join(bytes("JUNK"), new byte[]{1, 0, 0, 0}, new byte[]{7});
        assertThrows(IOException.class, () -> consume(riff(malformed,
                fmt(44100, 1, 1, 16, 2), chunk("data", samples(1)))));
    }

    @Test public void oversizedRiffIsRejectedWithoutAllocatingItsDeclaredSize() {
        byte[] file = wav(44100, 1, 1);
        long size = 256L * 1024 * 1024 + 1;
        for (int n = 0; n < 4; n++) file[4 + n] = (byte) (size >>> (n * 8));
        assertThrows(IOException.class, () -> consume(file));
    }

    @Test public void moreThanTenMinutesOfAudioIsRejectedFromHeaderAlone() {
        byte[] file = wav(8000, 1, 1);
        long dataLength = (600L * 8000 + 1) * 2;
        for (int n = 0; n < 4; n++) {
            file[4 + n] = (byte) ((dataLength + 36) >>> (n * 8));
            file[40 + n] = (byte) (dataLength >>> (n * 8));
        }
        assertThrows(IOException.class, () -> new WavReader(new ByteArrayInputStream(file)));
    }

    @Test public void closeAlsoClosesTheUnderlyingInput() throws Exception {
        final boolean[] closed = {false};
        InputStream input = new ByteArrayInputStream(wav(44100, 1, 1)) {
            @Override public void close() throws IOException {
                closed[0] = true;
                super.close();
            }
        };
        WavReader reader = new WavReader(input);
        reader.close();
        assertTrue(closed[0]);
    }
}
