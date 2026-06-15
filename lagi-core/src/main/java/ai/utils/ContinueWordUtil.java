package ai.utils;



import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ContinueWordUtil {

    private static final List<String> patterns = new ArrayList<>();

    static {
        List<String> wordList = JsonFileLoadUtil.readWordListJson("/continue_word.json");
        addWords(wordList);
    }

    public static void addWords(List<String> wordList) {
        if (wordList != null) {
            patterns.addAll(wordList);
        }
    }

    public static void clearWords() {
        patterns.clear();
    }

    public static void reloadWords(List<String> words) {
        clearWords();
        addWords(words);
    }

    public static boolean containsStoppingWorlds(String msg) {
        if (msg == null || msg.trim().isEmpty()) {
            return false;
        }
        msg = msg.trim();
        for(String pattern : patterns) {
            Pattern p = Pattern.compile(pattern);
            Matcher matcher = p.matcher(msg);
            if(matcher.find()) {
                FilterMonitorUtil.recordFilterAction("continue", "match", 
                    "匹配继续词规则: " + pattern + ", 内容: " + (msg.length() > 500 ? msg.substring(0, 500) : msg));
                return true;
            }
        }
        return false;
    }


}
