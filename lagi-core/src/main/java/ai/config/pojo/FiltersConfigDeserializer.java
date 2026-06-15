package ai.config.pojo;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FiltersConfigDeserializer extends JsonDeserializer<FiltersConfig> {
    @Override
    public FiltersConfig deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ObjectCodec codec = parser.getCodec();
        JsonNode root = codec.readTree(parser);
        FiltersConfig config = new FiltersConfig();
        config.setEnable(true);

        JsonNode itemsNode = root;
        if (root != null && root.isObject()) {
            JsonNode enableNode = root.get("enable");
            if (enableNode != null && !enableNode.isNull()) {
                config.setEnable(enableNode.asBoolean(true));
            }
            itemsNode = root.get("items");
        }

        if (itemsNode != null && itemsNode.isArray()) {
            List<FilterConfig> items = new ArrayList<>();
            for (JsonNode itemNode : itemsNode) {
                if (itemNode != null && itemNode.isObject()) {
                    items.add(codec.treeToValue(itemNode, FilterConfig.class));
                }
            }
            config.setItems(items);
        }

        return config;
    }
}
