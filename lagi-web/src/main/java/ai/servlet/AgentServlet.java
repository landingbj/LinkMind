package ai.servlet;

import ai.agent.Agent;
import ai.agent.dto.*;
import ai.common.exception.RRException;
import ai.common.pojo.Response;
import ai.config.pojo.AgentConfig;
import ai.dto.*;
import ai.manager.AgentManager;
import ai.agent.AgentService;
import ai.migrate.service.PayService;
import ai.worker.skillMap.SkillMap;
import cn.hutool.core.util.StrUtil;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;


public class AgentServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    protected Gson gson = new Gson();
    private final AgentService agentService = new AgentService();
    private final PayService payService = new PayService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        if (method.equals("getLagiAgentList")) {
            this.getLagiAgentList(req, resp);
        } else if (method.equals("getLagiAgent")) {
            this.getLagiAgent(req, resp);
        } else if (method.equals("getAgentChargeDetail")) {
            this.getAgentChargeDetail(req, resp);
        } else if (method.equals("getPaidAgentByUser")) {
            this.getPaidAgentByUser(req, resp);
        } else if(method.equals("getAgentLabel")) {
            this.getAgentLabel(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        if (method.equals("addLagiAgent")) {
            this.addLagiAgent(req, resp);
        } else if (method.equals("updateLagiAgent")) {
            this.updateLagiAgent(req, resp);
        } else if (method.equals("deleteLagiAgentById")) {
            this.deleteLagiAgentById(req, resp);
        } else if (method.equals("h5Prepay")) {
            this.h5Prepay(req, resp);
        } else if (method.equals("prepay")) {
            this.prepay(req, resp);
        } else if (method.equals("deductExpenses")) {
            this.deductExpenses(req, resp);
        } else if (method.equals("getFeeRequiredAgent")) {
            this.getFeeRequiredAgent(req, resp);
        }else if (method.equals("createLagiAgent")) {
            this.createLagiAgent(req, resp);
        }else if (method.equals("orchestrationAgent")) {
            this.orchestrationAgent(req, resp);
        } else if(method.equals("manualScoring")) {
            this.manualScoring(req, resp);
        }
    }

    private void getFeeRequiredAgent(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        FeeRequiredAgentRequest feeRequiredAgentRequest = reqBodyToObj(req, FeeRequiredAgentRequest.class);
        LagiAgentListResponse response = agentService.getFeeRequiredAgent(feeRequiredAgentRequest);
        responsePrint(resp, gson.toJson(response));
    }

    private void h5Prepay(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        PrepayRequest prepayRequest = reqBodyToObj(req, PrepayRequest.class);
        PrepayResponse prepayResponse = payService.h5Prepay(prepayRequest);
        responsePrint(resp, gson.toJson(prepayResponse));
    }

    private void prepay(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        PrepayRequest prepayRequest = reqBodyToObj(req, PrepayRequest.class);
        PrepayResponse prepayResponse = payService.prepay(prepayRequest);
        responsePrint(resp, gson.toJson(prepayResponse));
    }

    private void getAgentChargeDetail(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String outTradeNo = req.getParameter("outTradeNo");
        AgentChargeDetail agentChargeDetail = payService.getAgentChargeDetail(outTradeNo);
        responsePrint(resp, gson.toJson(agentChargeDetail));
    }

    private void getLagiAgent(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String lagiUserId = req.getParameter("lagiUserId");
        String agentId = req.getParameter("agentId");
        LagiAgentResponse lagiAgentResponse = agentService.getLagiAgent(lagiUserId, agentId);
        responsePrint(resp, gson.toJson(lagiAgentResponse));
    }



    private void getLagiAgentList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        int pageNumber = Integer.parseInt(req.getParameter("pageNumber"));
        int pageSize = Integer.parseInt(req.getParameter("pageSize"));
        String lagiUserId = req.getParameter("lagiUserId");
        String publishStatus = req.getParameter("publishStatus");
        LagiAgentListResponse lagiAgentResponse = agentService.getLagiAgentList(lagiUserId, pageNumber, pageSize, publishStatus);
        responsePrint(resp, gson.toJson(lagiAgentResponse));
    }

    private void getAgentLabel(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String lagiUserId = req.getParameter("lagiUserId");
        LagiAgentListResponse lagiAgentResponse = agentService.getLagiAgentList(lagiUserId, 1, 1000, "true");
        List<AgentConfig> data = lagiAgentResponse.getData();
        JsonArray jsonElements = new JsonArray();
        data.forEach(agentConfig -> {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("label", agentConfig.getName());
            jsonObject.addProperty("value", agentConfig.getId());
            jsonElements.add(jsonObject);
        });
        JsonObject jsonObject = new JsonObject();
        jsonObject.add("data", jsonElements);
        responsePrint(resp, gson.toJson(jsonObject));
    }

    private void getPaidAgentByUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String lagiUserId = req.getParameter("lagiUserId");
        String pageNumber = req.getParameter("pageNumber");
        String pageSize = req.getParameter("pageSize");
        LagiAgentExpenseListResponse response = agentService.getPaidAgentByUser(lagiUserId, pageNumber, pageSize);
        responsePrint(resp, gson.toJson(response));
    }

    private void addLagiAgent(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        AgentConfig agentConfig = reqBodyToObj(req, AgentConfig.class);
        Response response = agentService.addLagiAgent(agentConfig);
        responsePrint(resp, gson.toJson(response));
    }

    private void createLagiAgent(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        AgentConfig agentConfig = reqBodyToObj(req, AgentConfig.class);
        Response response = agentService.createLagiAgent(agentConfig);
        responsePrint(resp, gson.toJson(response));
    }

    private void updateLagiAgent(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        AgentConfig agentConfig = reqBodyToObj(req, AgentConfig.class);
        Response response = agentService.updateLagiAgent(agentConfig);
        responsePrint(resp, gson.toJson(response));
    }

    private void orchestrationAgent(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        // 从请求体中读取 JSON 数据
        BufferedReader reader = req.getReader();
        StringBuilder jsonBody = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            jsonBody.append(line);
        }

        // 使用 Gson 解析 JSON 数据
        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(jsonBody.toString(), JsonObject.class);

        // 提取字段
        String lagiUserId = jsonObject.get("lagiUserId").getAsString();
        String agentId = jsonObject.get("agentId").getAsString();
        List<OrchestrationItem> orchestrationData = gson.fromJson(
                jsonObject.get("orchestrationData"),
                new TypeToken<List<OrchestrationItem>>(){}.getType()
        );

        // 调用服务层处理逻辑
        Response response = agentService.orchestrationAgent(lagiUserId, agentId, orchestrationData);
        responsePrint(resp, gson.toJson(response));
    }

