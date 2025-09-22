package ai.migrate.service;

import ai.agent.Agent;
import ai.agent.chat.rag.LocalRagAgent;
import ai.common.pojo.Response;
import ai.config.pojo.AgentConfig;
import ai.dao.ManagerDao;
import ai.dto.DeductExpensesRequest;
import ai.dto.FeeRequiredAgentRequest;
import ai.dto.ManagerModel;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.service.CompletionsService;
import ai.llm.utils.LlmAdapterFactory;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.router.pojo.LLmRequest;
import ai.servlet.dto.LagiAgentExpenseListResponse;
import ai.servlet.dto.LagiAgentListResponse;
import ai.servlet.dto.LagiAgentResponse;
import ai.servlet.dto.OrchestrationItem;
import ai.utils.MigrateGlobal;
import ai.utils.OkHttpUtil;
import ai.worker.skillMap.SkillMapUtil;
import cn.hutool.core.util.IdUtil;
import com.google.common.collect.Lists;
import com.google.gson.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AgentService {
    private final Gson gson = new Gson();
    private static final String SAAS_BASE_URL = MigrateGlobal.SAAS_BASE_URL;

    public Response addLagiAgent(AgentConfig agentConfig) throws IOException {
        String resultJson = OkHttpUtil.post(SAAS_BASE_URL + "/agent/addLagiAgent", gson.toJson(agentConfig));
        Response response = gson.fromJson(resultJson, Response.class);
        return response;
    }

    public Response updateLagiAgent(AgentConfig agentConfig) throws IOException {
        String resultJson = OkHttpUtil.post(SAAS_BASE_URL + "/agent/updateLagiAgent", gson.toJson(agentConfig));
        Response response = gson.fromJson(resultJson, Response.class);
        return response;
    }

    public Response orchestrationAgent(String lagiUserId, String agentId, List<OrchestrationItem> orchestrationData) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("lagiUserId", lagiUserId);
        params.put("agentId", agentId);
        String resultJson = OkHttpUtil.get(SAAS_BASE_URL + "/agent/getLagiAgent", params);
        LagiAgentResponse lagiAgentResponse = gson.fromJson(resultJson, LagiAgentResponse.class);
        AgentConfig agentConfig = lagiAgentResponse.getData();
        String updatedCharacter = updateCharacter(agentConfig.getCharacter(), orchestrationData);
        agentConfig.setCharacter(updatedCharacter);
        String newResultJson = OkHttpUtil.post(SAAS_BASE_URL + "/agent/updateLagiAgent", gson.toJson(agentConfig));
        Response response = gson.fromJson(newResultJson, Response.class);
        return response;
    }

    private String updateCharacter(String currentCharacter, List<OrchestrationItem> orchestrationData) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("角色描述：\n");
        prompt.append(currentCharacter).append("\n\n");

        prompt.append("任务和逻辑更新：\n");

        for (OrchestrationItem item : orchestrationData) {
            prompt.append("任务: ").append(item.getTask()).append("\n");
            prompt.append("逻辑: ").append(item.getLogic()).append("\n\n");
        }

        prompt.append("根据以上任务和逻辑更新，生成以下内容：\n");
        prompt.append("1. 更新智能体的角色规范，确保任务范围包含所有新的任务。\n");
        prompt.append("2. 确保思考规范和回复规范与任务和逻辑相匹配。\n");
        prompt.append("3. 根据任务的性质，智能体应该如何反应，如何处理与任务相关的请求。\n");
        prompt.append("4. 考虑到任务和逻辑，如何引导用户并维持专业性和一致性。");

        return callAIModel(prompt.toString());
    }

    private String callAIModel(String prompt) {
        ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest();
        chatCompletionRequest.setTemperature(0.8);
        chatCompletionRequest.setMax_tokens(1024);

        ChatMessage message = new ChatMessage();
        message.setRole("system");
        message.setContent(prompt);

        chatCompletionRequest.setMessages(Lists.newArrayList(message));
        chatCompletionRequest.setStream(false);

        CompletionsService completionsService = new CompletionsService();
        ChatCompletionResult result = completionsService.completions(chatCompletionRequest);

        return result.getChoices().get(0).getMessage().getContent();
    }


    public List<OrchestrationItem> generateTasksAndLogics(AgentConfig agentConfig) throws IOException {
        String currentCharacter = agentConfig.getCharacter();
        String prompt = generatePromptForTasksAndLogics(currentCharacter);
        String response = callAIModelForTasksAndLogics(prompt);
        return parseTasksAndLogicsResponse(response);
    }

    private String generatePromptForTasksAndLogics(String currentCharacter) {
        String template =
                "你是一个专业领域的智能助手，专门处理以下目标：\n" +
                        "###目标###：\n" +
                        "{}\n" + // 插入 currentCharacter，角色的目标和职责
                        "\n" +
                        "根据角色的职责和任务范围，请生成以下内容：\n" +
                        "1. 任务（task）和对应的逻辑（logic）。\n" +
                        "2. 请确保每个任务都是在角色任务范围内的，并且每个逻辑都是合理且与任务相关的。\n" +
                        "请按以下格式生成任务和逻辑：\n" +
                        "{\n" +
                        "  \"task\": \"任务描述\",\n" +
                        "  \"logic\": \"任务完成的具体逻辑描述\"\n" +
                        "}\n" +
                        "你应该只以 JSON 格式响应，响应格式如下：\n" +
                        "[\n" +
                        "  { \"task\": \"任务1\", \"logic\": \"逻辑1\" },\n" +
                        "  { \"task\": \"任务2\", \"logic\": \"逻辑2\" }\n" +
                        "]\n" +
                        "请确保返回的 JSON 是合法的，且每个任务和逻辑都有明确的描述。";
        String prompt = template.replace("{}", currentCharacter);
        return prompt;
    }

    private String callAIModelForTasksAndLogics(String prompt) {
        ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest();
        chatCompletionRequest.setTemperature(0.8);
        chatCompletionRequest.setMax_tokens(1024);
        ChatMessage message = new ChatMessage();
        message.setRole("system");
        message.setContent(prompt);
        chatCompletionRequest.setMessages(Lists.newArrayList(message));
        chatCompletionRequest.setStream(false);
        CompletionsService completionsService = new CompletionsService();
        ChatCompletionResult result = completionsService.completions(chatCompletionRequest);
        return result.getChoices().get(0).getMessage().getContent();
    }

    private List<OrchestrationItem> parseTasksAndLogicsResponse(String response) {
        JsonArray jsonArray = JsonParser.parseString(response).getAsJsonArray();
        List<OrchestrationItem> orchestrationItems = new ArrayList<>();
        for (JsonElement element : jsonArray) {
            JsonObject jsonObject = element.getAsJsonObject();
            String task = jsonObject.get("task").getAsString();
            String logic = jsonObject.get("logic").getAsString();
            orchestrationItems.add(new OrchestrationItem(task, logic));
        }
        return orchestrationItems;
    }

    public Response deleteLagiAgentById(List<Integer> ids) throws IOException {
        String resultJson = OkHttpUtil.post(SAAS_BASE_URL + "/agent/deleteLagiAgentById", gson.toJson(ids));
        Response response = gson.fromJson(resultJson, Response.class);
        return response;
    }

    public LagiAgentListResponse getLagiAgentList(String lagiUserId, int pageNumber, int pageSize, String publishStatus) throws IOException {
        Map<String, String> params = new HashMap<>();
        if (lagiUserId != null) {
            params.put("lagiUserId", lagiUserId);
        }
        if (publishStatus != null) {
            params.put("publishStatus", publishStatus);
        }
        params.put("pageNumber", String.valueOf(pageNumber));
        params.put("pageSize", String.valueOf(pageSize));
        String resultJson = OkHttpUtil.get(SAAS_BASE_URL + "/agent/getLagiAgentList", params);
        LagiAgentListResponse lagiAgentResponse = gson.fromJson(resultJson, LagiAgentListResponse.class);
        return lagiAgentResponse;
    }

    public LagiAgentResponse getLagiAgent(String lagiUserId, String agentId) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("lagiUserId", lagiUserId);
        params.put("agentId", agentId);
        String resultJson = OkHttpUtil.get(SAAS_BASE_URL + "/agent/getLagiAgent", params);
        LagiAgentResponse lagiAgentResponse = gson.fromJson(resultJson, LagiAgentResponse.class);
        return lagiAgentResponse;
    }

    public LagiAgentExpenseListResponse getPaidAgentByUser(String lagiUserId, String pageNumber, String pageSize) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("lagiUserId", lagiUserId);
        if (pageNumber != null) params.put("pageNumber", pageNumber);
        if (pageSize != null) params.put("pageSize", pageSize);
        String resultJson = OkHttpUtil.get(SAAS_BASE_URL + "/agent/getPaidAgentByUser", params);
        LagiAgentExpenseListResponse lagiAgentResponse = gson.fromJson(resultJson, LagiAgentExpenseListResponse.class);
        return lagiAgentResponse;
    }

    public Response deductExpenses(DeductExpensesRequest deductExpensesRequest) throws IOException {
        String resultJson = OkHttpUtil.post(SAAS_BASE_URL + "/agent/deductExpenses", gson.toJson(deductExpensesRequest));
        Response lagiAgentResponse = gson.fromJson(resultJson, Response.class);
        return lagiAgentResponse;
    }

    public LagiAgentListResponse getFeeRequiredAgent(FeeRequiredAgentRequest feeRequiredAgentRequest) throws IOException {
        String resultJson = OkHttpUtil.post(SAAS_BASE_URL + "/agent/getFeeRequiredAgent", gson.toJson(feeRequiredAgentRequest));
        LagiAgentListResponse lagiAgentResponse = gson.fromJson(resultJson, LagiAgentListResponse.class);
        return lagiAgentResponse;
    }

    public Response createLagiAgent(AgentConfig agentConfig) throws IOException {
        String prompt = generatePrompt(agentConfig.getDescribe(), agentConfig.getName());
        ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest();
        chatCompletionRequest.setTemperature(0.8);
        chatCompletionRequest.setMax_tokens(1024);
        ChatMessage message = new ChatMessage();
        message.setRole("system");
        message.setContent(prompt);
        chatCompletionRequest.setMessages(Lists.newArrayList(message));
        chatCompletionRequest.setStream(false);
        CompletionsService completionsService = new CompletionsService();
        ChatCompletionResult result = completionsService.completions(chatCompletionRequest);
        agentConfig.setCharacter(result.getChoices().get(0).getMessage().getContent());
        agentConfig.setDriver("ai.agent.customer.GeneralAgent");
        agentConfig.setToken(IdUtil.simpleUUID());
        agentConfig.setAppId(IdUtil.simpleUUID());
        String resultJson = OkHttpUtil.post(SAAS_BASE_URL + "/agent/addLagiAgent", gson.toJson(agentConfig));
        Response response = gson.fromJson(resultJson, Response.class);
        return response;
    }

    public static String generatePrompt(String describe, String name) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("智能体名称：").append(name).append("\n\n");
        prompt.append("简介：").append(describe).append("\n\n");

        prompt.append("任务范围：根据你的简介，自动确定你的任务范围。\n");
        prompt.append("你仅能回答与你的专业领域相关的问题。请确保在自己的任务范围内提供回答，若遇到超出任务范围的问题，需明确告知用户并引导他们寻找其他专家。\n\n");

        prompt.append("根据上述简介，生成以下内容：\n\n");

        prompt.append("#角色规范\n");
        prompt.append("你是一个").append(name).append("，主要职责是").append(describe).append("。你需熟练掌握相关领域的知识，并根据用户需求提供准确、专业、权威的解答。\n");
        prompt.append("你的任务范围包括：所有与").append(describe).append("相关的任务，如：提供专业建议、解答相关问题等。对于其他不相关的领域问题，请告知用户并引导他们咨询其他专业领域的专家。\n\n");

        prompt.append("#思考规范\n");
        prompt.append("在回应用户问题时，你需遵循以下思考路径：\n");
        prompt.append("1. **确认问题领域**：首先明确用户问题的领域，确保问题属于你的专业领域。\n");
        prompt.append("2. **任务范围内的解答**：确保问题是在你的任务范围内。如果问题超出任务范围，必须告知用户并引导他们去其他相关领域。\n");
        prompt.append("3. **综合解答**：结合你的专业知识，提供全面且精准的解答，尽量通过实际的案例或建议来帮助用户。\n");
        prompt.append("4. **处理不确定问题**：若遇到无法解答或不确定的问题，应礼貌地告诉用户你无法提供帮助，并建议用户咨询相关领域的专家。\n");
        prompt.append("5. **确保信息权威性**：在提供建议时，确保信息准确无误且具有权威性，避免提供未经验证的答案。\n");
        prompt.append("6. **引导用户**：若用户提出偏离任务范围的问题，温和地引导用户回到相关问题范围。例如：\n");
        prompt.append("   - “我主要专注于提供健身建议，对于您提到的饮食问题，我可以给出建议，但关于您提到的假期问题，我建议您咨询其他相关领域的专家。”\n\n");

        prompt.append("#回复规范\n");
        prompt.append("在与用户交流时，你应遵循以下规范：\n");
        prompt.append("1. **语气**：使用专业且权威的语气，让用户感受到你的可靠性。\n");
        prompt.append("2. **回复格式**：回复要清晰、有条理，最好使用列表、序号等形式，便于用户理解。\n");
        prompt.append("3. **深入询问**：在解答问题时，主动询问用户更多背景信息，以便提供更精确的解答。\n");
        prompt.append("4. **结尾引导**：每次回答结束后，可以询问用户是否还有其他相关问题，例如：“请问您是否还有其他问题需要咨询？”\n");
        prompt.append("5. **直接回应**：确保每个回复都直接针对用户的具体问题，避免偏离主题。\n");

        prompt.append("\n根据上述结构，生成相应的角色规范、思考规范和回复规范。请确保输出内容详细、结构清晰，并且符合智能体的角色特征。\n");

        return prompt.toString();
    }

    public List<Agent<ChatCompletionRequest, ChatCompletionResult>> getAllAgents(LLmRequest llmRequest, String uri) throws IOException {
//        LagiAgentExpenseListResponse paidAgentByUser = getPaidAgentByUser(llmRequest.getUserId(), "1", "1000");
//        Map<Integer, Boolean> haveABalance = paidAgentByUser.getData().stream().collect(Collectors.toMap(AgentConfig::getId, agentConfig -> {
//            BigDecimal balance = agentConfig.getBalance();
//            BigDecimal pricePerReq = agentConfig.getPricePerReq();
//            return balance.doubleValue() >= pricePerReq.doubleValue();
//        }));
//        LagiAgentListResponse lagiAgentList = getLagiAgentList(null, 1, 1000, "true");
//        List<AgentConfig> agentConfigs = lagiAgentList.getData();
//        List<Agent<ChatCompletionRequest, ChatCompletionResult>> agents = SkillMapUtil.convert2AgentList(agentConfigs, haveABalance);

        List<Agent<ChatCompletionRequest, ChatCompletionResult>> agents = new ArrayList<>();
        List<Agent<ChatCompletionRequest, ChatCompletionResult>> llmAndAgentList = SkillMapUtil.getLlmAndAgentList();
        agents.addAll(llmAndAgentList);
        return agents;
    }

    public List<Agent<ChatCompletionRequest, ChatCompletionResult>> getAgentsById(
            List<Integer> agentIds,
            List<Agent<ChatCompletionRequest, ChatCompletionResult>> agents) {

        if (agentIds == null || agentIds.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Integer, Agent<ChatCompletionRequest, ChatCompletionResult>> agentMap = agents.stream()
                .collect(Collectors.toMap(
                        agent -> agent.getAgentConfig().getId(),
                        agent -> agent,
                        (existing, replacement) -> existing
                ));
        return agentIds.stream()
                .map(agentMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<ILlmAdapter> getUserLlmAdapters(String userId) {
        // TODO 2025/3/4  support invoke remote service
        ManagerDao managerDao = new ManagerDao();
        List<ManagerModel> managerModels = managerDao.getManagerModels(userId, 1);
        return managerModels.stream().map(m -> {
            return LlmAdapterFactory.getLlmAdapter(m.getModelType(), m.getModelName(), 999, m.getApiKey(), m.getEndpoint());
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }
}

