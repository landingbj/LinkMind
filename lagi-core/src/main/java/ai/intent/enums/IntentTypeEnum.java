package ai.intent.enums;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Getter
public enum IntentTypeEnum {

    IMAGE("image", new String[]{"生成.*?图$",
            "生成.*?图片$",
            "生成.*?图像$",
            "画1张.*?图$",
            "画1张.*?图片$",
            "画1张.*?图像$",
            "画一张.*?图$",
            "画一张.*?图片$",
            "画一张.*?图像$",
            "画.*?张.*?图$",
            "画.*?张.*?图片$",
            "画.*?张.*?图像$"}),
    VIDEO("svd_by_text", new String[]{"生成.*?视频$",
            "生成视频.*?",}),
    TRANSLATE("multilanguage", new String[]{"翻译.*?语",
            "翻译.*?文",
            "语.*?翻译",
            "文.*?翻译",
            "英语.*?",
            "英文.*?"}),
    TIME("time", new String[]{"前天.*?星期几",
            "昨天.*?星期几",
            "今天.*?星期几",
            "明天.*?星期几",
            "后天.*?星期几",
            "前天.*?几号",
            "昨天.*?几号",
            "今天.*?几号",
            "明天.*?几号",
            "后天.*?几号",
            "现在.*?几点",
            "现在.*?几点了",
            "几点了",}),
    TEXT("text", new String[]{}),;


    private final String name;

    private final String[] patterns;

    IntentTypeEnum(String name, String[] patterns) {
        this.name = name;
        this.patterns = patterns;
    }

    public boolean matches(List<String> segments) {
        for (String segment : segments) {
            for (String pattern : patterns) {
                if (Pattern.matches(pattern, segment)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean matches(String instruction, List<String> segments) {

        if(this == IntentTypeEnum.TRANSLATE) {
            int[] lens = englishLengths(instruction);
            int max_en_len = lens[0];
            int total_len = lens[1];
            double ratio = (double) total_len / (double) max_en_len;
            if( ratio > 0.1 && max_en_len > 15) {
                return true;
            }
        }
        for (String segment : segments) {
            for (String pattern : patterns) {
                if (Pattern.matches(pattern, segment)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int[] englishLengths(String s) {
        int maxContinuousLength = 0;
        int currentContinuousLength = 0;
        int totalEnglishLength = 0;

        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);

            if (Character.isLetter(ch) && ch <= 127 || Character.isWhitespace(ch)) { // ASCII 字符范围是 0-127
                currentContinuousLength++;
                totalEnglishLength++;
                maxContinuousLength = Math.max(maxContinuousLength, currentContinuousLength);
            } else {
                currentContinuousLength = 0;
            }
        }

        return new int[]{maxContinuousLength, totalEnglishLength};
    }

    public static void main(String[] args) {
        String ins = "Please help write a poem";
        List<String> ls = new ArrayList<>();
        ls.add("帮忙");
        ls.add("Please help write a poem");
        boolean matches = TRANSLATE.matches(ins,ls);
        System.out.println(matches);
    }

}
