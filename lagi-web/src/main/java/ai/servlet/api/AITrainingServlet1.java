package ai.servlet.api;

import ai.common.exception.RRException;
import ai.config.ContextLoader;
import ai.dao.modelBaseDao;
import ai.dto.TrainTaskUpdateDTO;
import ai.finetune.service.TrainerService;
import ai.servlet.RestfulServlet;
import ai.servlet.annotation.Body;
import ai.servlet.annotation.Get;
import ai.servlet.annotation.Param;
import ai.servlet.annotation.Post;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;


import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * AI 模型训练任务管理 Servlet（通用版）
 * 支持任意 AI 模型的训练、评估、预测和导出
 * 包括但不限于：YOLOv8, YOLOv11, CenterNet, CRNN, HRNet, PIDNet, ResNet, OSNet等
 * 提供训练任务的完整生命周期管理和流式输出
 * 扩展性：
 * - 通过 trainerMap 注册新模型的 Trainer
 * - 支持动态模型类别和框架推断
 * - 无法推断的模型自动归为 "custom" 类别
 */
@Slf4j
public class AITrainingServlet1 extends RestfulServlet {

    private static final TrainerService trainerService = ContextLoader.getBean(TrainerService.class);
    private static final modelBaseDao modelDao = new modelBaseDao();

    @Post("start")
    public String start(@Body JSONObject config) {
        config.set("the_train_type", "train");
        if (trainerService != null) {
            trainerService.startTrainingTask(config);
            return "训练任务已提交";
        }
        throw new RRException("为找到对应的训练服务");
    }

    @Post("pause")
    public String pause(@Body String taskId) {
        if (trainerService != null) {
            return trainerService.pauseTask(taskId);
        }
        throw new RRException("为找到对应的训练服务");
    }

    @Post("resume")
    public String resume(@Param("taskId") String taskId, @Param("containerId") String containerId) {
        if (trainerService != null) {
            trainerService.resumeTask(StrUtil.isBlank(taskId)?containerId:taskId);
            return "任务已恢复";
        }
        throw new RRException("为找到对应的训练服务");
    }


    @Post("stop")
    public String stop(@Param("taskId") String taskId, @Param("containerId") String containerId) {
        if (trainerService != null) {
            return trainerService.stopTask(StrUtil.isBlank(taskId)?containerId:taskId);
        }
        throw new RRException("为找到对应的训练服务");
    }

    @Post("remove")
    public String remove(@Param("taskId") String taskId, @Param("containerId") String containerId) {
        if (trainerService != null) {
            return trainerService.removeTask(StrUtil.isBlank(taskId)?containerId:taskId);
        }
        throw new RRException("为找到对应的训练服务");
    }

    @Post("deleted")
    public String deleted(@Param("taskId") String taskId, @Param("containerId") String containerId) {
        if (trainerService != null) {
            return trainerService.removeTask(StrUtil.isBlank(taskId)?containerId:taskId);
        }
        throw new RRException("为找到对应的训练服务");
    }

    @Get("status")
    public String status(@Param("taskId") String taskId, @Param("containerId") String containerId) {
        if (trainerService != null) {
            return trainerService.getTaskStatus(StrUtil.isBlank(taskId)?containerId:taskId);
        }
        throw new RRException("为找到对应的训练服务");
    }

    @Get("logs")
    public String getLogs(@Param("taskId") String taskId, @Param("containerId") String containerId, @Param("lines") Integer lastLines) {
        if (trainerService != null) {
            return trainerService.getTaskLogs(StrUtil.isBlank(taskId)?containerId:taskId, lastLines);
        }
        throw new RRException("为找到对应的训练服务");
    }


    @Post("evaluate")
    public String evaluate(@Body JSONObject config) {
        config.set("the_train_type", "valuate");
        if (trainerService != null) {
            trainerService.startEvaluationTask(config);
            return "评估任务已提交";
        }
        throw new RRException("为找到对应的训练服务");
    }

    @Post("predict")
    public String predict(@Body JSONObject config) {
        config.set("the_train_type", "predict");
        if (trainerService != null) {
            trainerService.startPredictionTask(config);
            return "推理任务已提交";
        }
        throw new RRException("为找到对应的训练服务");
    }

