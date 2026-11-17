package xyz.ckkpd.ewsparserapp.core;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class CalendarValidationTest {
    @Test public void february29CannotUseAnOddGregorianYearDigit() {
        // A final digit cannot prove a leap year, but an odd digit proves a non-leap year.
        for (String year : new String[]{"10001", "11001", "10101", "11101", "10011"}) {
            String body = SignalFixtures.replace(SignalFixtures.START_BODY, 51, "10111");
            body = SignalFixtures.replace(body, 57, "01001");
            body = SignalFixtures.replace(body, 89, year);
            List<EwsMessage> messages = new ArrayList<>();
            FrameParser parser = new FrameParser(messages::add);
            for (char symbol : ("1100" + body).toCharArray()) parser.accept(symbol);
            assertTrue("odd year code=" + year, messages.isEmpty());
        }
    }
}
