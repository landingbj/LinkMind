package ai.finetune;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;

/**
 * Docker 容器训练抽象基类
 * 定义了训练任务的生命周期管理方法
 */
@Slf4j
@Getter
@Setter
public abstract class DockerTrainerAbstract {

    // SSH 远程服务器配置
    protected String sshHost;
    protected int sshPort;
    protected String sshUsername;
    protected String sshPassword;

    // Docker 配置
    protected String dockerImage;
    protected String volumeMount;
    protected boolean useRemote = true;

    // SSH 连接超时时间（毫秒）
    protected int sshTimeout = 300000; // 5分钟

    /**
     * 构造函数
     */
    public DockerTrainerAbstract() {
    }

    /**
     * 构造函数
     * @param sshHost SSH主机地址
     * @param sshPort SSH端口
     * @param sshUsername SSH用户名
     * @param sshPassword SSH密码
     */
    public DockerTrainerAbstract(String sshHost, int sshPort, String sshUsername, String sshPassword) {
        this.sshHost = sshHost;
        this.sshPort = sshPort;
        this.sshUsername = sshUsername;
        this.sshPassword = sshPassword;
    }

    /**
     * 设置远程服务器信息
     */
    public void setRemoteServer(String host, int port, String username, String password) {
        this.sshHost = host;
        this.sshPort = port;
        this.sshUsername = username;
        this.sshPassword = password;
        this.useRemote = true;
    }

    /**
     * 启动训练任务
     * @param taskId 任务ID
     * @param trackId 跟踪ID
     * @param config 训练配置
     * @return 执行结果（JSON字符串）
     */
    public abstract String startTraining(String taskId, String trackId, JSONObject config);

    /**
     * 暂停容器
     * @param containerId 容器ID或容器名称
     * @return 执行结果（JSON字符串）
     */
    public abstract String pauseContainer(String containerId);

    /**
     * 继续容器（恢复暂停的容器）
     * @param containerId 容器ID或容器名称
     * @return 执行结果（JSON字符串）
     */
    public abstract String resumeContainer(String containerId);

    /**
     * 停止容器
     * @param containerId 容器ID或容器名称
     * @return 执行结果（JSON字符串）
     */
    public abstract String stopContainer(String containerId);

    /**
     * 删除容器
     * @param containerId 容器ID或容器名称
     * @return 执行结果（JSON字符串）
     */
    public abstract String removeContainer(String containerId);

    /**
     * 执行评估任务
     * @param config 评估配置
     * @return 执行结果（JSON字符串）
     */
    public abstract String evaluate(JSONObject config);

    /**
     * 执行预测任务
     * @param config 预测配置
     * @return 执行结果（JSON字符串）
     */
    public abstract String predict(JSONObject config);

    /**
     * 导出模型
     * @param config 导出配置
     * @return 执行结果（JSON字符串）
     */
    public abstract String exportModel(JSONObject config);

    /**
     * 查看容器状态
     * @param containerId 容器ID或容器名称
     * @return 执行结果（JSON字符串）
     */
    public abstract String getContainerStatus(String containerId);

    /**
     * 查看容器日志
     * @param containerId 容器ID或容器名称
     * @param lines 显示最后多少行日志，默认100
     * @return 执行结果（JSON字符串）
     */
    public abstract String getContainerLogs(String containerId, int lines);

    /**
     * 上传文件到容器
     * @param containerId 容器ID或容器名称
     * @param localPath 本地文件路径
     * @param containerPath 容器内目标路径
     * @return 执行结果（JSON字符串）
     */
    public abstract String uploadToContainer(String containerId, String localPath, String containerPath);

    /**
     * 从容器下载文件
     * @param containerId 容器ID或容器名称
     * @param containerPath 容器内文件路径
     * @param localPath 本地目标路径
     * @return 执行结果（JSON字符串）
     */
    public abstract String downloadFromContainer(String containerId, String containerPath, String localPath);

    /**
     * 将容器提交为新镜像
     * @param containerId 容器ID或容器名称
     * @param imageName 新镜像名称
     * @param imageTag 新镜像标签
     * @return 执行结果（JSON字符串）
     */
    public abstract String commitContainerAsImage(String containerId, String imageName, String imageTag);

    // ==================== 公共方法 ====================

