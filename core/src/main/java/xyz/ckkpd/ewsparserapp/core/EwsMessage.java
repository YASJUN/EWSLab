package xyz.ckkpd.ewsparserapp.core;

/** An intact 96-bit body. Confirmation is reception evidence, not a CRC. */
public final class EwsMessage {
    public enum Kind { FIRST, SECOND, END }
    public final Kind kind;
    public final String areaCode;
    public final int yearDigit, month, day, hour;
    public final boolean dateShifted, hourShifted, prefixVerified;
    public final int confirmedBlocks;
    public final String bodyBits;

    EwsMessage(Kind kind, String areaCode, int yearDigit, int month, int day, int hour,
               boolean dateShifted, boolean hourShifted, boolean prefixVerified,
               int confirmedBlocks, String bodyBits) {
        this.kind = kind;
        this.areaCode = areaCode;
        this.yearDigit = yearDigit;
        this.month = month;
        this.day = day;
        this.hour = hour;
        this.dateShifted = dateShifted;
        this.hourShifted = hourShifted;
        this.prefixVerified = prefixVerified;
        this.confirmedBlocks = confirmedBlocks;
        this.bodyBits = bodyBits;
    }

    EwsMessage withCount(int count) {
        return new EwsMessage(kind, areaCode, yearDigit, month, day, hour,
                dateShifted, hourShifted, prefixVerified, count, bodyBits);
    }
}