/*    private void generateTasksAndLogics(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        AgentConfig agentConfig = reqBodyToObj(req, AgentConfig.class);
        Response response = agentService.generateTasksAndLogics(agentConfig);
        responsePrint(resp, gson.toJson(response));
    }*/

    private void deleteLagiAgentById(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Type listType = new TypeToken<ArrayList<Integer>>() {
        }.getType();
        List<Integer> ids = gson.fromJson(requestToJson(req), listType);
        Response response = agentService.deleteLagiAgentById(ids);
        responsePrint(resp, gson.toJson(response));
    }

    private void deductExpenses(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        DeductExpensesRequest deductExpensesRequest = reqBodyToObj(req, DeductExpensesRequest.class);
        Response response = agentService.deductExpenses(deductExpensesRequest);
        responsePrint(resp, gson.toJson(response));
    }

    public void manualScoring(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        ManualScoring manualScoring = reqBodyToObj(req, ManualScoring.class);
        if(manualScoring == null) {
            responsePrint(resp, "{\"code\": 400, \"message\": \"参数错误\"}");
            return ;
        }
        if(manualScoring.getQuestion() == null || manualScoring.getAgentId() == null || manualScoring.getScore() == null) {
            responsePrint(resp, "{\"code\": 400, \"message\": \"参数错误\"}");
            return ;
        }
        SkillMap skillMap = new SkillMap();
        LagiAgentResponse lagiAgentResponse = agentService.getLagiAgent(null, manualScoring.getAgentId().toString());
        String agentName = null;
        if(lagiAgentResponse != null && lagiAgentResponse.getData() != null) {
            agentName = lagiAgentResponse.getData().getName();
        }
        if(StrUtil.isBlank(agentName)) {
            List<Agent<?, ?>> agents = AgentManager.getInstance().agents();
            if(agents != null) {
                for (Agent<?, ?> agent : agents) {
                    if(agent.getAgentConfig().getId().equals(manualScoring.getAgentId())) {
                        agentName = agent.getAgentConfig().getName();
                        break;
                    }
                }
            }
        }
        if(StrUtil.isBlank(agentName)) {
            responsePrint(resp, "{\"code\": 400, \"message\": \"未找到对应的智能体\"}");
            return ;
        }
        try {
            skillMap.manualScoring(manualScoring.getQuestion(), manualScoring.getAgentId(), agentName ,manualScoring.getScore());
            responsePrint(resp, "{\"code\": 0, \"message\": \"录入成功\"}");
        } catch (RRException e) {
            responsePrint(resp, "{\"code\": 500, \"message\": \"" + e.getMsg() + "\"}");
        }
    }
}
