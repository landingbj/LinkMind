package ai.openai.pojo;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
@Data
public class Parameters {
    private String type;
    private Map<String, Property> properties;
    private List<String> required;
    private Object additionalProperties = false;
    @JsonIgnore
    private final Map<String, Object> schemaExtensions = new LinkedHashMap<>();

    @JsonAnySetter
    public void putSchemaExtension(String key, Object value) {
        schemaExtensions.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> anySchemaExtensions() {
        return schemaExtensions;
    }
}
