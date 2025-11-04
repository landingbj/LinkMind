package ai.workflow.tools.impl;

import ai.openai.pojo.Tool;
import ai.utils.ResourceUtil;
import ai.workflow.tools.AgentTool;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;


public class GenerateStartNodeTool implements AgentTool {

    @Override
    public String getName() {
        return "generate_start_node";
    }

    @Override
    public Tool getTool() {
        String s = ResourceUtil.loadAsString("/tools/generate_start_node_tool.json");
        Gson gson = new Gson();
        return gson.fromJson(s, Tool.class);
    }

    @Override
    public String invoke(String jsonStr) {
        Gson gson = new Gson();
        JsonObject startNode = new JsonObject();
        JsonObject jsonObject = gson.fromJson(jsonStr, JsonObject.class);
        String id = jsonObject.get("nodeId").getAsString();
        startNode.addProperty("id", id);
        startNode.addProperty("type", "start");
        JsonObject meta = new JsonObject();
        startNode.add("meta", meta);
        JsonObject position = jsonObject.getAsJsonObject("position");
        meta.add("position",  position);
        JsonObject data = new JsonObject();
        startNode.add("data",  data);
        data.addProperty("title", "开始");
        JsonObject outputs = new JsonObject();
        data.add("outputs", outputs);
        JsonArray outputProperties = jsonObject.getAsJsonArray("outputProperties");
        outputProperties.forEach(outputProperty -> {
            outputs.add(outputProperty.getAsJsonObject().get("name").getAsString(), outputProperty);
        });
        return startNode.toString();
    }
}
