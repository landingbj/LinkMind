package ai.workflow.tools.impl;

import ai.openai.pojo.Tool;
import ai.utils.ResourceUtil;
import ai.workflow.tools.AgentTool;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;


public class GenerateEdgesTool implements AgentTool {

    @Override
    public String getName() {
        return "generate_start_node";
    }

    @Override
    public Tool getTool() {
        String s = ResourceUtil.loadAsString("/tools/generate_end_node_tool.json");
        Gson gson = new Gson();
        return gson.fromJson(s, Tool.class);
    }

    @Override
    public String invoke(String jsonStr) {
        Gson gson = new Gson();
        JsonObject endNode = new JsonObject();
        JsonObject jsonObject = gson.fromJson(jsonStr, JsonObject.class);
        String id = jsonObject.get("nodeId").getAsString();
        endNode.addProperty("id", id);
        endNode.addProperty("type", "end");
        JsonObject meta = new JsonObject();
        endNode.add("meta", meta);
        JsonObject position = jsonObject.getAsJsonObject("position");
        meta.add("position",  position);
        JsonObject data = new JsonObject();
        endNode.add("data",  data);
        data.addProperty("title", "结束");
        JsonObject inputs = new JsonObject();
        data.add("inputs", inputs);
        JsonArray outputProperties = jsonObject.getAsJsonArray("inputsProperties");
        outputProperties.forEach(outputProperty -> {
            inputs.add(outputProperty.getAsJsonObject().get("name").getAsString(), outputProperty);
        });
        data.add("inputsValues", inputs);
        JsonArray inputsValuesI = jsonObject.getAsJsonArray("inputsValues");
        JsonObject inputsValues = new JsonObject();
        inputsValuesI.forEach(inputsValue -> {
            inputsValues.add(inputsValuesI.getAsJsonObject().get("name").getAsString(), inputsValuesI);
        });
        return endNode.toString();
    }
}
