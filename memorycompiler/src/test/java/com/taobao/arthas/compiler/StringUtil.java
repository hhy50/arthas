package com.taobao.arthas.compiler;

public class StringUtil {
    public static String firstCharUpper(String fieldName) {
        char[] charArray = fieldName.toCharArray();
        char ch = charArray[0];
        charArray[0] = (char) (ch & 0xDF);
        return new String(charArray);
    }
}
