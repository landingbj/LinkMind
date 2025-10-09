package ai.utils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonExtractor {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Extracts JSON strings from a given input string.
     * This method looks for content that appears to be valid JSON objects or arrays.
     *
     * @param input The string that may contain JSON content
     * @return A list of extracted JSON strings
     */
    public static List<String> extractJsonStrings(String input) {
        if (input == null || input.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> jsonStrings = new ArrayList<>();

        // Pattern to find JSON objects (starting with { and ending with })
        Pattern objectPattern = Pattern.compile("\\{(?:[^{}]|(?:\\{[^{}]*\\}))*\\}");
        Matcher objectMatcher = objectPattern.matcher(input);

        while (objectMatcher.find()) {
            jsonStrings.add(objectMatcher.group());
        }
        return jsonStrings;
    }

    public static List<String> extractJsonArrayStrings(String input) {
        if (input == null || input.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> jsonStrings = new ArrayList<>();
        // Pattern to find JSON arrays (starting with [ and ending with ])
        Pattern arrayPattern = Pattern.compile("\\[(?:[^\\[\\]]|(?:\\[[^\\[\\]]*\\]))*\\]");
        Matcher arrayMatcher = arrayPattern.matcher(input);

        while (arrayMatcher.find()) {
            jsonStrings.add(arrayMatcher.group());
        }

        return jsonStrings;
    }

    /**
     * Extract a single JSON string from input. Returns the first match.
     *
     * @param input The string that may contain JSON content
     * @return The first found JSON string or null if none is found
     */
    public static String extractFirstJsonString(String input) {
        List<String> results = extractJsonStrings(input);
        return results.isEmpty() ? null : results.get(0);
    }

    public static String extractFirstJsonArray(String input) {
        List<String> results = extractJsonArrayStrings(input);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 从字符串中提取第一个有效的JSON对象或数组
     *
     * @param input 包含JSON的字符串
     * @return 提取到的有效JSON字符串，如果没有找到则返回null
     */
    public static String extractJson(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        // 寻找JSON的起始位置
        int startIndex = -1;
        char startChar = ' ';
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '{' || c == '[') {
                startIndex = i;
                startChar = c;
                break;
            }
        }

        if (startIndex == -1) {
            return null; // 没有找到JSON起始标记
        }

        // 寻找匹配的结束标记，考虑嵌套情况
        int endIndex = -1;
        char endChar = (startChar == '{') ? '}' : ']';
        int balance = 1; // 括号平衡计数器

        for (int i = startIndex + 1; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == startChar) {
                balance++;
            } else if (c == endChar) {
                balance--;
            }

            // 当平衡计数器为0时，找到匹配的结束标记
            if (balance == 0) {
                endIndex = i;
                break;
            }
        }

        if (endIndex == -1) {
            return null; // 没有找到匹配的结束标记
        }

        // 提取候选JSON字符串
        String candidateJson = input.substring(startIndex, endIndex + 1);

        // 验证提取的字符串是否为有效的JSON
        if (isJson(candidateJson)) {
            return candidateJson;
        } else {
            return null;
        }
    }


    public static boolean isJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return false;
        }

        try {
            // 尝试解析JSON字符串
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            return true;
        } catch (JsonParseException e) {
            // JSON解析异常，不是有效的JSON
            return false;
        } catch (Exception e) {
            // 其他异常，也不是有效的JSON
            return false;
        }
    }

    public static String removeSpaces(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            Object obj = mapper.readValue(json, Object.class);
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Invalid JSON string: " + e.getMessage(), e);
        }
    }

    // Example usage
    public static void main(String[] args) {
        String testString = "Some text before {\"name\": \"John\", \"age\": 30} and some text after. " +
                "Also here's an array [1, 2, 3, 4] and another object {\"city\": \"New York\"}";

        List<String> jsonStrings = extractJsonStrings(testString);
        System.out.println("Found " + jsonStrings.size() + " JSON strings:");
        for (String json : jsonStrings) {
            System.out.println(json);
        }

        List<String> jsonArrayStrings = extractJsonArrayStrings(testString);
        System.out.println("Found " + jsonArrayStrings.size() + " JSON arrays:");
        for (String json : jsonArrayStrings) {
            System.out.println(json);
        }
    }
}
