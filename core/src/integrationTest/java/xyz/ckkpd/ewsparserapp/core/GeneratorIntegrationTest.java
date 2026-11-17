package xyz.ckkpd.ewsparserapp.core;

import org.junit.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/** Cross-language WAV integration. Every fixture is checked field-by-field, including both flags. */
public class GeneratorIntegrationTest {
    @Test public void realGeneratorWavsDecodeEveryBodyWithoutLaneDuplicates() throws Exception {
        String directory = System.getProperty("ews.fixtures");
        if (directory == null) throw new IllegalStateException("Run node scripts/generator-check.cjs first; Gradle supplies the fixture path");
        Path root = Path.of(directory);
        Map<String, List<String[]>> cases = new LinkedHashMap<>();
        for (String line : Files.readAllLines(root.resolve("expected.tsv"), StandardCharsets.UTF_8)) {
            String[] fields = line.split("\t");
            assertEquals(11, fields.length);
            cases.computeIfAbsent(fields[0], unused -> new ArrayList<>()).add(fields);
        }
        assertEquals(26, cases.size());
        int bodies = 0;
        for (Map.Entry<String, List<String[]>> entry : cases.entrySet()) {
            List<EwsMessage> messages = new ArrayList<>();
            try (InputStream input = Files.newInputStream(root.resolve(entry.getKey())); WavReader wav = new WavReader(input)) {
                AudioDecoder decoder = new AudioDecoder(wav.getSampleRate(), messages::add);
                short[] buffer = new short[4093];
                int count;
                while ((count = wav.read(buffer, 0, buffer.length)) != -1) decoder.accept(buffer, 0, count);
                decoder.flush();
            }
            String label = entry.getKey();
            assertEquals(label, entry.getValue().size(), messages.size());
            for (int index = 0; index < messages.size(); index++) {
                EwsMessage message = messages.get(index);
                String[] expected = entry.getValue().get(index);
                assertEquals(label, expected[2], message.kind.name());
                assertEquals(label, expected[3], message.areaCode);
                assertEquals(label, Integer.parseInt(expected[4]), message.yearDigit);
                assertEquals(label, Integer.parseInt(expected[5]), message.month);
                assertEquals(label, Integer.parseInt(expected[6]), message.day);
                assertEquals(label, Integer.parseInt(expected[7]), message.hour);
                assertEquals(label, expected[8].equals("1"), message.dateShifted);
                assertEquals(label, expected[9].equals("1"), message.hourShifted);
                assertEquals(label, expected[10], message.bodyBits);
                assertEquals(label, index + 1, message.confirmedBlocks);
                bodies++;
            }
        }
        assertEquals(242, bodies);
    }
}
