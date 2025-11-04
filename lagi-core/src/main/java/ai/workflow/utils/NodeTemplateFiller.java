package ai.workflow.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Build node JSON by applying minimal overrides onto canonical templates per node type.
 */
public class NodeTemplateFiller {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ObjectNode buildNode(String type, String id, Map<String, Object> overrides) {
        switch (type) {
            case "llm":
                return buildLlmNode(id, overrides);
            case "start":
                return buildStartNode(id, overrides);
            case "end":
                return buildEndNode(id, overrides);
            case "api":
                return buildApiNode(id, overrides);
            case "knowledge-base":
                return buildKnowledgeNode(id, overrides);
            case "intent-recognition":
                return buildIntentNode(id, overrides);
            case "program":
                return buildProgramNode(id, overrides);
            case "asr":
                return buildAsrNode(id, overrides);
            case "image2text":
                return buildImage2TextNode(id, overrides);
            case "image2detect":
                return buildImage2DetectNode(id, overrides);
            case "database-query":
                return buildDatabaseQueryNode(id, overrides);
            case "database-update":
                return buildDatabaseUpdateNode(id, overrides);
            case "condition":
                return buildConditionNode(id, overrides);
            case "loop":
                return buildLoopNode(id, overrides);
            case "parallel":
                return buildParallelNode(id, overrides);
            case "agent":
                return buildAgentNode(id, overrides);
            case "mcp-agent":
                return buildMcpAgentNode(id, overrides);
            default:
                return buildBaseNode(type, id, overrides);
        }
    }

    private static ObjectNode buildBaseNode(String type, String id, Map<String, Object> overrides) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("id", id);
        root.put("type", type);

        ObjectNode meta = MAPPER.createObjectNode();
        ObjectNode position = MAPPER.createObjectNode();
        // default position; can be overridden
        position.put("x", getNumber(overrides, "meta.position.x", 100));
        position.put("y", getNumber(overrides, "meta.position.y", 100));
        meta.set("position", position);
        root.set("meta", meta);