    @Post("export")
    public String export(@Body JSONObject config) {
        config.set("the_train_type", "export");
        if (trainerService != null) {
            try {
                Future<String> stringFuture = trainerService.startConvertTask(config);
                return stringFuture.get();
            } catch (Exception e) {
                throw new RRException(e.getMessage());
            }
        }
        throw new RRException("为找到对应的训练服务");
    }


    @Get("list")
    public String list() {
        if (trainerService != null) {
            try {
                return trainerService.getRunningTaskInfo();
            } catch (Exception e) {
                throw new RRException(e.getMessage());
            }
        }
        throw new RRException("为找到对应的训练服务");
    }

    @Get("resources")
    public JSONObject resources(@Param("taskId") String taskId, @Param("containerId") String containerId) {
        if (trainerService != null) {
            try {
                return trainerService.getResourceInfo(StrUtil.isBlank(taskId)?containerId:taskId);
            } catch (Exception e) {
                throw new RRException(e.getMessage());
            }
        }
        throw new RRException("为找到对应的服务");
    }
    @Post("detail")
    public Map<String, Object> detail(@Body JSONObject object) {
        String taskId = object.get("task_id") != null ? object.get("task_id").toString() : null;
        if (StrUtil.isBlank(taskId)) {
            throw new RRException("task_id不能为空");
        }

        Map<String, Object> result = modelDao.getTaskDetailByTaskId(taskId);
        if (result == null) {
            throw new RRException("未找到对应的训练任务");
        }
        return result;
    }
    @Get("queryFramework")
    public List<Map<String, Object>> queryFramework() {
        return modelDao.queryFramework();
    }
    @Get("listModelsWithDetails")
    public Map<String, Object> listModelsWithDetails(
            @Param("page") Integer page,
            @Param("page_size") Integer pageSize,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("category_id") Long categoryId) {
        try {
            return modelDao.listModelsWithDetails(page, pageSize, keyword, status, categoryId);
        } catch (Exception e) {
            log.error("查询模型列表失败", e);
            throw new RRException("查询模型列表失败");
        }
    }
    @Get("queryModelCategory")
    public List<Map<String, Object>> queryModelCategory() {
        return modelDao.queryModelCategory();
    }
    @Get("queryModelType")
    public List<Map<String, Object>> queryModelType() {
        return modelDao.queryModelType();
    }

    @Get("getModelDetail")
    public Map<String, Object> getModelDetail(@Param("id") Long modelId) {
        // 参数校验
        if (modelId == null) {
            throw new RRException("模型ID不能为空");
        }

        try {
            Map<String, Object> modelDetail = modelDao.getModelDetail(modelId);
            if (modelDetail == null) {
                throw new RRException("模型不存在");
            }
            return modelDetail;
        } catch (Exception e) {
            log.error("查询模型详情失败", e);
            throw new RRException("查询模型详情失败: " + e.getMessage());
        }
    }
    @Post("deleteModel")
    public String deleteModel(@Param("id") Long modelId,@Body("id") JSONObject jsonObject ) {
        if (modelId == null) {
            Object idObj = jsonObject.get("id");
            if (idObj == null){
                throw new RRException("模型ID不能为空");
            }
            modelId = ((Integer) idObj).longValue();
        }

        try {
            boolean success = modelDao.deleteModel(modelId);
            if (success) {
                return "模型删除成功";
            } else {
                throw new RRException("删除失败，记录可能不存在或已被删除");
            }
        } catch (Exception e) {
            log.error("删除模型失败", e);
            throw new RRException("服务器内部错误: " + e.getMessage());
        }
    }
    @Post("updateModel")
    public String updateModel(@Param("id") Long modelId, @Body("model") JSONObject model) {
        // 验证必填参数 id
        if (modelId == null) {
            throw new RRException("模型ID不能为空");
        }

        try {
            boolean success = modelDao.updateModel(modelId, model);
            if (success) {
                return "模型更新成功";
            } else {
                throw new RRException("模型更新失败，记录可能不存在或已被删除");
            }
        } catch (Exception e) {
            log.error("更新模型失败", e);
            throw new RRException("服务器内部错误: " + e.getMessage());
        }
    }
    @Post("createModel")
    public String createModel(@Body("model") JSONObject model) {
        try {
            Long modelId = modelDao.createModel(model);
            if (modelId != null) {
                return "模型创建成功";
            } else {
                throw new RRException("模型创建失败");
            }
        } catch (Exception e) {
            log.error("创建模型失败", e);
            throw new RRException("服务器内部错误: " + e.getMessage());
        }
    }

