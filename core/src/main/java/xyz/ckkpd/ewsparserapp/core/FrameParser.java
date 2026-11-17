package xyz.ckkpd.ewsparserapp.core;

import java.util.Objects;
import java.util.function.Consumer;

/** Sliding bit parser; unknown symbols consume time and invalidate overlapping bodies. */
public final class FrameParser {
    private final Consumer<EwsMessage> listener;
    private final char[] window = new char[100];
    private final Confirmation confirmation = new Confirmation();
    private int size;
    private long symbols;

    public FrameParser(Consumer<EwsMessage> listener) { this.listener = Objects.requireNonNull(listener); }
    public void accept(char symbol) {
        if (symbol != '0' && symbol != '1' && symbol != '.') throw new IllegalArgumentException("Unknown symbol");
        if (size == window.length) System.arraycopy(window, 1, window, 0, --size);
        window[size++] = symbol;
        symbols++;
        if (size < 96) return;
        int start = size - 96;
        String fixed = new String(window, start, 16);
        if (!fixed.equals("0000111001101101") && !fixed.equals("1111000110010010")) return;
        String body = new String(window, start, 96);
        if (body.indexOf('.') >= 0 || !body.regionMatches(32, fixed, 0, 16)
                || !body.regionMatches(64, fixed, 0, 16)) return;
        boolean end = body.startsWith("01", 16);
        String suffix = end ? "11" : "00";
        if (!body.startsWith(end ? "01" : "10", 16) || !body.startsWith(suffix, 30)
                || !body.startsWith(end ? "100" : "010", 48) || !body.startsWith(suffix, 62)
                || !body.startsWith(end ? "101" : "011", 80) || !body.startsWith(suffix, 94)) return;
        if (end && !fixed.equals("0000111001101101")) return;
        String area = body.substring(18, 30);
        if (!EwsCodes.AREAS.containsKey(area)) return;
        int day = EwsCodes.day(body.substring(51, 56));
        int month = EwsCodes.lookup(EwsCodes.MONTHS, body.substring(57, 62));
        int hour = EwsCodes.lookup(EwsCodes.HOURS, body.substring(83, 88));
        int year = EwsCodes.lookup(EwsCodes.YEARS, body.substring(89, 94));
        if (day < 1 || month < 1 || hour < 0 || year < 0) return;
        int[] maxDays = {0, 31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        if (day > maxDays[month]) return;
        boolean dateShifted = body.charAt(56) == '1', hourShifted = body.charAt(88) == '1';
        if (dateShifted && !hourShifted) return;
        EwsMessage.Kind kind = end ? EwsMessage.Kind.END : fixed.charAt(0) == '1'
                ? EwsMessage.Kind.SECOND : EwsMessage.Kind.FIRST;
        String prefix = end ? "0011" : "1100";
        boolean prefixVerified = start >= 4 && prefix.equals(new String(window, start - 4, 4));
        EwsMessage message = new EwsMessage(kind, area, year, month, day, hour,
                dateShifted, hourShifted, prefixVerified, 1, body);
        listener.accept(confirmation.accept(message, symbols, 0));
    }
}
