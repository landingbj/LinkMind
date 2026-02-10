package ai.servlet.api;

import ai.common.exception.RRException;
import ai.config.ContextLoader;
import ai.finetune.service.TrainerService;
import ai.servlet.RestfulServlet;
import ai.servlet.annotation.Body;
import ai.servlet.annotation.Get;
import ai.servlet.annotation.Param;
import ai.servlet.annotation.Post;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import lombok.extern.slf4j.Slf4j;


import java.util.concurrent.*;

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

    private final TrainerService trainerService = ContextLoader.getBean(TrainerService.class);

    @Post("start")
    public String start(@Body("config") JSONObject config) {
        config.set("the_train_type", "train");
        if (trainerService != null) {
            trainerService.startTrainingTask(config);
            return "训练任务已提交";
        }
        throw new RRException("为找到对应的训练服务");
    }

    @Post("pause")
    public String pause(@Body("taskId") String taskId) {
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
    public String evaluate(@Body("config") JSONObject config) {
        config.set("the_train_type", "evaluate");
        if (trainerService != null) {
            trainerService.startEvaluationTask(config);
            return "评估任务已提交";
        }
        throw new RRException("为找到对应的训练服务");
    }

    @Post("predict")
    public String predict(@Body("config") JSONObject config) {
        config.set("the_train_type", "predict");
        if (trainerService != null) {
            trainerService.startPredictionTask(config);
            return "推理任务已提交";
        }
        throw new RRException("为找到对应的训练服务");
    }

    @Post("export")
    public String export(@Body("config") JSONObject config) {
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



}
