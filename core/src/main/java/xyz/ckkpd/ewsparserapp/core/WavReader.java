package xyz.ckkpd.ewsparserapp.core;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Bounded streaming RIFF/WAVE PCM16 reader. Stereo is averaged in 32-bit arithmetic. */
public final class WavReader implements AutoCloseable {
    private static final long MAX_RIFF = 256L * 1024 * 1024;
    private final InputStream input;
    private final byte[] buffer = new byte[16384];
    private int sampleRate, channels, alignment;
    private long frameCount, dataRemaining, tailRemaining;
    private boolean closed, finished;

    public WavReader(InputStream input) throws IOException {
        this.input = new BufferedInputStream(Objects.requireNonNull(input), 16384);
        byte[] header = new byte[16];
        fully(header, 12);
        if (!tag(header, 0).equals("RIFF") || !tag(header, 8).equals("WAVE")) throw error("不是 RIFF/WAVE 文件");
        long size = u32(header, 4);
        if (size < 4 || size > MAX_RIFF) throw error("RIFF 长度无效或超过 256 MiB");
        long remaining = size - 4;
        boolean formatSeen = false;
        while (remaining >= 8) {
            fully(header, 8);
            remaining -= 8;
            String name = tag(header, 0);
            long length = u32(header, 4), padded = length + (length & 1);
            if (padded > remaining) throw error("WAV 区块长度超出 RIFF 边界");
            if (name.equals("fmt ")) {
                if (formatSeen || length < 16 || length > 4096) throw error("fmt 区块重复或长度无效");
                fully(header, 16);
                channels = u16(header, 2);
                long rate = u32(header, 4);
                alignment = u16(header, 12);
                if (u16(header, 0) != 1 || u16(header, 14) != 16 || (channels != 1 && channels != 2)) {
                    throw error("仅支持 PCM16 little-endian 单声道或双声道");
                }
                if (rate < 8000 || rate > 192000) throw error("采样率须为 8000..192000 Hz");
                sampleRate = (int) rate;
                if (alignment != channels * 2 || u32(header, 8) != rate * alignment) throw error("WAV 字节率或采样帧对齐错误");
                skip(padded - 16);
                formatSeen = true;
            } else if (name.equals("data")) {
                if (!formatSeen) throw error("data 必须位于 fmt 之后");
                if (length % alignment != 0) throw error("data 包含不完整采样帧");
                frameCount = length / alignment;
                if (frameCount > 600L * sampleRate) throw error("音频超过 10 分钟限制");
                dataRemaining = length;
                tailRemaining = remaining - padded;
                return;
            } else {
                skip(padded);
            }
            remaining -= padded;
        }
        throw error("缺少 data 区块");
    }

    public int getSampleRate() { return sampleRate; }
    public int getChannels() { return channels; }
    public long getFrameCount() { return frameCount; }

    public int read(short[] mono, int offset, int length) throws IOException {
        Objects.requireNonNull(mono);
        if (offset < 0 || length < 0 || offset > mono.length - length) throw new IndexOutOfBoundsException();
        if (closed) throw error("WAV 已关闭");
        if (length == 0) return 0;
        if (dataRemaining == 0) {
            finish();
            return -1;
        }
        int frames = (int) Math.min(length, Math.min(dataRemaining, buffer.length) / alignment);
        int bytes = frames * alignment;
        fully(buffer, bytes);
        dataRemaining -= bytes;
        for (int n = 0; n < frames; n++) {
            int position = n * alignment;
            int value = (short) u16(buffer, position);
            if (channels == 2) value = (value + (short) u16(buffer, position + 2)) / 2;
            mono[offset + n] = (short) value;
        }
        return frames;
    }

    private void finish() throws IOException {
        if (finished) return;
        byte[] header = new byte[8];
        while (tailRemaining > 0) {
            if (tailRemaining < 8) throw error("RIFF 尾部区块不完整");
            fully(header, 8);
            tailRemaining -= 8;
            long length = u32(header, 4);
            long padded = length + (length & 1);
            if (padded > tailRemaining) throw error("RIFF 尾部区块长度无效");
            if (tag(header, 0).equals("data") || tag(header, 0).equals("fmt ")) throw error("不支持多个 data 或 fmt 区块");
            skip(padded);
            tailRemaining -= padded;
        }
        finished = true;
    }

    private void fully(byte[] output, int length) throws IOException {
        int position = 0;
        while (position < length) {
            int read = input.read(output, position, length - position);
            if (read < 0) throw new EOFException("WAV 文件截断");
            if (read == 0) { // Guard legal but unusual short/zero-reading providers.
                int value = input.read();
                if (value < 0) throw new EOFException("WAV 文件截断");
                output[position++] = (byte) value;
            } else position += read;
        }
    }

    private void skip(long length) throws IOException {
        while (length > 0) {
            int count = (int) Math.min(length, buffer.length);
            fully(buffer, count);
            length -= count;
        }
    }
    private static String tag(byte[] b, int at) { return new String(b, at, 4, StandardCharsets.US_ASCII); }
    private static int u16(byte[] b, int at) { return (b[at] & 255) | ((b[at + 1] & 255) << 8); }
    private static long u32(byte[] b, int at) { return u16(b, at) | ((long) u16(b, at + 2) << 16); }
    private static IOException error(String reason) { return new IOException(reason); }
    @Override public void close() throws IOException {
        closed = true;
        input.close();
    }
}
