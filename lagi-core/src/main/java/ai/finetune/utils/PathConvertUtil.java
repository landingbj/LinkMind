package ai.finetune.utils;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 路径转换工具类：用于容器路径与宿主机路径之间的转换
 *
 * 使用场景：
 * - Docker/K8s 容器中路径转换为宿主机路径
 * - 根据挂载规则（volumeMount）进行路径映射
 */
@Slf4j
public final class PathConvertUtil {

    /**
     * 将容器内的路径转换为宿主机路径
     *
     * @param containerPath 容器内的路径（例如：/app/logs/convert.log）
     * @param volumeMount   挂载路径规则，格式为 "宿主机路径:容器路径"（例如：/host/data:/app/data）
     * @return 宿主机路径，如果转换失败则返回 null
     */
    public static String convertToHostPath(String containerPath, String volumeMount) {
        // 参数校验
        if (containerPath == null || containerPath.trim().isEmpty()) {
            log.warn("容器路径为空，无法转换");
            return containerPath;
        }

        if (volumeMount == null || volumeMount.trim().isEmpty()) {
            log.warn("挂载规则为空，无法转换");
            return containerPath;
        }

        // 检查挂载规则格式（必须包含冒号分隔符）
        if (!volumeMount.contains(":")) {
            log.warn("挂载规则格式错误，应为 '宿主机路径:容器路径'，当前值: {}", volumeMount);
            return containerPath;
        }

        try {
            // 解析挂载规则
            String[] mountParts = volumeMount.split(":");
            if (mountParts.length < 2) {
                log.warn("挂载规则格式错误，无法解析，当前值: {}", volumeMount);
                return containerPath;
            }

            String hostPath = mountParts[0].trim();
            String mountContainerPath = mountParts[1].trim();

            // 检查容器路径是否以挂载的容器路径开头
            if (!containerPath.startsWith(mountContainerPath)) {
                log.debug("容器路径 '{}' 不以挂载路径 '{}' 开头，无法转换", containerPath, mountContainerPath);
                return containerPath;
            }

            // 执行路径替换
            String hostFilePath = containerPath.replace(mountContainerPath, hostPath);
            log.debug("路径转换成功: 容器路径 '{}' -> 宿主机路径 '{}'", containerPath, hostFilePath);
            return hostFilePath;

        } catch (Exception e) {
            log.error("路径转换时发生异常: containerPath={}, volumeMount={}", containerPath, volumeMount, e);
            return containerPath;
        }
    }

    /**
     * 从 JSON 格式字符串中提取 exported_path 路径
     *
     * @param jsonString JSON 格式的字符串，包含 output 字段
     * @return 提取到的 exported_path 路径，如果提取失败则返回 null
     */
    public static String extractExportPath(String jsonString) {
        // 参数校验
        if (jsonString == null || jsonString.trim().isEmpty()) {
            log.warn("输入的 JSON 字符串为空，无法提取 exported_path");
            return null;
        }

        try {
            // 解析 JSON 字符串
            JSONObject jsonObject = JSONUtil.parseObj(jsonString);
            
            // 获取 output 字段
            Object outputObj = jsonObject.get("output");
            if (outputObj == null) {
                log.warn("JSON 中未找到 'output' 字段");
                return null;
            }

            String output = outputObj.toString();

            // 使用正则表达式提取 exported_path 后面的路径
            // 匹配模式：exported_path=/path/to/file 或 export complete; exported_path=/path/to/file
            Pattern pattern = Pattern.compile("exported_path=([^\\s\\n]+)");
            Matcher matcher = pattern.matcher(output);

            if (matcher.find()) {
                String exportPath = matcher.group(1);
                log.debug("成功提取 exported_path: {}", exportPath);
                return exportPath;
            } else {
                log.warn("在 output 中未找到 exported_path 信息");
                return null;
            }

        } catch (Exception e) {
            log.error("提取 exported_path 时发生异常: jsonString={}", jsonString, e);
            return null;
        }
    }


    public static Long getFileSize(String hostPath) {
        try {
            File file = new File(hostPath);
            return file.length();
        } catch (Exception e) {
            log.error("获取文件大小失败: hostPath={}", hostPath, e);
            return 0L;
        }
    }




}
