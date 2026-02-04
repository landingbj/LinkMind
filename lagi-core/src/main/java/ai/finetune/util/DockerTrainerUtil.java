package ai.finetune.util;

import ai.finetune.SSHConnectionManager;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

/**
 * Docker 容器训练工具类
 * 提供 Docker 容器操作和 SSH 远程命令执行的工具方法
 */
@Slf4j
public class DockerTrainerUtil {

    /**
     * 暂停容器
     * @param sshHost SSH主机地址
     * @param sshPort SSH端口
     * @param sshUsername SSH用户名
     * @param sshPassword SSH密码
     * @param containerId 容器ID或容器名称
     * @return 执行结果（JSON字符串）
     */
    public static String pauseContainer(String sshHost, int sshPort, String sshUsername, String sshPassword, String containerId) {
        String command = "docker pause " + containerId;
        log.info("暂停容器: {}", containerId);
        return executeRemoteCommand(sshHost, sshPort, sshUsername, sshPassword, command);
    }

    /**
     * 继续容器（恢复暂停的容器）
     * @param sshHost SSH主机地址
     * @param sshPort SSH端口
     * @param sshUsername SSH用户名
     * @param sshPassword SSH密码
     * @param containerId 容器ID或容器名称
     * @return 执行结果（JSON字符串）
     */
    public static String resumeContainer(String sshHost, int sshPort, String sshUsername, String sshPassword, String containerId) {
        String command = "docker unpause " + containerId;
        log.info("恢复容器: {}", containerId);
        return executeRemoteCommand(sshHost, sshPort, sshUsername, sshPassword, command);

    }

    /**
     * 停止容器
     * @param sshHost SSH主机地址
     * @param sshPort SSH端口
     * @param sshUsername SSH用户名
     * @param sshPassword SSH密码
     * @param containerId 容器ID或容器名称
     * @return 执行结果（JSON字符串）
     */
    public static String stopContainer(String sshHost, int sshPort, String sshUsername, String sshPassword, String containerId) {
        String command = "docker stop " + containerId;
        log.info("停止容器: {}", containerId);
        return executeRemoteCommand(sshHost, sshPort, sshUsername, sshPassword, command);
    }

    /**
     * 删除容器
     * @param sshHost SSH主机地址
     * @param sshPort SSH端口
     * @param sshUsername SSH用户名
     * @param sshPassword SSH密码
     * @param containerId 容器ID或容器名称
     * @return 执行结果（JSON字符串）
     */
    public static String removeContainer(String sshHost, int sshPort, String sshUsername, String sshPassword, String containerId) {
            String command = "docker rm -f " + containerId;
            log.info("删除容器: {}", containerId);
            return executeRemoteCommand(sshHost, sshPort, sshUsername, sshPassword, command);
    }

    /**
     * 查看容器状态
     * @param sshHost SSH主机地址
     * @param sshPort SSH端口
     * @param sshUsername SSH用户名
     * @param sshPassword SSH密码
     * @param containerId 容器ID或容器名称
     * @return 执行结果
     */
    public static String getContainerStatus(String sshHost, int sshPort, String sshUsername, String sshPassword, String containerId) {
        String command = "docker inspect --format='{{.State.Status}};{{.State.ExitCode}}' " + containerId;
        log.info("查询容器状态: {}", containerId);
        return executeRemoteCommand(sshHost, sshPort, sshUsername, sshPassword, command);
    }

    /**
     * 查看容器日志
     * @param sshHost SSH主机地址
     * @param sshPort SSH端口
     * @param sshUsername SSH用户名
     * @param sshPassword SSH密码
     * @param containerId 容器ID或容器名称
     * @param lines 显示最后多少行日志，默认100
     * @return 执行结果（JSON字符串）
     */
    public static String getContainerLogs(String sshHost, int sshPort, String sshUsername, String sshPassword, String containerId, int lines) {
        String command = "docker logs --tail " + lines + " " + containerId;
        log.info("查询容器日志: {}, 显示最后{}行", containerId, lines);
        return executeRemoteCommand(sshHost, sshPort, sshUsername, sshPassword, command);
    }




    public static String executeRemoteCommand(String sshHost, int sshPort, String sshUsername, String sshPassword, String command) {
        return executeRemoteCommand(sshHost, sshPort, sshUsername, sshPassword, command, null);
    }


    /**
     * 执行远程命令（使用连接池复用SSH连接）
     * @param sshHost SSH主机地址
     * @param sshPort SSH端口
     * @param sshUsername SSH用户名
     * @param sshPassword SSH密码
     * @param command 要执行的命令
     * @return 执行结果（JSON字符串）
     */
    public static <T, R> String executeRemoteCommand(String sshHost, int sshPort, String sshUsername, String sshPassword, String command, Function<T, R> callback) {
        StringBuilder output = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();
        Session session = null;
        ChannelExec channelExec = null;

        try {
            // 使用SSH连接管理器获取或创建连接（复用连接池）
            SSHConnectionManager connectionManager = SSHConnectionManager.getInstance();
            session = connectionManager.getSession(sshHost, sshPort, sshUsername, sshPassword);
            log.debug("使用SSH连接池中的连接: {}:{}", sshHost, sshPort);

            // 打开执行通道
            channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand(command);

            // 获取输入流和错误流
            InputStream in = channelExec.getInputStream();
            InputStream err = channelExec.getErrStream();

            // 执行命令
            channelExec.connect();
            log.debug("执行远程命令: {}", command);

            if(callback != null) {
                callback.apply(null);
            }

            // 读取标准输出
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.debug("远程输出: {}", line);
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
            log.debug("远程命令退出码: {}", exitCode);
            if (exitCode == 0) {
                return output.toString();
            }
            throw new RuntimeException(errorOutput.toString());
        } catch (JSchException | IOException e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            // 只关闭ChannelExec，不关闭Session（保留在连接池中复用）
            if (channelExec != null && channelExec.isConnected()) {
                channelExec.disconnect();
            }
        }
    }

    /**
     * 测试 SSH 连接是否正常
     * @param sshHost SSH主机地址
     * @param sshPort SSH端口
     * @param sshUsername SSH用户名
     * @param sshPassword SSH密码
     * @return 执行结果（JSON字符串）
     */
    public static String testConnection(String sshHost, int sshPort, String sshUsername, String sshPassword) {
        return executeRemoteCommand(sshHost, sshPort, sshUsername, sshPassword, "echo 'SSH connection test successful'");
    }

    /**
     * 列出所有训练容器（通用 Docker 实现）
     * 约定：训练容器名称包含 "train" 片段，例如 yolo_train_xxx、deeplab_train_xxx。
     * @param sshHost SSH主机地址
     * @param sshPort SSH端口
     * @param sshUsername SSH用户名
     * @param sshPassword SSH密码
     * @return 执行结果（JSON字符串）
     */
    public static String listTrainingContainers(String sshHost, int sshPort, String sshUsername, String sshPassword) {
        String command = "docker ps -a --filter name=train --format 'table {{.ID}}\\t{{.Names}}\\t{{.Image}}\\t{{.Status}}\\t{{.CreatedAt}}'";
        log.info("列出所有训练容器 (Docker)");
        return executeRemoteCommand(sshHost, sshPort, sshUsername, sshPassword, command);
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
