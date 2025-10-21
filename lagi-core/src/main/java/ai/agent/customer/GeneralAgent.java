package ai.agent.customer;

import ai.agent.Agent;
import ai.agent.customer.pojo.ToolInfo;
import ai.common.pojo.ImageGenerationData;
import ai.common.pojo.ImageGenerationRequest;
import ai.common.pojo.ImageGenerationResult;
import ai.config.ContextLoader;
import ai.config.pojo.AgentConfig;
import ai.image.service.AllImageService;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.*;
import ai.utils.OkHttpUtil;
import ai.utils.qa.ChatCompletionUtil;
import ai.workflow.LagiAgentResponse;
import ai.workflow.WorkflowEngine;
import ai.workflow.pojo.NodeResult;
import ai.workflow.pojo.WorkflowContext;
import ai.workflow.pojo.WorkflowResult;
import ai.workflow.utils.DefaultNodeEnum;
import cn.hutool.json.JSONUtil;
import com.google.common.collect.Lists;
import com.google.gson.*;
import io.reactivex.Observable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GeneralAgent extends Agent<ChatCompletionRequest, ChatCompletionResult> {
    private final CompletionsService completionsService = new CompletionsService();
    protected List<ToolInfo> toolInfoList;
    private static Gson gson = new Gson();

    public GeneralAgent(AgentConfig agentConfig) {
        this.agentConfig = agentConfig;
        this.toolInfoList = new ArrayList<>();
    }

    public GeneralAgent() {
        this.toolInfoList = new ArrayList<>();
    }

    private String parsingQuestion(String question) {
        boolean isValidJson = false;
        String resultContent = "";

//        while (!isValidJson) {
            ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest();
            chatCompletionRequest.setTemperature(0.8);
            chatCompletionRequest.setMax_tokens(1024);
            chatCompletionRequest.setCategory("default");

            String prompt = "你是一个专业的智能助手，只能完成你所使用的动作所能完成的目标，而不是使用你所学的事实知识来完成目标。请遵循以下要求：\n" +
                    "1. **拆解问题**：\n" +
                    "   - 如果问题包含多个子任务（例如查询武汉天气和油价），请将问题拆解成多个子问题，并返回一个列表，其中包含所有拆解后的问题。例如：\n" +
                    "     {\n" +
                    "       \"action\": \"multi_part_question\",\n" +
                    "       \"questions\": [\n" +
                    "         \"查询武汉的天气\",\n" +
                    "         \"查询武汉的油价\"\n" +
                    "       ]\n" +
                    "     }\n" +
                    "   - 如果问题涉及图片生成（例如请求生成一张风景图），请返回：\n" +
                    "     {\n" +
                    "       \"action\": \"image_generation_needed\",\n" +
                    "       \"questions\": \"Generate landscape image\",\n" +
                    "       \"chinese_questions\": \"生成风景图\"\n" +
                    "     }\n" +
                    "   - 如果问题是简单的单一问题（如查询天气或油价），请返回：\n" +
                    "     {\n" +
                    "       \"action\": \"single_question\",\n" +
                    "       \"questions\": \"查询武汉的天气\"\n" +
                    "     }\n" +
                    "\n" +
                    "2. **返回值结构**：\n" +
                    "   - 如果问题是拆解后的多个子任务（例如查询天气和油价），返回一个包含子任务的列表，如上面的 \"multi_part_question\" 示例。\n" +
                    "   - 如果问题只包含一个任务，如查询天气或油价，返回一个字符串表示任务。\n" +
                    "   - 如果问题需要生成图片，返回包含翻译后的问题的字符串，并且附加中文和英文的回答。\n" +
                    "\n" +
                    "3. 请注意：\n" +
                    "   - **返回格式**必须严格遵循上述结构。\n" +
                    "   - 你只需返回一个 JSON 对象，指示当前问题的拆解方式或是否需要生成图片。\n" +
                    "   - 不要返回无关的内容，只需提供所需的操作指令。\n" +
                    "\n" +
                    "问题：" + question;

            ChatMessage message = new ChatMessage();
            message.setRole("user");
            message.setContent(prompt);

            chatCompletionRequest.setMessages(Lists.newArrayList(message));
            chatCompletionRequest.setStream(false);

            CompletionsService completionsService = new CompletionsService();
            ChatCompletionResult result = completionsService.completions(chatCompletionRequest);

            resultContent = result.getChoices().get(0).getMessage().getContent();

            isValidJson = JSONUtil.isJson(resultContent);
//        }

        return resultContent;
    }

    @Override
    public ChatCompletionResult communicate(ChatCompletionRequest data) {
        String schema = agentConfig.getSchema();
        JsonObject schemaObj = gson.fromJson(schema, JsonObject.class);
        JsonArray nodes = schemaObj.getAsJsonArray("nodes");
        Map<String, Object> inputData = new HashMap<>();
        List<JsonElement> list = nodes.asList();
        String lastUserContent = ChatCompletionUtil.getLastUserContent(data);
        for(JsonElement node : list) {
            JsonObject asJsonObject = node.getAsJsonObject();
            if(asJsonObject.get("type").getAsString().equals(DefaultNodeEnum.StartNode.getName())) {
                JsonObject asJsonObject1 = asJsonObject.getAsJsonObject("data");
                JsonObject asJsonObject2 = asJsonObject1.getAsJsonObject("outputs").getAsJsonObject("properties");
                Set<String> propertyNames = asJsonObject2.keySet();
                for(String propertyName : propertyNames) {
                    if( "query".equals(propertyName) || "userInput".equals(lastUserContent)) {
                        inputData.put(propertyName, lastUserContent);
                    } else {
                        inputData.put(propertyName, "");
                    }
                }
                break;
            }

        }
        WorkflowEngine workflowEngine = new WorkflowEngine();
        String taskId = UUID.randomUUID().toString();
        Map<String, Object> memories = new ConcurrentHashMap<>();
        List<ChatMessage> chatMessages = data.getMessages().subList(0, data.getMessages().size() - 1);
        memories.put("workflow", chatMessages);
        WorkflowContext workflowContext = new WorkflowContext(inputData, memories);
        WorkflowResult result = workflowEngine.execute(taskId, schema, workflowContext);
        ChatCompletionResult chatCompletionResult = null;
        String s = detectEndNodeResult(result);
        if(s != null) {
            chatCompletionResult = ChatCompletionUtil.toChatCompletionResult(s, null);
        }
        return chatCompletionResult;
    }

    public String detectEndNodeResult(WorkflowResult result) {
        if(!result.isSuccess()) {
            return null;
        }
        NodeResult nodeResult = (NodeResult)result.getResult();
        if(nodeResult.getNodeType().equals(DefaultNodeEnum.EndNode.getName())) {
            if(DefaultNodeEnum.EndNode.getName().equals(nodeResult.getNodeType())) {
                String res = null;
                Map<String, Object> outputs = (Map<String, Object>) nodeResult.getData();
                Set<String> strings = outputs.keySet();
                for (String string : strings) {
                    Object output = outputs.get(string);
                    if(output instanceof String && (res == null || string.equals("result"))) {
                        res = (String) output;
                    }
                }
                return res;
            }
        }
        List<WorkflowResult> subResults = result.getSubResults();
        for (WorkflowResult subResult : subResults) {
            String res = detectEndNodeResult(subResult);
            if(res != null) {
                return res;
            }
        }
        return null;
    }


    @Override
    public void connect() {
    }

    @Override
    public void terminate() {
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void send(ChatCompletionRequest request) {
    }

    @Override
    public ChatCompletionResult receive() {
        return null;
    }

    @Override
    public Observable<ChatCompletionResult> stream(ChatCompletionRequest data) {
        throw new UnsupportedOperationException("streaming is not supported");
    }

    @Override
    public boolean canStream() {
        return true;
    }

    public static void main(String[] args) {
        ContextLoader.loadContext();

        String reqStr = "{\n" +
                "  \"category\": \"default\",\n" +
                "  \"messages\": [\n" +
                "    {\n" +
                "      \"role\": \"user\",\n" +
                "      \"content\": \"你帮我制定一份一个月的健身计划表\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"temperature\": 0.8,\n" +
                "  \"max_tokens\": 1024,\n" +
                "  \"stream\": false\n" +
                "}";

        Gson gson = new Gson();
        ChatCompletionRequest request = gson.fromJson(reqStr, ChatCompletionRequest.class);

        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setName("健身教练AI助手");
        agentConfig.setCharacter("# 角色规范\n" +
                "\n" +
                "你是一个健身教练AI助手，主要职责是根据用户的需求制定个性化的健身计划和饮食建议。你擅长科学的训练方法，并根据生理学和营养学为用户提供专业的健身指导。你总是以耐心、热情的态度与用户互动，帮助他们实现健身目标。你需要熟练掌握相关领域的知识，并根据用户需求提供准确、专业、权威的解答。\n" +
                "\n" +
                "# 思考规范\n" +
                "\n" +
                "在回应用户问题时，你需遵循以下思考路径：\n" +
                "1. **确认问题领域**：首先明确用户问题的领域（如健身计划、饮食建议等），确保你拥有相关的专业知识。\n" +
                "2. **综合解答**：结合科学理论、实际案例或其他专业资源，提供全面且精准的解答。\n" +
                "3. **处理不确定问题**：若遇到不确定或无法直接解答的问题，应建议用户咨询相关领域的专家或提供权威资源链接。\n" +
                "4. **确保信息权威性**：在提供任何建议时，务必确保信息准确无误且具备权威性。\n" +
                "5. **引导用户**：对于偏离咨询范围的问题，温和地引导用户回到主题，例如：“我主要专注于提供健身和饮食建议，关于您提到的其他方面的问题，我建议您直接咨询相关领域的专家。”\n" +
                "\n" +
                "# 回复规范\n" +
                "\n" +
                "在与用户交流时，你应遵循以下规范：\n" +
                "1. **语气**：使用专业且权威的语气，让用户感受到你的专业性和可靠性。\n" +
                "2. **回复格式**：回复要清晰、有条理，最好使用列表、序号等形式，便于用户理解。\n" +
                "3. **深入询问**：在解答问题时，主动询问用户更多背景信息，以便提供更加精确的解答。\n" +
                "4. **结尾引导**：每次回答结束后，可以询问用户是否还有其他相关问题，例如：“请问您是否还有其他健身或饮食方面的疑问？”\n" +
                "5. **直接回应**：确保每个回复都直接针对用户的具体问题，避免偏离主题。");

        GeneralAgent generalAgent = new GeneralAgent(agentConfig);

        ChatCompletionResult communicate = generalAgent.communicate(request);
        String jsonStr = JSONUtil.toJsonStr(communicate);
        System.out.println("jsonStr = " + jsonStr);
    }
}
