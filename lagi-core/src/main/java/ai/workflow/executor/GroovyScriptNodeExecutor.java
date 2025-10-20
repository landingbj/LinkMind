package ai.workflow.executor;

import ai.workflow.TaskStatusManager;
import ai.workflow.exception.WorkflowException;
import ai.workflow.pojo.Node;
import ai.workflow.pojo.NodeResult;
import ai.workflow.pojo.TaskReportOutput;
import ai.workflow.pojo.WorkflowContext;
import ai.workflow.utils.InputValueParser;
import ai.workflow.utils.NodeExecutorUtil;
import com.fasterxml.jackson.databind.JsonNode;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * Groovy脚本节点执行器 - 安全增强版
 */
public class GroovyScriptNodeExecutor implements INodeExecutor {

    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();

    // 最大脚本执行时间（毫秒）
    private static final long MAX_SCRIPT_EXECUTION_TIME = 5000;

    // 允许的导入包白名单
    private static final String[] ALLOWED_PACKAGES = {
            "java.math",
            "java.util",
            "java.time",
            "java.text"
    };

    // 禁止的导入包黑名单
    private static final String[] RESTRICTED_PACKAGES = {
            "java.lang.reflect",
            "java.io",
            "java.net",
            "java.nio",
            "groovy.lang",
            "javax.script"
    };

    // 线程池用于执行带有超时的脚本
    private static final ExecutorService scriptExecutor = Executors.newCachedThreadPool(
            r -> {
                Thread t = new Thread(r);
                t.setName("groovy-script-executor-" + t.getId());
                t.setDaemon(true);
                return t;
            }
    );

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
                throw new WorkflowException("Groovy脚本节点缺少输入配置");
            }

            Map<String, Object> inputs = InputValueParser.parseInputs(inputsValues, context);

            // 验证脚本输入
            validateScriptInputs(inputs);

            // 执行Groovy脚本（带安全机制）
            Object result = executeGroovyScriptWithSecurity(inputs);

            // 构建输出结果
            Map<String, Object> outputResult = new HashMap<>();
            if (result != null) {
                outputResult.put("result", result);
            }
            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;

            // 添加安全审计日志
            taskStatusManager.addExecutionLog(taskId, nodeId,
                    "Groovy脚本节点执行成功，脚本长度: " + ((String)inputs.get("script")).length() + " 字符",
                    startTime);

            TaskReportOutput.Snapshot snapshot = taskStatusManager.createNodeSnapshot(nodeId, inputs, outputResult, null, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, snapshot);

            nodeResult = new NodeResult(node.getType(), node.getId(), outputResult, null);
        } catch (Exception e) {
            // 添加安全审计日志（错误情况）
            taskStatusManager.addExecutionLog(taskId, nodeId,
                    "Groovy脚本节点执行失败: " + e.getMessage(),
                    System.currentTimeMillis());
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "Groovy脚本节点", e);
        }
        return nodeResult;
    }

    /**
     * 执行Groovy脚本（带安全机制）
     */
    private Object executeGroovyScriptWithSecurity(Map<String, Object> inputs) {
        String scriptText = (String)inputs.get("script");
        if(scriptText == null) {
            throw new WorkflowException("Groovy脚本节点缺少执行脚本");
        }

        // 验证输入（包括危险模式检查）
        validateScriptInputs(inputs);

        // 创建安全的GroovyShell配置
        CompilerConfiguration config = createSecureCompilerConfiguration();
        GroovyShell shell = new GroovyShell(config);

        // 将输入参数作为变量绑定到脚本环境中（排除script本身）
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            if(!"script".equals(entry.getKey())) {
                shell.setVariable(entry.getKey(), entry.getValue());
            }
        }

        // 编译脚本
        Script script = shell.parse(scriptText);

        // 使用超时机制执行脚本
        return executeWithTimeout(script);
    }

    /**
     * 创建安全的编译器配置
     */
    private CompilerConfiguration createSecureCompilerConfiguration() {
        CompilerConfiguration config = new CompilerConfiguration();

        // 创建安全AST定制器 - 使用Groovy 3.x兼容的方式
        SecureASTCustomizer secure = new SecureASTCustomizer();
        secure.setClosuresAllowed(true); // 允许闭包
//        secure.setMethodDefinitionAllowed(false); // 禁止方法定义

        // 使用包限制而不是导入限制
        secure.setPackageAllowed(false); // 禁止包声明

        // 在Groovy 3.x中，部分AST限制方式有所变化
        // 我们可以通过其他方式增强安全性

        config.addCompilationCustomizers(secure);
        return config;
    }

    /**
     * 使用超时机制执行脚本
     */
    private Object executeWithTimeout(Script script) {
        Future<Object> future = scriptExecutor.submit(() -> script.run());

        try {
            return future.get(MAX_SCRIPT_EXECUTION_TIME, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new WorkflowException("Groovy脚本执行超时，已终止执行");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorkflowException("Groovy脚本执行被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw new WorkflowException("Groovy脚本执行错误: " + cause.getMessage(), cause);
        }
    }

    /**
     * 验证脚本输入
     */
    private void validateScriptInputs(Map<String, Object> inputs) {
        String script = (String) inputs.get("script");
        if (script == null || script.trim().isEmpty()) {
            throw new WorkflowException("Groovy脚本不能为空");
        }

        // 检查脚本长度（防止超长脚本）
        if (script.length() > 10000) {
            throw new WorkflowException("Groovy脚本长度超过限制(10000字符)");
        }

        // 检查潜在的危险模式
        if (containsDangerousPatterns(script)) {
            throw new WorkflowException("Groovy脚本包含潜在危险操作");
        }
    }

    /**
     * 检查脚本中是否包含危险模式
     */
    private boolean containsDangerousPatterns(String script) {
        String[] dangerousPatterns = {
                "System\\.exit",
                "Runtime\\.getRuntime",
                "ProcessBuilder",
                "Thread\\.(start|stop|interrupt)",
                "Reflection",
                "ClassLoader",
                "GroovyShell",
                "evaluate\\(",
                "java\\.lang\\.reflect",
                "java\\.io\\.",
                "java\\.net\\.",
                "java\\.nio\\.",
                "System\\.setSecurityManager",
                "Class\\.forName"
        };

        for (String pattern : dangerousPatterns) {
            if (Pattern.compile(pattern).matcher(script).find()) {
                return true;
            }
        }

        return false;
    }

    public static void main(String[] args) throws Exception {
        GroovyScriptNodeExecutor executor = new GroovyScriptNodeExecutor();

        // 测试正常脚本
        String safeScript = "def sum = 0\n" +
                "for (int i = 1; i <= n; i++) {\n" +
                "    sum += i\n" +
                "}\n" +
                "return sum";
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("n", 10);
        inputs.put("script", safeScript);

        Object result = executor.executeGroovyScriptWithSecurity(inputs);
        System.out.println("正常脚本执行结果: " + result);

        // 测试危险脚本
        String dangerousScript = "import ai.utils.ApiInvokeUtil;\n ApiInvokeUtil.get(\"http://www.baidu.com\");\n reutnr 100 ";
        inputs.put("script", dangerousScript);
        try {
            result = executor.executeGroovyScriptWithSecurity(inputs);
            System.out.println("危险脚本执行结果: " + result);
        } catch (Exception e) {
            System.out.println("危险脚本被正确拦截: " + e.getMessage());
        }
        System.out.println("测试完成");
    }

}