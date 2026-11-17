package xyz.ckkpd.ewsparserapp.core;

import java.time.DateTimeException;
import java.time.LocalDate;

/** Shared by the symbol parser and the lane-deduplicated audio stream. */
final class Confirmation {
    private EwsMessage previous;
    private double position;
    private int count;

    EwsMessage accept(EwsMessage message, double at, double tolerance) {
        int spacing = message.kind == EwsMessage.Kind.END ? 192 : 96;
        boolean consecutive = previous != null && Math.abs(at - position - spacing) <= tolerance
                && compatible(previous, message);
        count = consecutive ? count + 1 : 1;
        previous = message;
        position = at;
        return message.withCount(count);
    }
    private static boolean compatible(EwsMessage a, EwsMessage b) {
        if (a.kind != b.kind || !a.areaCode.equals(b.areaCode)) return false;
        if (sameDate(a, b) && a.hour == b.hour) {
            return a.hourShifted == b.hourShifted && a.dateShifted == b.dateShifted;
        }
        if (a.hourShifted == b.hourShifted) return false;
        return nextHour(a, b) || nextHour(b, a);
    }
    private static boolean sameDate(EwsMessage a, EwsMessage b) {
        return a.yearDigit == b.yearDigit && a.month == b.month && a.day == b.day;
    }
    private static boolean nextHour(EwsMessage a, EwsMessage b) {
        if (sameDate(a, b)) return b.hour == a.hour + 1 && !a.dateShifted && !b.dateShifted;
        if (a.hour != 23 || b.hour != 0 || a.dateShifted == b.dateShifted) return false;
        // The full year is unknown: allow both leap/non-leap calendar alternatives.
        for (int year : new int[]{2000, 2001}) {
            try {
                LocalDate next = LocalDate.of(year, a.month, a.day).plusDays(1);
                int digit = (a.yearDigit + next.getYear() - year) % 10;
                if (b.yearDigit == digit && b.month == next.getMonthValue() && b.day == next.getDayOfMonth()) return true;
            } catch (DateTimeException ignored) { /* February 29 in the non-leap alternative. */ }
        }
        return false;
    }
}
