package xczl.recursivecraft.utils;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import xczl.recursivecraft.RecursiveCraft;

public class PinyinUtils {

    // 预配置一个格式化对象：输出小写、无声调
    private static final HanyuPinyinOutputFormat FORMAT = new HanyuPinyinOutputFormat();

    static {
        FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    /**
     * 获取字符串的拼音首字母 (例如 "石头" -> "st")
     */
    public static String toInitials(String input) {
        if (input == null || input.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();

        for (char c : input.toCharArray()) {
            try {
                // 判断是否为汉字 (Pinyin4j 的方法)
                String[] pinyins = PinyinHelper.toHanyuPinyinStringArray(c, FORMAT);
                if (pinyins != null && pinyins.length > 0) {
                    // 取第一个读音的首字母
                    sb.append(pinyins[0].charAt(0));
                } else {
                    // 非汉字保持原样
                    sb.append(c);
                }
            } catch (Exception e) {
                // 忽略转换错误，保留原字符
                sb.append(c);
            }
        }
        return sb.toString().toLowerCase();
    }

    /**
     * 获取全拼，每个字之间用空格隔开 (例如 "石头" -> "shi tou")
     */
    public static String toFullPinyin(String input) {
        if (input == null || input.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            try {
                String[] pinyins = PinyinHelper.toHanyuPinyinStringArray(c, FORMAT);
                if (pinyins != null && pinyins.length > 0) {
                    sb.append(pinyins[0]); // 取第一个读音
                    sb.append(" "); // 加空格
                } else {
                    sb.append(c);
                }
            } catch (Exception e) {
                sb.append(c);
            }
        }
        return sb.toString().trim().toLowerCase();
    }

    /**
     * 全能匹配逻辑
     */
    public static boolean matches(String itemName, String query) {
        if (query == null || query.isEmpty()) return true;

        String lowerQuery = query.toLowerCase().trim();
        String lowerName = itemName.toLowerCase();

        // 1. 原文匹配
        if (lowerName.contains(lowerQuery)) return true;

        // 2. 拼音首字母匹配
        if (toInitials(itemName).contains(lowerQuery)) return true;

        // 3. 全拼匹配
        String fullPinyinWithSpace = toFullPinyin(itemName); // "shi tou"

        // 3.1 带空格匹配
        if (fullPinyinWithSpace.contains(lowerQuery)) return true;

        // 3.2 连写匹配 (移除空格)
        if (fullPinyinWithSpace.replace(" ", "").contains(lowerQuery)) return true;

        return false;
    }
}