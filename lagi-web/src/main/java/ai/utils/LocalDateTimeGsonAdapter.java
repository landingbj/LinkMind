package ai.utils;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * LocalDateTime的Gson序列化/反序列化适配器
 * 解决默认序列化出现date/time嵌套结构的问题
 */
public class LocalDateTimeGsonAdapter implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {

    // 定义业务需要的日期时间格式（可根据需求调整，比如ISO格式、带时区格式等）
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 序列化：把LocalDateTime转成指定格式的字符串
     */
    @Override
    public JsonElement serialize(LocalDateTime localDateTime, Type type, JsonSerializationContext context) {
        // 空值处理，避免空指针异常
        if (localDateTime == null) {
            return JsonNull.INSTANCE;
        }
        return new JsonPrimitive(FORMATTER.format(localDateTime));
    }

    /**
     * 反序列化：把JSON字符串转回LocalDateTime
     */
    @Override
    public LocalDateTime deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) throws JsonParseException {
        // 空值处理
        if (jsonElement == null || jsonElement.isJsonNull()) {
            return null;
        }
        try {
            return LocalDateTime.parse(jsonElement.getAsString(), FORMATTER);
        } catch (Exception e) {
            throw new JsonParseException("解析LocalDateTime失败，期望格式：yyyy-MM-dd HH:mm:ss", e);
        }
    }
}
