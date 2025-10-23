package ai.workflow.executor;

import ai.config.ContextLoader;
import ai.config.GlobalConfigurations;
import ai.database.DatabaseOperationService;
import ai.database.pojo.SQLJdbc;
import ai.database.pojo.UpdateRequest;
import ai.workflow.TaskStatusManager;
import ai.workflow.exception.WorkflowException;
import ai.workflow.pojo.Node;
import ai.workflow.pojo.NodeResult;
import ai.workflow.pojo.TaskReportOutput;
import ai.workflow.pojo.WorkflowContext;
import ai.workflow.utils.InputValueParser;
import ai.workflow.utils.NodeExecutorUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据库更新/插入节点执行器
 * 仅支持INSERT和UPDATE操作，防止SQL注入
 */
public class DatabaseUpdateNodeExecutor implements INodeExecutor {

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
                throw new WorkflowException("数据库更新节点缺少输入配置");
            }

            // 解析输入值
            Map<String, Object> inputs = InputValueParser.parseInputs(inputsValues, context);

            // 执行数据库更新/插入
            Object result = executeUpdate(inputs);

            Map<String, Object> output = new HashMap<>();
            output.put("result", result);

            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            TaskReportOutput.Snapshot nodeSnapshot = taskStatusManager.createNodeSnapshot(nodeId, inputs, output, null, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, nodeSnapshot);
            taskStatusManager.addExecutionLog(taskId, nodeId, "数据库更新节点执行成功", startTime);
            nodeResult = new NodeResult(node.getType(), node.getId(), output, null);

        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "数据库更新节点", e);
        }

        return nodeResult;
    }

    private Object executeUpdate(Map<String, Object> inputs) {
        // 获取数据库配置名称
        String databaseName = (String) inputs.get("databaseName");
        if (databaseName == null || databaseName.trim().isEmpty()) {
            throw new WorkflowException("数据库更新节点缺少数据库名称配置");
        }

        // 根据名称查找数据库配置
        SQLJdbc dbConfig = findDatabaseConfig(databaseName);
        if (dbConfig == null) {
            throw new WorkflowException("未找到数据库配置: " + databaseName);
        }

        // 获取操作类型
        String operationType = (String) inputs.get("operationType");
        if (operationType == null || (!"insert".equalsIgnoreCase(operationType)
                && !"update".equalsIgnoreCase(operationType))) {
            throw new WorkflowException("操作类型必须为'insert'或'update'");
        }

        // 获取表名
        String tableName = (String) inputs.get("tableName");
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new WorkflowException("数据库更新节点缺少表名");
        }

        // 获取数据
        Map<String, Object> data = new Gson().fromJson((String) inputs.get("data"), new TypeToken<Map<String, Object>>() {
        }.getType());
        if (data == null || data.isEmpty()) {
            throw new WorkflowException("数据库更新节点缺少数据");
        }

        // 验证数据字段名是否合法
        validateFieldNames(data);

        // 创建更新请求对象
        UpdateRequest request = new UpdateRequest();
        request.setDatabaseConfig(dbConfig);
        request.setOperationType(operationType.toLowerCase());
        request.setTableName(tableName);
        request.setData(data);

        // 设置WHERE条件（仅用于UPDATE操作）
        if ("update".equalsIgnoreCase(operationType) && inputs.containsKey("where")) {
            Map<String, Object> where = new Gson().fromJson((String) inputs.get("where"), new TypeToken<Map<String, Object>>() {
            }.getType());
            validateFieldNames(where);
            request.setWhere(where);
        }

        // 执行更新/插入操作
        return databaseService.update(request);
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

    private void validateFieldNames(Map<String, Object> fields) {
        if (fields != null) {
            for (String fieldName : fields.keySet()) {
                // 检查字段名是否包含非法字符
                if (!fieldName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                    throw new WorkflowException("非法字段名: " + fieldName);
                }
            }
        }
    }
}
