package ai.servlet.api;

import ai.config.ContextLoader;
import ai.config.pojo.DiscriminativeModelsConfig;
import ai.database.impl.MysqlAdapter;
import ai.servlet.BaseServlet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
public class DatasetServlet extends BaseServlet {

    private static volatile MysqlAdapter mysqlAdapter = null;
    private static MysqlAdapter getMysqlAdapter() {
        if (mysqlAdapter == null) {
            synchronized (DatasetServlet.class) {
                if (mysqlAdapter == null) {
                    mysqlAdapter = new MysqlAdapter("mysql");
                }
            }
        }
        return mysqlAdapter;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);


        if (method.equals("lists")) {
            getDatasetList(req,resp);
        }else if (method.equals("upload")){
            uploadDataset(req, resp);
        }else {
            {
                resp.setStatus(404);
                Map<String, String> error = new HashMap<>();
                error.put("error", "接口不存在");
                responsePrint(resp, toJson(error));
            }
        }
    }


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doGet(req, resp);
    }


    /**
     * 获取数据集列表
     * @param req
     * @param resp
     * @throws IOException
     */
    private void getDatasetList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> response = new HashMap<>();
        try {
            String userId = req.getParameter("user_id");

            // 查询数据集列表
            List<Map<String, Object>> datasetList = getDatasetsFromDB(userId);

            response.put("status", "SUCCESS");
            response.put("data", datasetList);
            response.put("code", 200);

            resp.setStatus(200);
            responsePrint(resp, toJson(response));

        } catch (Exception e) {
            resp.setStatus(500);
            response.put("error", "服务器内部错误：" + e.getMessage());
            response.put("code", 500);
            responsePrint(resp, toJson(response));
        }
    }

    private List<Map<String, Object>> getDatasetsFromDB(String userId) {

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM dataset_records ");

        List<Object> params = new ArrayList<>();

        if (userId != null && !userId.isEmpty()) {
            sql.append("WHERE user_id = ? ");
            params.add(userId);
        }
        try {
            List<Map<String, Object>> datasetsRecordsList = getMysqlAdapter().select(sql.toString(), params.toArray());
            return datasetsRecordsList;
        } catch (Exception e) {
            throw new RuntimeException("查询数据集列表失败：" + e.getMessage(), e);
        }
    }

    /**
     * 上传预训练数据集
     * @param req
     * @param resp
     * @throws IOException
     */
    public void uploadDataset(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 检查请求是否为multipart/form-data格式
            if (!ServletFileUpload.isMultipartContent(req)) {
                resp.setStatus(400);
                response.put("status", "failed");
                response.put("message", "请求必须是multipart/form-data格式");
                response.put("code", 400);
                responsePrint(resp, toJson(response));
                return;
            }

            // 解析multipart请求
            DiskFileItemFactory factory = new DiskFileItemFactory();
            ServletFileUpload upload = new ServletFileUpload(factory);
            @SuppressWarnings("unchecked")
            List<FileItem> items = upload.parseRequest(req);

            String datasetName = null;
            String description = null;
            String userId = null;
            FileItem fileItem = null;

            // 解析表单字段
            for (FileItem item : items) {
                if (item.isFormField()) {
                    String fieldName = item.getFieldName();
                    String fieldValue = item.getString("UTF-8");
                    
                    switch (fieldName) {
                        case "dataset_name":
                            datasetName = fieldValue;
                            break;
                        case "description":
                            description = fieldValue;
                            break;
                        case "user_id":
                            userId = fieldValue;
                            break;
                    }
                } else {
                    fileItem = item;
                }
            }

            // 验证必填字段
            if (datasetName == null || datasetName.trim().isEmpty()) {
                resp.setStatus(400);
                response.put("status", "failed");
                response.put("message", "数据集名称不能为空");
                response.put("code", 400);
                responsePrint(resp, toJson(response));
                return;
            }

            if (fileItem == null || fileItem.getSize() == 0) {
                resp.setStatus(400);
                response.put("status", "failed");
                response.put("message", "请选择要上传的文件");
                response.put("code", 400);
                responsePrint(resp, toJson(response));
                return;
            }

            // 获取K8s PVC挂载路径（优先从系统属性读取，其次从环境变量读取，再次从配置文件读取，最后使用默认路径）
            String pvcMountPath = getPvcMountPath();
            if (pvcMountPath == null || pvcMountPath.trim().isEmpty()) {
                resp.setStatus(500);
                response.put("status", "failed");
                response.put("message", "PVC挂载路径获取失败，请检查配置");
                response.put("code", 500);
                responsePrint(resp, toJson(response));
                return;
            }

            String fileName = fileItem.getName();
            // 确保文件名唯一，添加时间戳
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String uniqueFileName = timestamp + "_" + fileName;
            Path targetDir = Paths.get(pvcMountPath);
            Path targetFilePath = targetDir.resolve(uniqueFileName);

            // 保存文件到PVC挂载目录
            InputStream fileInputStream = null;
            try {
                // 确保目录存在
                Files.createDirectories(targetDir);
                log.info("数据集文件保存目录已创建或已存在: {}", targetDir);

                // 读取文件输入流并保存到本地
                fileInputStream = fileItem.getInputStream();
                Files.copy(fileInputStream, targetFilePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                log.info("数据集文件已保存到PVC挂载路径: {}", targetFilePath);
            } catch (IOException e) {
                log.error("保存文件到PVC挂载目录时发生异常，挂载路径: {}, 错误信息: {}", pvcMountPath, e.getMessage(), e);
                e.printStackTrace();
                resp.setStatus(500);
                response.put("status", "failed");
                response.put("message", "文件保存失败，请检查PVC挂载路径是否正确: " + e.getMessage());
                response.put("code", 500);
                responsePrint(resp, toJson(response));
                return;
            } finally {
                // 确保输入流关闭（确保资源释放）
                if (fileInputStream != null) {
                    try {
                        fileInputStream.close();
                    } catch (IOException e) {
                        log.warn("关闭文件输入流时发生异常: {}", e.getMessage(), e);
                        e.printStackTrace();
                    }
                }
            }

            // 保存文件信息到数据库
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            long fileSize = fileItem.getSize();
            
            // 计算样本数量（这里可以根据实际需求实现，暂时设为0或null）
            Integer sampleCount = null; // 可以根据文件内容解析，暂时设为null
            
            // 使用完整的PVC挂载路径作为dataset_path
            String datasetPath = targetFilePath.toString();
            saveDatasetRecord(datasetName, datasetPath, description, userId, fileSize, sampleCount, currentTime);

            // 返回成功响应
            resp.setStatus(200);
            response.put("status", "success");
            response.put("message", "数据集上传成功");
            response.put("code", 200);
            Map<String, Object> data = new HashMap<>();
            data.put("dataset_name", datasetName);
            data.put("dataset_path", datasetPath);
            data.put("file_size", fileSize);
            data.put("created_at", currentTime);
            response.put("data", data);
            responsePrint(resp, toJson(response));

        } catch (Exception e) {
            log.error("上传数据集时发生异常: {}", e.getMessage(), e);
            e.printStackTrace();
            resp.setStatus(500);
            response.put("status", "failed");
            response.put("message", "上传失败: " + e.getMessage());
            response.put("code", 500);
            responsePrint(resp, toJson(response));
        }
    }

    /**
     * 获取K8s PVC挂载路径
     * 优先级：1. 系统属性 2. 环境变量 3. 配置文件（从训练任务的volume_mount中提取） 4. 默认路径
     */
    private String getPvcMountPath() {
        // 1. 优先从系统属性读取
        String mountPath = System.getProperty("dataset.pvc.mount.path");
        if (mountPath != null && !mountPath.trim().isEmpty()) {
            log.info("从系统属性读取PVC挂载路径: {}", mountPath);
            return mountPath.trim();
        }
        
        // 2. 其次从环境变量读取
        mountPath = System.getenv("DATASET_PVC_MOUNT_PATH");
        if (mountPath != null && !mountPath.trim().isEmpty()) {
            log.info("从环境变量读取PVC挂载路径: {}", mountPath);
            return mountPath.trim();
        }
        
        // 3. 尝试从配置文件读取（从训练任务的volume_mount配置中提取主机路径）
        mountPath = getMountPathFromConfig();
        if (mountPath != null && !mountPath.trim().isEmpty()) {
            log.info("从配置文件读取PVC挂载路径: {}", mountPath);
            return mountPath.trim();
        }
        
        // 4. 使用默认路径（与训练任务保持一致）
        String defaultPath = "/mnt/k8s_data/wangshuanglong/datasets";
        log.warn("PVC挂载路径未配置，使用默认路径: {}。如需修改，请设置系统属性dataset.pvc.mount.path或环境变量DATASET_PVC_MOUNT_PATH", defaultPath);
        return defaultPath;
    }
    
    /**
     * 从配置文件（lagi.yml）中读取volume_mount配置，提取主机路径部分
     * 查找顺序：yolo.docker.volume_mount -> deeplab.docker.volume_mount -> tracknetv3.docker.volume_mount
     */
    private String getMountPathFromConfig() {
        try {
            ContextLoader.loadContext();
            if (ContextLoader.configuration == null ||
                ContextLoader.configuration.getModelPlatformConfig() == null ||
                ContextLoader.configuration.getModelPlatformConfig().getDiscriminativeModelsConfig() == null) {
                return null;
            }
            
            DiscriminativeModelsConfig discriminativeConfig = 
                ContextLoader.configuration.getModelPlatformConfig().getDiscriminativeModelsConfig();
            
            // 尝试从 yolo 配置读取
            if (discriminativeConfig.getYolo() != null && 
                discriminativeConfig.getYolo().getDocker() != null) {
                String volumeMount = discriminativeConfig.getYolo().getDocker().getVolumeMount();
                if (volumeMount != null && volumeMount.contains(":")) {
                    String[] parts = volumeMount.split(":");
                    if (parts.length >= 2) {
                        String hostPath = parts[0].trim();
                        // 在主机路径下添加 datasets 子目录
                        return hostPath + "/datasets";
                    }
                }
            }
            
            // 尝试从 deeplab 配置读取
            if (discriminativeConfig.getDeeplab() != null && 
                discriminativeConfig.getDeeplab().getDocker() != null) {
                String volumeMount = discriminativeConfig.getDeeplab().getDocker().getVolumeMount();
                if (volumeMount != null && volumeMount.contains(":")) {
                    String[] parts = volumeMount.split(":");
                    if (parts.length >= 2) {
                        String hostPath = parts[0].trim();
                        return hostPath + "/datasets";
                    }
                }
            }
            
            // 尝试从 tracknetv3 配置读取
            if (discriminativeConfig.getTracknetv3() != null && 
                discriminativeConfig.getTracknetv3().getDocker() != null) {
                String volumeMount = discriminativeConfig.getTracknetv3().getDocker().getVolumeMount();
                if (volumeMount != null && volumeMount.contains(":")) {
                    String[] parts = volumeMount.split(":");
                    if (parts.length >= 2) {
                        String hostPath = parts[0].trim();
                        return hostPath + "/datasets";
                    }
                }
            }
        } catch (Exception e) {
            log.debug("从配置文件读取volume_mount失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 保存数据集记录到数据库
     */
    private void saveDatasetRecord(String datasetName, String datasetPath, String description, 
                                  String userId, long fileSize, Integer sampleCount, 
                                  String createdAt) {
        try {
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            StringBuilder sql = new StringBuilder();
            sql.append("INSERT INTO dataset_records ");
            sql.append("(dataset_name, dataset_path, created_at, updated_at, file_size, ");
            sql.append("sample_count, description, user_id, status) ");
            sql.append("VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'active')");
            
            List<Object> params = new ArrayList<>();
            params.add(datasetName);
            params.add(datasetPath);
            params.add(createdAt != null ? createdAt : currentTime);
            params.add(currentTime);
            params.add(fileSize);
            params.add(sampleCount);
            params.add(description);
            params.add(userId);
            
            int result = getMysqlAdapter().executeUpdate(sql.toString(), params.toArray());
            
            if (result > 0) {
                log.info("数据集记录保存成功: " + datasetName);
            } else {
                log.info("数据集记录保存失败: " + datasetName);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("保存数据集记录失败: " + e.getMessage(), e);
        }
    }
}