    /**
     * 执行远程命令
     * @param command 要执行的命令
     * @return 执行结果（JSON字符串）
     */
    protected String executeRemoteCommand(String command) {
        StringBuilder output = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();
        Session session = null;
        ChannelExec channelExec = null;

        try {
            // 创建 JSch 对象
            JSch jsch = new JSch();

            // 创建 SSH 会话
            log.info("连接到远程服务器: {}:{}", sshHost, sshPort);
            session = jsch.getSession(sshUsername, sshHost, sshPort);
            session.setPassword(sshPassword);

            // 配置 SSH 连接
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("server_host_key", "rsa-sha2-512,rsa-sha2-256,ssh-ed25519,ssh-rsa,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521");
            config.put("PubkeyAcceptedAlgorithms", "rsa-sha2-512,rsa-sha2-256,ssh-ed25519,ssh-rsa,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521");

            session.setConfig(config);
            session.setTimeout(sshTimeout);

            // 连接
            session.connect();
            log.info("SSH 连接成功");

            // 打开执行通道
            channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand(command);

            // 获取输入流和错误流
            InputStream in = channelExec.getInputStream();
            InputStream err = channelExec.getErrStream();

            // 执行命令
            channelExec.connect();
            log.info("执行远程命令: {}", command);

            // 读取标准输出
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.info("远程输出: {}", line);
                }
            }

            // 读取错误输出
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(err))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                    log.warn("远程错误输出: {}", line);
                }
            }

            // 获取退出状态
            int exitCode = channelExec.getExitStatus();
            log.info("远程命令退出码: {}", exitCode);

            JSONObject result = new JSONObject();
            if (exitCode == 0) {
                result.put("status", "success");
                result.put("message", "远程任务执行成功");
                result.put("output", output.toString());
                result.put("server", sshHost + ":" + sshPort);
            } else {
                result.put("status", "error");
                result.put("message", "远程任务执行失败");
                result.put("exitCode", exitCode);
                result.put("output", output.toString());
                result.put("error", errorOutput.toString());
                result.put("server", sshHost + ":" + sshPort);
            }

            return result.toString();

        } catch (Exception e) {
            log.error("SSH 执行命令失败", e);
            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "SSH连接或执行失败");
            errorResult.put("error", e.getMessage());
            errorResult.put("server", sshHost + ":" + sshPort);
            return errorResult.toString();
        } finally {
            // 关闭连接
            if (channelExec != null && channelExec.isConnected()) {
                channelExec.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
                log.info("SSH 连接已关闭");
            }
        }
    }

    /**
     * 测试 SSH 连接是否正常
     */
    public String testConnection() {
        return executeRemoteCommand("echo 'SSH connection test successful'");
    }

    // ==================== 工具方法 ====================

    /**
     * 判断任务执行结果是否成功
     */
    public static boolean isSuccess(String resultJson) {
        try {
            JSONObject json = JSONUtil.parseObj(resultJson);
            return "success".equals(json.getStr("status"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取任务执行的退出码
     */
    public static Integer getExitCode(String resultJson) {
        try {
            JSONObject json = JSONUtil.parseObj(resultJson);
            return json.getInt("exitCode");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取任务执行的错误信息
     */
    public static String getErrorMessage(String resultJson) {
        try {
            JSONObject json = JSONUtil.parseObj(resultJson);
            String error = json.getStr("error");
            String message = json.getStr("message");
            if (error != null && !error.isEmpty()) {
                return message + ": " + error;
            }
            return message;
        } catch (Exception e) {
            return "解析结果失败";
        }
    }

    /**
     * 获取任务执行的输出内容
     */
    public static String getOutput(String resultJson) {
        try {
            JSONObject json = JSONUtil.parseObj(resultJson);
            return json.getStr("output");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 生成随机的 Task ID
     */
    public static String generateTaskId() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String timestamp = sdf.format(new Date());
        String uuidPart = UUID.randomUUID().toString().substring(0, 8);
        return "task_" + timestamp + "_" + uuidPart;
    }

    /**
     * 生成随机的 Track ID
     */
    public static String generateTrackId() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String timestamp = sdf.format(new Date());
        String uuidPart = UUID.randomUUID().toString().substring(0, 8);
        return "track_" + timestamp + "_" + uuidPart;
    }

    /**
     * 生成容器名称
     */
    public static String generateContainerName(String prefix) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String timestamp = sdf.format(new Date());
        String uuidPart = UUID.randomUUID().toString().substring(0, 8);
        return prefix + "_" + timestamp + "_" + uuidPart;
    }
}

