package com.github.tvbox.osc.util;

/**
 * 拼音首字母工具: 汉字 -> 拼音首字母 (GB2312 区位码分段算法, 离线可用)
 * 例: "无职转生" -> "wzzs"
 */
public class PinyinUtil {

    // GB2312 汉字区段起始码 -> 首字母(标准分段表, 覆盖一级/二级汉字)
    private static final int[] BOUNDS = {
            0xB0A1, 0xB0C5, 0xB2C1, 0xB4EE, 0xB6EA, 0xB7A2, 0xB8C1, 0xB9FE,
            0xBBF7, 0xBFA6, 0xC0AC, 0xC2E8, 0xC4C3, 0xC5B6, 0xC5BE, 0xC6DA,
            0xC8BB, 0xC8F6, 0xCBFA, 0xCDDA, 0xCEF4, 0xD1B9, 0xD4D1
    };
    private static final char[] LETTERS = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
            'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q',
            'R', 'S', 'T', 'W', 'X', 'Y', 'Z'
    };

    /**
     * 取单个汉字的拼音首字母(大写), 非汉字返回0
     */
    public static char getFirstLetter(char c) {
        if (c < 128) return 0; // ASCII交给调用方处理
        try {
            byte[] bytes = String.valueOf(c).getBytes("GB2312");
            if (bytes == null || bytes.length < 2) return 0;
            int code = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
            if (code < 0xB0A1 || code > 0xD7F9) return 0;
            for (int i = 0; i < BOUNDS.length; i++) {
                if (code < BOUNDS[i]) {
                    return LETTERS[i - 1];
                }
            }
            return 'Z';
        } catch (Throwable th) {
            return 0;
        }
    }

    /**
     * 取字符串的拼音首字母(小写), 英文/数字原样保留, 其他符号忽略
     * 例: "无职转生" -> "wzzs", "复仇者联盟4" -> "fczlm4"
     */
    public static String getFirstLetters(String str) {
        if (str == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                sb.append(Character.toLowerCase(c));
            } else if (c >= '0' && c <= '9') {
                sb.append(c);
            } else if (c >= 128) {
                char letter = getFirstLetter(c);
                if (letter != 0) {
                    sb.append(Character.toLowerCase(letter));
                }
            }
            // ASCII 标点/空格: 忽略(便于拼音连续匹配)
        }
        return sb.toString();
    }
}