        ObjectNode data = MAPPER.createObjectNode();
        data.put("title", getString(overrides, "data.title", type.toUpperCase() + "_1"));
        root.set("data", data);
        return root;
    }

    private static ObjectNode buildLlmNode(String id, Map<String, Object> overrides) {
        ObjectNode root = buildBaseNode("llm", id, overrides);
        ObjectNode data = (ObjectNode) root.get("data");

        ObjectNode inputsValues = MAPPER.createObjectNode();
        ObjectNode model = MAPPER.createObjectNode();
        model.put("type", getString(overrides, "data.inputsValues.model.type", "ref"));
        JsonNode modelContent = toJsonNode(overrides.getOrDefault("data.inputsValues.model.content", MAPPER.createArrayNode()));
        model.set("content", modelContent);
        inputsValues.set("model", model);

        ObjectNode prompt = MAPPER.createObjectNode();
        prompt.put("type", getString(overrides, "data.inputsValues.prompt.type", "template"));
        prompt.put("content", getString(overrides, "data.inputsValues.prompt.content", ""));
        inputsValues.set("prompt", prompt);
        data.set("inputsValues", inputsValues);

        ObjectNode inputs = MAPPER.createObjectNode();
        inputs.put("type", "object");
        ArrayNode required = MAPPER.createArrayNode();
        required.add("model");
        required.add("prompt");
        inputs.set("required", required);
        ObjectNode props = MAPPER.createObjectNode();
        ObjectNode modelProp = MAPPER.createObjectNode();
        modelProp.put("type", "string");
        props.set("model", modelProp);
        ObjectNode promptProp = MAPPER.createObjectNode();
        promptProp.put("type", "string");
        ObjectNode extra = MAPPER.createObjectNode();
        extra.put("formComponent", "prompt-editor");
        promptProp.set("extra", extra);
        props.set("prompt", promptProp);
        inputs.set("properties", props);
        data.set("inputs", inputs);

        ObjectNode outputs = MAPPER.createObjectNode();
        outputs.put("type", "object");
        ObjectNode outProps = MAPPER.createObjectNode();
        ObjectNode resultProp = MAPPER.createObjectNode();
        resultProp.put("type", "string");
        outProps.set("result", resultProp);
        outputs.set("properties", outProps);
        data.set("outputs", outputs);
        return root;
    }

    private static ObjectNode buildStartNode(String id, Map<String, Object> overrides) {
        return buildBaseNode("start", id, overrides);
    }

    private static ObjectNode buildEndNode(String id, Map<String, Object> overrides) {
        return buildBaseNode("end", id, overrides);
    }

    private static ObjectNode buildApiNode(String id, Map<String, Object> overrides) {
        return buildBaseNode("api", id, overrides);
    }

    private static ObjectNode buildKnowledgeNode(String id, Map<String, Object> overrides) {
        return buildBaseNode("knowledge-base", id, overrides);
    }

    private static ObjectNode buildIntentNode(String id, Map<String, Object> overrides) {
        return buildBaseNode("intent-recognition", id, overrides);
    }

    private static ObjectNode buildProgramNode(String id, Map<String, Object> overrides) {
        return buildBaseNode("program", id, overrides);
    }

    private static ObjectNode buildAsrNode(String id, Map<String, Object> overrides) {
        return buildBaseNode("asr", id, overrides);
    }

    private static ObjectNode buildImage2TextNode(String id, Map<String, Object> overrides) {
        return buildBaseNode("image2text", id, overrides);
    }

    private static ObjectNode buildImage2DetectNode(String id, Map<String, Object> overrides) {
        return buildBaseNode("image2detect", id, overrides);
    }

    private static ObjectNode buildDatabaseQueryNode(String id, Map<String, Object> overrides) {
        return buildBaseNode("database-query", id, overrides);
    }

    private static ObjectNode buildDatabaseUpdateNode(String id, Map<String, Object> overrides) {
        return buildBaseNode("database-update", id, overrides);
    }

    private static ObjectNode buildConditionNode(String id, Map<String, Object> overrides) {
        return buildBaseNode("condition", id, overrides);
    }

    private static ObjectNode buildLoopNode(String id, Map<String, Object> overrides) {
        ObjectNode root = buildBaseNode("loop", id, overrides);
        root.set("blocks", MAPPER.createArrayNode());
        root.set("edges", MAPPER.createArrayNode());
        return root;
    }

    private static ObjectNode buildParallelNode(String id, Map<String, Object> overrides) {
        ObjectNode root = buildBaseNode("parallel", id, overrides);
        root.set("blocks", MAPPER.createArrayNode());
        root.set("edges", MAPPER.createArrayNode());
        return root;
    }

    private static ObjectNode buildAgentNode(String id, Map<String, Object> overrides) {
        return buildBaseNode("agent", id, overrides);
    }

    private static ObjectNode buildMcpAgentNode(String id, Map<String, Object> overrides) {
        return buildBaseNode("mcp-agent", id, overrides);
    }

    private static String getString(Map<String, Object> overrides, String path, String def) {
        Object v = getByPath(overrides, path);
        return v instanceof String ? (String) v : def;
    }

    private static int getNumber(Map<String, Object> overrides, String path, int def) {
        Object v = getByPath(overrides, path);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        return def;
    }

    @SuppressWarnings("unchecked")
    private static Object getByPath(Map<String, Object> map, String path) {
        if (map == null || path == null) return null;
        String[] parts = path.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(part);
            if (current == null) return null;
        }
        return current;
    }

    private static JsonNode toJsonNode(Object value) {
        if (value == null) return MAPPER.nullNode();
        if (value instanceof JsonNode) return (JsonNode) value;
        return MAPPER.valueToTree(value);
    }
}


