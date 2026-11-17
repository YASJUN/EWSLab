package xyz.ckkpd.ewsparserapp.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Field checks independent from the production lookup tables. */
public class FrameValidationTest {
    private List<EwsMessage> parseBody(String body) {
        List<EwsMessage> messages = new ArrayList<>();
        FrameParser parser = new FrameParser(messages::add);
        for (char symbol : ("1100" + body).toCharArray()) parser.accept(symbol);
        return messages;
    }

    private EwsMessage single(String body) {
        List<EwsMessage> messages = parseBody(body);
        assertEquals(1, messages.size());
        return messages.get(0);
    }

    @Test public void firstSignalUsesTheSamePayloadWithoutBecomingAnEndSignal() {
        String first = SignalFixtures.START_BODY.replace(SignalFixtures.FIXED_II, SignalFixtures.FIXED_I);
        EwsMessage message = single(first);
        assertEquals("FIRST", message.kind.name());
        assertEquals(SignalFixtures.COMMON, message.areaCode);
        assertEquals(4, message.yearDigit);
        assertEquals(16, message.hour);
    }

    @Test public void everyFixedCodeBitMustMatchInAllThreeWords() {
        for (int start : new int[]{0, 32, 64}) {
            for (int bit = start; bit < start + 16; bit++) {
                assertTrue("fixed bit=" + bit,
                        parseBody(SignalFixtures.flip(SignalFixtures.START_BODY, bit)).isEmpty());
            }
        }
    }

    @Test public void everyFieldMarkerBitIsValidatedRatherThanMaskedAsUnknown() {
        for (int bit : new int[]{16, 17, 30, 31, 48, 49, 50, 62, 63, 80, 81, 82, 94, 95}) {
            assertTrue("marker bit=" + bit,
                    parseBody(SignalFixtures.flip(SignalFixtures.START_BODY, bit)).isEmpty());
        }
    }

    @Test public void undefinedDateAndHourCodeWordsNeverUseNearestValueGuessing() {
        for (int offset : new int[]{51, 57, 83, 89}) {
            String body = SignalFixtures.replace(SignalFixtures.START_BODY, offset, "00000");
            assertTrue("undefined field at=" + offset, parseBody(body).isEmpty());
        }
    }

    @Test public void unknownAreaCodesAreRejectedInsteadOfReportedAsValidWarnings() {
        for (String code : new String[]{"000000000000", "111111111111"}) {
            String body = SignalFixtures.replace(SignalFixtures.START_BODY, 18, code);
            assertTrue("unknown area=" + code, parseBody(body).isEmpty());
        }
    }

    @Test public void impossibleMonthDayCombinationsAreRejected() {
        String april31 = SignalFixtures.replace(SignalFixtures.START_BODY, 51, "11111");
        april31 = SignalFixtures.replace(april31, 57, "00101");
        assertTrue(parseBody(april31).isEmpty());
        String february30 = SignalFixtures.replace(SignalFixtures.START_BODY, 51, "01111");
        february30 = SignalFixtures.replace(february30, 57, "01001");
        assertTrue(parseBody(february30).isEmpty());
    }

    @Test public void leapDayIsNotRejectedByInventingAFullYearFromTheYearDigit() {
        String leapDay = SignalFixtures.replace(SignalFixtures.START_BODY, 51, "10111");
        leapDay = SignalFixtures.replace(leapDay, 57, "01001");
        EwsMessage message = single(leapDay);
        assertEquals(2, message.month);
        assertEquals(29, message.day);
        assertEquals(4, message.yearDigit);
    }

    @Test public void everyGregorianYearDigitIsDecodedWithoutShowaEraOffset() {
        String[] codes = {"01011", "10001", "01001", "11001", "00101",
                "10101", "01101", "11101", "00011", "10011"};
        for (int digit = 0; digit < codes.length; digit++) {
            String body = SignalFixtures.replace(SignalFixtures.START_BODY, 89, codes[digit]);
            assertEquals("year digit=" + digit, digit, single(body).yearDigit);
        }
    }

    @Test public void all24HourCodesRetainTheirAirOrderBitMapping() {
        String[] codes = {"00011", "10011", "01011", "11011", "00111", "10111",
                "01111", "11111", "00001", "10001", "01001", "11001",
                "00101", "10101", "01101", "11101", "00010", "10010",
                "01010", "11010", "00110", "10110", "01110", "11110"};
        for (int hour = 0; hour < codes.length; hour++) {
            String body = SignalFixtures.replace(SignalFixtures.START_BODY, 83, codes[hour]);
            assertEquals("hour=" + hour, hour, single(body).hour);
        }
    }

    @Test public void all12MonthCodesRetainTheirAirOrderBitMapping() {
        String[] codes = {"10001", "01001", "11001", "00101", "10101", "01101",
                "11101", "00011", "10011", "01011", "11011", "00111"};
        for (int month = 1; month <= codes.length; month++) {
            String body = SignalFixtures.replace(SignalFixtures.START_BODY, 57, codes[month - 1]);
            assertEquals("month=" + month, month, single(body).month);
        }
    }
}
