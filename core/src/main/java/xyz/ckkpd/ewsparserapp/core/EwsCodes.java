package xyz.ckkpd.ewsparserapp.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Notification No.405, on-air bit order. See docs/protocol.md for provenance. */
public final class EwsCodes {
    private EwsCodes() { }
    static final String[] MONTHS = {"", "10001", "01001", "11001", "00101", "10101", "01101",
            "11101", "00011", "10011", "01011", "11011", "00111"};
    static final String[] HOURS = {"00011", "10011", "01011", "11011", "00111", "10111", "01111", "11111",
            "00001", "10001", "01001", "11001", "00101", "10101", "01101", "11101",
            "00010", "10010", "01010", "11010", "00110", "10110", "01110", "11110"};
    // Indexed by Gregorian final digit; the Showa-code offset is applied once.
    static final String[] YEARS = {"01011", "10001", "01001", "11001", "00101", "10101", "01101", "11101", "00011", "10011"};
    public static final Map<String, String> AREAS;
    static {
        Map<String, String> areas = new LinkedHashMap<>();
        areas.put("001101001101", "地域共通符号");
        areas.put("010110100101", "関東広域圏");
        areas.put("011100101010", "中京広域圏");
        areas.put("100011010101", "近畿広域圏");
        areas.put("011010011001", "鳥取・島根圏");
        areas.put("010101010011", "岡山・香川圏");
        areas.put("000101101011", "北海道");
        areas.put("010001100111", "青森県");
        areas.put("010111010100", "岩手県");
        areas.put("011101011000", "宮城県");
        areas.put("101011000110", "秋田県");
        areas.put("111001001100", "山形県");
        areas.put("000110101110", "福島県");
        areas.put("110001101001", "茨城県");
        areas.put("111000111000", "栃木県");
        areas.put("100110001011", "群馬県");
        areas.put("011001001011", "埼玉県");
        areas.put("000111000111", "千葉県");
        areas.put("101010101100", "東京都");
        areas.put("010101101100", "神奈川県");
        areas.put("010011001110", "新潟県");
        areas.put("010100111001", "富山県");
        areas.put("011010100110", "石川県");
        areas.put("100100101101", "福井県");
        areas.put("110101001010", "山梨県");
        areas.put("100111010010", "長野県");
        areas.put("101001100101", "岐阜県");
        areas.put("101001011010", "静岡県");
        areas.put("100101100110", "愛知県");
        areas.put("001011011100", "三重県");
        areas.put("110011100100", "滋賀県");
        areas.put("010110011010", "京都府");
        areas.put("110010110010", "大阪府");
        areas.put("011001110100", "兵庫県");
        areas.put("101010010011", "奈良県");
        areas.put("001110010110", "和歌山県");
        areas.put("110100100011", "鳥取県");
        areas.put("001100011011", "島根県");
        areas.put("001010110101", "岡山県");
        areas.put("101100110001", "広島県");
        areas.put("101110011000", "山口県");
        areas.put("111001100010", "徳島県");
        areas.put("100110110100", "香川県");
        areas.put("000110011101", "愛媛県");
        areas.put("001011100011", "高知県");
        areas.put("011000101101", "福岡県");
        areas.put("100101011001", "佐賀県");
        areas.put("101000101011", "長崎県");
        areas.put("100010100111", "熊本県");
        areas.put("110010001101", "大分県");
        areas.put("110100011100", "宮崎県");
        areas.put("110101000101", "鹿児島県");
        areas.put("001101110010", "沖縄県");
        AREAS = Collections.unmodifiableMap(areas);
    }
    static int lookup(String[] codes, String value) {
        for (int n = 0; n < codes.length; n++) if (codes[n].equals(value)) return n;
        return -1;
    }
    static int day(String bits) { return Integer.reverse(Integer.parseInt(bits, 2)) >>> 27; }
}

