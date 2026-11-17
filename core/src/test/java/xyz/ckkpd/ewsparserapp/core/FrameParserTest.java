package xyz.ckkpd.ewsparserapp.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class FrameParserTest {
    private List<EwsMessage> parse(String symbols) {
        List<EwsMessage> messages = new ArrayList<>();
        FrameParser parser = new FrameParser(messages::add);
        for (char symbol : symbols.toCharArray()) parser.accept(symbol);
        return messages;
    }

    @Test public void publishedSixWordVectorDecodesEveryField() {
        List<EwsMessage> messages = parse(SignalFixtures.start(1));
        assertEquals(1, messages.size());
        EwsMessage message = messages.get(0);
        assertEquals("SECOND", message.kind.name());
        assertEquals(SignalFixtures.COMMON, message.areaCode);
        assertEquals(4, message.yearDigit);
        assertEquals(1, message.month);
        assertEquals(1, message.day);
        assertEquals(16, message.hour);
        assertFalse(message.dateShifted);
        assertFalse(message.hourShifted);
        assertTrue(message.prefixVerified);
    }

    @Test public void startPrefixAppearsOnceThenBodiesRemain96SymbolsApart() {
        assertEquals(10, parse(SignalFixtures.start(10)).size());
    }

    @Test public void recoversAfterStartingInTheMiddleOfABlock() {
        List<EwsMessage> messages = parse(SignalFixtures.START_BODY.substring(37)
                + SignalFixtures.START_BODY.repeat(3));
        assertEquals(3, messages.size());
        assertEquals(16, messages.get(0).hour);
        assertFalse(messages.get(0).prefixVerified);
    }

    @Test public void rejectsOneCorruptedFixedCodeAndFindsTheNextBody() {
        String damaged = SignalFixtures.flip(SignalFixtures.START_BODY, 35);
        List<EwsMessage> messages = parse("1100" + damaged + SignalFixtures.START_BODY.repeat(2));
        assertEquals(2, messages.size());
        assertEquals(1, messages.get(0).day);
    }

    @Test public void invalidMonthLookupDoesNotThrowOrLeakAPartialMessage() {
        String damaged = SignalFixtures.replace(SignalFixtures.START_BODY, 57, "00000");
        List<EwsMessage> messages = parse("1100" + damaged + SignalFixtures.START_BODY);
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).month);
    }

    @Test public void rejectsWrongFieldMarkersEvenIfTheFixedCodesAreCorrect() {
        // The original app treats unknown check tokens as the permissive 111 mask.
        String damaged = SignalFixtures.replace(SignalFixtures.START_BODY, 48, "111");
        assertTrue(parse("1100" + damaged).isEmpty());
    }

    @Test public void unknownAudioSymbolInvalidatesOnlyTheAffectedBlock() {
        String damaged = SignalFixtures.replace(SignalFixtures.START_BODY, 70, ".");
        assertEquals(2, parse("1100" + damaged + SignalFixtures.START_BODY.repeat(2)).size());
    }

    @Test public void endSignalIncludes92SilentSlotsAndEveryBlockIsRecovered() {
        List<EwsMessage> messages = parse(SignalFixtures.end(4));
        assertEquals(4, messages.size());
        for (EwsMessage message : messages) {
            assertEquals("END", message.kind.name());
            assertEquals(4, message.yearDigit);
            assertEquals(16, message.hour);
        }
    }

    @Test public void oddAndEvenNewYearBlocksKeepTheirDifferentDatesAndFlags() {
        // Jan 1 2024 00:05: odd 2024-01-01 00h; even 2023-12-31 23h.
        String odd = SignalFixtures.FIXED_II + "1000110100110100"
                + SignalFixtures.FIXED_II + "0101000001000100"
                + SignalFixtures.FIXED_II + "0110001100010100";
        String even = SignalFixtures.FIXED_II + "1000110100110100"
                + SignalFixtures.FIXED_II + "0101111110011100"
                + SignalFixtures.FIXED_II + "0111111011100100";
        List<EwsMessage> messages = parse("1100" + odd + even);
        assertEquals(2, messages.size());
        EwsMessage current = messages.get(0), previous = messages.get(1);
        assertEquals(4, current.yearDigit);
        assertEquals(1, current.month);
        assertEquals(1, current.day);
        assertEquals(0, current.hour);
        assertFalse(current.dateShifted);
        assertFalse(current.hourShifted);
        assertEquals(3, previous.yearDigit);
        assertEquals(12, previous.month);
        assertEquals(31, previous.day);
        assertEquals(23, previous.hour);
        assertTrue(previous.dateShifted);
        assertTrue(previous.hourShifted);
    }

    @Test public void repeatedNoiseAndATruncatedBodyDoNotEmitMessages() {
        assertTrue(parse("01011010010010110101".repeat(100)).isEmpty());
        assertTrue(parse(SignalFixtures.start(1).substring(0, 99)).isEmpty());
    }
}
