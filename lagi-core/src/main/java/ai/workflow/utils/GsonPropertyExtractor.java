package ai.workflow.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GsonPropertyExtractor {
    // 匹配数组表达式的正则（如 "b[0]"）
    private static final Pattern ARRAY_PATTERN = Pattern.compile("^([a-zA-Z0-9_$]+)\\[(\\d+)\\]$");
    private static final Gson gson = new Gson();

    /**
     * 根据表达式从JSON字符串中提取属性
     *
     * @param json       JSON字符串
     * @param expression 提取表达式（如 "a.b[0].name"）
     * @return 提取到的属性值（JsonElement类型，可通过getAsString()、getAsInt()等方法转换）
     */
    public static JsonElement extract(String json, String expression) {
        if (json == null || json.trim().isEmpty() || expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON和表达式不能为空");
        }

        JsonElement rootElement = gson.fromJson(json, JsonElement.class);
        return extract(rootElement, expression);
    }

    /**
     * 从已解析的JsonElement中提取属性
     *
     * @param rootElement 解析后的JSON根节点
     * @param expression  提取表达式
     * @return 提取到的属性值
     */
    public static JsonElement extract(JsonElement rootElement, String expression) {
        // 拆分表达式（避免在数组括号内分割，如 "a.b[0].c" 拆分为 ["a", "b[0]", "c"]）
        String[] pathSegments = expression.split("\\.(?![^\\[]*\\])");
        JsonElement currentElement = rootElement;

        for (String segment : pathSegments) {
            Matcher arrayMatcher = ARRAY_PATTERN.matcher(segment);

            if (arrayMatcher.matches()) {
                // 处理数组类型（如 "b[0]"）
                String propName = arrayMatcher.group(1); // 属性名（如 "b"）
                int index = Integer.parseInt(arrayMatcher.group(2)); // 数组索引（如 0）

                // 先获取数组所在的属性节点（必须是JsonObject）
                if (!currentElement.isJsonObject()) {
                    throw new RuntimeException("当前节点不是对象，无法获取属性：" + propName);
                }
                JsonObject currentObj = currentElement.getAsJsonObject();
                JsonElement arrayElement = currentObj.get(propName);

                // 检查数组节点是否存在且为数组
                if (arrayElement == null) {
                    throw new RuntimeException("属性不存在：" + propName);
                }
                if (!arrayElement.isJsonArray()) {
                    throw new RuntimeException("属性不是数组：" + propName);
                }
                JsonArray array = arrayElement.getAsJsonArray();

                // 检查索引合法性
                if (index < 0 || index >= array.size()) {
                    throw new IndexOutOfBoundsException("数组索引越界：" + index + "（数组长度：" + array.size() + "）");
                }

                currentElement = array.get(index);
            } else {
                // 处理普通属性（如 "name"）
                if (!currentElement.isJsonObject()) {
                    throw new RuntimeException("当前节点不是对象，无法获取属性：" + segment);
                }
                JsonObject currentObj = currentElement.getAsJsonObject();
                currentElement = currentObj.get(segment);

                // 检查属性是否存在
                if (currentElement == null) {
                    throw new RuntimeException("属性不存在：" + segment);
                }
            }
        }

        return currentElement;
    }

    // 测试示例
    public static void main(String[] args) {
        // 示例JSON
        String json = "{\n" +
                "  \"a\": {\n" +
                "    \"b\": [\n" +
                "      { \"name\": \"张三\", \"age\": 20 },\n" +
                "      { \"name\": \"李四\", \"age\": 25 }\n" +
                "    ],\n" +
                "    \"c\": { \"d\": \"测试值\" }\n" +
                "  }\n" +
                "}";

        // 测试1：提取数组元素的属性
        JsonElement result1 = extract(json, "a.b[0].name");
        System.out.println("a.b[0].name = " + result1.getAsString()); // 输出：张三

        // 测试2：提取嵌套对象属性
        JsonElement result2 = extract(json, "a.c.d");
        System.out.println("a.c.d = " + result2.getAsString()); // 输出：测试值

        // 测试3：提取数组元素的数字属性
        JsonElement result3 = extract(json, "a.b[1].age");
        System.out.println("a.b[1].age = " + result3.getAsInt()); // 输出：25
    }
}