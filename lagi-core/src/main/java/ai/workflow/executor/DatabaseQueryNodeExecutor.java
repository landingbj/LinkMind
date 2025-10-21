package ai.workflow.executor;

import ai.config.ContextLoader;
import ai.config.GlobalConfigurations;
import ai.database.DatabaseOperationService;
import ai.database.pojo.QueryRequest;
import ai.database.pojo.SQLJdbc;
import ai.workflow.TaskStatusManager;
import ai.workflow.exception.WorkflowException;
import ai.workflow.pojo.Node;
import ai.workflow.pojo.NodeResult;
import ai.workflow.pojo.TaskReportOutput;
import ai.workflow.pojo.WorkflowContext;
import ai.workflow.utils.InputValueParser;
import ai.workflow.utils.NodeExecutorUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库查询节点执行器
 * 仅支持SELECT操作，防止SQL注入
 */
public class DatabaseQueryNodeExecutor implements INodeExecutor {

    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();
    private final DatabaseOperationService databaseService = new DatabaseOperationService();

    @Override
    public boolean isValid() {
        GlobalConfigurations configuration = ContextLoader.configuration;
        if (configuration != null && configuration.getStores() != null
                && configuration.getStores().getDatabase() != null) {
            return !configuration.getStores().getDatabase().isEmpty();
        }
        return false;
    }

    @Override
    public NodeResult execute(String taskId, Node node, WorkflowContext context) throws Exception {
        long startTime = System.currentTimeMillis();
        String nodeId = node.getId();
        taskStatusManager.updateNodeReport(taskId, nodeId, "processing", startTime, null, null, null);

        NodeResult nodeResult = null;
        try {
            JsonNode data = node.getData();
            JsonNode inputsValues = data.get("inputsValues");

            if (inputsValues == null) {
                throw new WorkflowException("数据库查询节点缺少输入配置");
            }

            // 解析输入值
            Map<String, Object> inputs = InputValueParser.parseInputs(inputsValues, context);

            // 执行数据库查询
            Object result = executeQuery(inputs);

            Map<String, Object> output = new HashMap<>();
            output.put("result", result);

            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            TaskReportOutput.Snapshot nodeSnapshot = taskStatusManager.createNodeSnapshot(nodeId, inputs, output, null, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, nodeSnapshot);
            taskStatusManager.addExecutionLog(taskId, nodeId, "数据库查询节点执行成功", startTime);
            nodeResult = new NodeResult(node.getType(), node.getId(), output, null);

        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "数据库查询节点", e);
        }

        return nodeResult;
    }

    private Object executeQuery(Map<String, Object> inputs) {
        // 获取数据库配置名称
        String databaseName = (String) inputs.get("databaseName");
        if (databaseName == null || databaseName.trim().isEmpty()) {
            throw new WorkflowException("数据库查询节点缺少数据库名称配置");
        }

        // 根据名称查找数据库配置
        SQLJdbc dbConfig = findDatabaseConfig(databaseName);
        if (dbConfig == null) {
            throw new WorkflowException("未找到数据库配置: " + databaseName);
        }

        // 获取查询语句
        String sql = (String) inputs.get("sql");
        if (sql == null || sql.trim().isEmpty()) {
            throw new WorkflowException("数据库查询节点缺少SQL语句");
        }

        // 验证SQL语句是否为查询语句
        if (!isSelectStatement(sql)) {
            throw new WorkflowException("仅允许执行SELECT查询语句");
        }

        // 创建查询请求对象
        QueryRequest request = new QueryRequest();
        request.setDatabaseConfig(dbConfig);
        request.setSql(sql);

        // 设置查询参数（如果有）
        if (inputs.containsKey("parameters")) {
            List< Object> o = new Gson().fromJson((String) inputs.get("parameters"), new TypeToken<List<Object>>() {
            }.getType());
            request.setParameters(o);
        }

        // 执行查询
        return databaseService.query(request);
    }

    private SQLJdbc findDatabaseConfig(String databaseName) {
        GlobalConfigurations configuration = ContextLoader.configuration;
        if (configuration != null && configuration.getStores() != null
                && configuration.getStores().getDatabase() != null) {

            for (SQLJdbc config : configuration.getStores().getDatabase()) {
                if (databaseName.equals(config.getName())) {
                    return config;
                }
            }
        }
        return null;
    }

    private boolean isSelectStatement(String sql) {
        // 移除开头的空白字符后检查是否以SELECT开头
        String trimmedSql = sql.trim().toLowerCase();
        return trimmedSql.startsWith("select");
    }
}
