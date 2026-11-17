package xyz.ckkpd.ewsparserapp.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class AudioConfirmationTest {
    private List<EwsMessage> decode(String symbols) {
        short[] pcm = SignalFixtures.pcm(symbols, 48000, 5107, 0.32, 0.0, 1.0, 1);
        List<EwsMessage> messages = new ArrayList<>();
        AudioDecoder decoder = new AudioDecoder(48000, messages::add);
        for (int offset = 0; offset < pcm.length; offset += 1021) {
            decoder.accept(pcm, offset, Math.min(1021, pcm.length - offset));
        }
        decoder.flush();
        return messages;
    }

    @Test public void oneValidBodyRemainsAnUnconfirmedCandidate() {
        List<EwsMessage> messages = decode(SignalFixtures.start(1));
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).confirmedBlocks);
    }

    @Test public void damagedBodyBreaksConfirmationContinuity() {
        String damaged = SignalFixtures.flip(SignalFixtures.START_BODY, 35);
        List<EwsMessage> messages = decode(SignalFixtures.start(2) + damaged
                + SignalFixtures.START_BODY.repeat(2));
        assertEquals(4, messages.size());
        assertEquals(1, messages.get(0).confirmedBlocks);
        assertTrue(messages.get(1).confirmedBlocks >= 2);
        assertEquals(1, messages.get(2).confirmedBlocks);
        assertTrue(messages.get(3).confirmedBlocks >= 2);
    }

    @Test public void longSilenceStartsANewConfirmationSequence() {
        List<EwsMessage> messages = decode(SignalFixtures.start(2)
                + ".".repeat(192) + SignalFixtures.start(2));
        assertEquals(4, messages.size());
        assertTrue(messages.get(1).confirmedBlocks >= 2);
        assertEquals(1, messages.get(2).confirmedBlocks);
        assertTrue(messages.get(3).confirmedBlocks >= 2);
    }

    @Test public void differentAreaCannotReuseThePreviousAreasConfirmation() {
        String kanto = SignalFixtures.replace(SignalFixtures.START_BODY, 18, "010110100101");
        List<EwsMessage> messages = decode(SignalFixtures.start(2) + kanto.repeat(2));
        assertEquals(4, messages.size());
        assertEquals(SignalFixtures.COMMON, messages.get(1).areaCode);
        assertTrue(messages.get(1).confirmedBlocks >= 2);
        assertEquals("010110100101", messages.get(2).areaCode);
        assertEquals(1, messages.get(2).confirmedBlocks);
        assertTrue(messages.get(3).confirmedBlocks >= 2);
    }

    @Test public void differentSignalKindCannotReuseThePreviousKindsConfirmation() {
        String first = SignalFixtures.START_BODY.replace(SignalFixtures.FIXED_II, SignalFixtures.FIXED_I);
        List<EwsMessage> messages = decode(SignalFixtures.start(2) + first.repeat(2));
        assertEquals(4, messages.size());
        assertEquals("SECOND", messages.get(1).kind.name());
        assertTrue(messages.get(1).confirmedBlocks >= 2);
        assertEquals("FIRST", messages.get(2).kind.name());
        assertEquals(1, messages.get(2).confirmedBlocks);
        assertTrue(messages.get(3).confirmedBlocks >= 2);
    }

    @Test public void aLargeHourJumpCannotReuseAnOtherwiseMatchingConfirmation() {
        String fourAm = SignalFixtures.replace(SignalFixtures.START_BODY, 83, "00111");
        List<EwsMessage> messages = decode(SignalFixtures.start(2) + fourAm.repeat(2));
        assertEquals(4, messages.size());
        assertTrue(messages.get(1).confirmedBlocks >= 2);
        assertEquals(4, messages.get(2).hour);
        assertEquals(1, messages.get(2).confirmedBlocks);
        assertTrue(messages.get(3).confirmedBlocks >= 2);
    }

    @Test public void unrelatedDateOrYearChangesBreakConfirmationContinuity() {
        String june = SignalFixtures.replace(SignalFixtures.START_BODY, 57, "01101");
        String nextYear = SignalFixtures.replace(SignalFixtures.START_BODY, 89, "10101");
        for (String changed : new String[]{june, nextYear}) {
            List<EwsMessage> messages = decode(SignalFixtures.start(2) + changed.repeat(2));
            assertEquals(4, messages.size());
            assertTrue(messages.get(1).confirmedBlocks >= 2);
            assertEquals(1, messages.get(2).confirmedBlocks);
            assertTrue(messages.get(3).confirmedBlocks >= 2);
        }
    }

    @Test public void adjacentProtectedHoursAcrossNewYearRemainOneConfirmationSequence() {
        String odd = SignalFixtures.FIXED_II + "1000110100110100"
                + SignalFixtures.FIXED_II + "0101000001000100"
                + SignalFixtures.FIXED_II + "0110001100010100";
        String even = SignalFixtures.FIXED_II + "1000110100110100"
                + SignalFixtures.FIXED_II + "0101111110011100"
                + SignalFixtures.FIXED_II + "0111111011100100";
        List<EwsMessage> messages = decode("1100" + odd + even + odd + even);
        assertEquals(4, messages.size());
        for (int index = 0; index < messages.size(); index++) {
            assertEquals(index + 1, messages.get(index).confirmedBlocks);
        }
        assertEquals(4, messages.get(0).yearDigit);
        assertEquals(3, messages.get(1).yearDigit);
        assertTrue(messages.get(1).dateShifted);
        assertTrue(messages.get(1).hourShifted);
    }
}