    /**
     * 批量获取训练任务状态
     * 支持通过逗号拼接的容器ID或训练任务ID查询，并对ID进行去重
     *
     * @param containerIds 逗号拼接的容器ID，例如: "id1,id2,id3"
     * @param taskIds 逗号拼接的训练任务ID，例如: "id1,id2,id3"
     * @return 包含训练任务状态信息的Map列表
     */
    public static List<Map<String, Object>> batchGetTaskStatus(String containerIds, String taskIds) {
        if (trainerService != null) {
            // 合并并去重 containerIds 和 taskIds
            Set<String> uniqueIds = Stream.concat(
                            Optional.ofNullable(containerIds).stream().flatMap(s -> Arrays.stream(s.split(","))),
                            Optional.ofNullable(taskIds).stream().flatMap(s -> Arrays.stream(s.split(",")))
                    )
                    .map(String::trim)
                    .filter(id -> !id.isEmpty())
                    .collect(Collectors.toSet());

            List<Map<String, Object>> result = new ArrayList<>();
            for (String id : uniqueIds) {
                try {
                    String statusOutput = trainerService.getTaskStatus(id);
                    String[] parts = statusOutput != null ? statusOutput.split(";") : new String[]{"unknown"};
                    String resultStatus = parts[0].trim();
                    String exitCode = parts.length > 1 ? parts[1].trim() : "";

                    JSONObject statusJson = JSONUtil.createObj()
                        .put("status", resultStatus)
                        .put("output", statusOutput)
                        .put("containerStatus",
                            !exitCode.isEmpty() && exitCode.matches("\\d+") ?
                            (Integer.parseInt(exitCode) > 0 ? "failed" : resultStatus) : resultStatus);

                    if (!"error".equals(resultStatus)) {
                        String containerStatus = Optional.ofNullable(statusJson.getStr("containerStatus"))
                                .orElse(Optional.ofNullable(statusOutput).filter(o -> !o.isEmpty()).map(o -> o.trim().replace("\n", "")).orElse(resultStatus));
                        if (containerStatus != null && !containerStatus.isEmpty()) {
                            String finalStatus = "SuccessCriteriaMet".equals(containerStatus) ? "completed" : containerStatus;
                            modelDao.updateTaskStatus(id, finalStatus, null);
                        }
                    } else {
                        String errorMsg = statusJson.getStr("message", "获取状态失败");
                        log.warn("获取状态失败: id={}, error={}", id, errorMsg);
                    }

                    result.add(statusJson.toBean(Map.class));
                } catch (Exception e) {
                    log.error("处理ID {} 时发生异常", id, e);
                    throw new RRException("处理ID " + id + " 时发生异常: " + e.getMessage());
                }
            }
            return result;
        }
        throw new RRException("未找到对应的服务");
    }
    @Post("updateData")
    public String updateData(@Body JSONObject jsonBody) {
        try {
            String taskId = jsonBody.getStr("task_id");
            if (taskId == null || taskId.isEmpty()) {
                throw new RRException("taskId不能为空");
            }
            TrainTaskUpdateDTO updateDTO = JSONUtil.toBean(jsonBody, TrainTaskUpdateDTO.class);
            boolean modelId = modelDao.updateData(taskId,updateDTO);
            if (modelId) {
                return "训练任务更新成功";
            } else {
                throw new RRException("训练任务更新失败");
            }
        } catch (Exception e) {
            log.error("训练任务更新失败", e);
            throw new RRException("服务器内部错误: " + e.getMessage());
        }
    }


}
