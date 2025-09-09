package ai.servlet.passenger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步数据库服务管理器
 * 负责管理所有数据库保存操作的异步执行，确保不影响主流程性能
 */
public class AsyncDbServiceManager {

    private static final Logger logger = LoggerFactory.getLogger(AsyncDbServiceManager.class);

    // 异步执行线程池
    private final ExecutorService asyncExecutor;

    // 各类数据库服务实例
    private final BusCardSwipeDbService busCardSwipeDbService;
    private final BusArriveLeaveDbService busArriveLeaveDbService;
    private final OpenCloseDoorMsgDbService openCloseDoorMsgDbService;
    private final RetrieveDownUpMsgDbService retrieveDownUpMsgDbService;
    private final RetrieveLoadFactorMsgDbService retrieveLoadFactorMsgDbService;
    private final RetrieveAllMsgDbService retrieveAllMsgDbService;
    private final RetrieveAllWsDbService retrieveAllWsDbService;

    // 性能统计
    private final AtomicLong totalSubmitted = new AtomicLong(0);
    private final AtomicLong totalCompleted = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);

    // 保存开关控制
    private volatile boolean saveCardSwipeDataEnabled = true;
    private volatile boolean saveArriveLeaveDataEnabled = true;
    private volatile boolean saveOpenCloseDoorMsgEnabled = true;
    private volatile boolean saveDownUpMsgEnabled = true;
    private volatile boolean saveLoadFactorMsgEnabled = true;
    private volatile boolean saveAllMessageEnabled = true;
    private volatile boolean saveAllWebSocketMessageEnabled = true;

    // 单例模式
    private static volatile AsyncDbServiceManager instance;

    private AsyncDbServiceManager() {
        // 创建异步执行线程池
        // 核心线程数：2，最大线程数：8，队列容量：1000
        this.asyncExecutor = new ThreadPoolExecutor(
            2, 8,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new ThreadFactory() {
                private int threadNumber = 1;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "AsyncDB-" + threadNumber++);
                    t.setDaemon(true); // 设置为守护线程
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：调用者执行
        );

        // 初始化数据库服务
        this.busCardSwipeDbService = new BusCardSwipeDbService();
        this.busArriveLeaveDbService = new BusArriveLeaveDbService();
        this.openCloseDoorMsgDbService = new OpenCloseDoorMsgDbService();
        this.retrieveDownUpMsgDbService = new RetrieveDownUpMsgDbService();
        this.retrieveLoadFactorMsgDbService = new RetrieveLoadFactorMsgDbService();
        this.retrieveAllMsgDbService = new RetrieveAllMsgDbService();
        this.retrieveAllWsDbService = new RetrieveAllWsDbService();

        if (Config.LOG_INFO) {
            logger.info("[AsyncDbServiceManager] 异步数据库服务管理器初始化完成");
        }
    }

    public static AsyncDbServiceManager getInstance() {
        if (instance == null) {
            synchronized (AsyncDbServiceManager.class) {
                if (instance == null) {
                    instance = new AsyncDbServiceManager();
                }
            }
        }
        return instance;
    }

    /**
     * 异步保存刷卡数据
     */
    public void saveCardSwipeDataAsync(BusCardSwipeData cardData) {
        if (!saveCardSwipeDataEnabled) {
            return;
        }

        submitTask("CardSwipe", () -> {
            try {
                return busCardSwipeDbService.saveCardSwipeData(cardData);
            } catch (Exception e) {
                if (Config.LOG_ERROR) {
                    logger.error("[AsyncDB] 保存刷卡数据异常: " + e.getMessage());
                }
                return false;
            }
        }, "车辆=" + cardData.getBusSelfNo());
    }

    /**
     * 异步保存到离站数据
     */
    public void saveArriveLeaveDataAsync(BusArriveLeaveData arriveLeaveData) {
        if (!saveArriveLeaveDataEnabled) {
            return;
        }

        submitTask("ArriveLeave", () -> {
            try {
                return busArriveLeaveDbService.saveArriveLeaveData(arriveLeaveData);
            } catch (Exception e) {
                if (Config.LOG_ERROR) {
                    logger.error("[AsyncDB] 保存到离站数据异常: " + e.getMessage());
                }
                return false;
            }
        }, "车辆=" + arriveLeaveData.getBusNo());
    }

    /**
     * 异步保存开关门消息
     */
    public void saveOpenCloseDoorMsgAsync(OpenCloseDoorMsg doorMsg) {
        if (!saveOpenCloseDoorMsgEnabled) {
            return;
        }

        submitTask("OpenCloseDoor", () -> {
            try {
                return openCloseDoorMsgDbService.saveOpenCloseDoorMsg(doorMsg);
            } catch (Exception e) {
                if (Config.LOG_ERROR) {
                    logger.error("[AsyncDB] 保存开关门消息异常: " + e.getMessage());
                }
                return false;
            }
        }, "车辆=" + doorMsg.getBusNo() + ",动作=" + doorMsg.getAction());
    }

    /**
     * 异步保存downup消息
     */
    public void saveDownUpMsgAsync(RetrieveDownUpMsg downUpMsg) {
        if (!saveDownUpMsgEnabled) {
            return;
        }

        submitTask("DownUp", () -> {
            try {
                return retrieveDownUpMsgDbService.saveDownUpMsg(downUpMsg);
            } catch (Exception e) {
                if (Config.LOG_ERROR) {
                    logger.error("[AsyncDB] 保存downup消息异常: " + e.getMessage());
                }
                return false;
            }
        }, "车辆=" + downUpMsg.getBusNo() + ",上车=" + downUpMsg.getUpCount() + ",下车=" + downUpMsg.getDownCount());
    }

    /**
     * 异步保存满载率消息
     */
    public void saveLoadFactorMsgAsync(RetrieveLoadFactorMsg loadFactorMsg) {
        if (!saveLoadFactorMsgEnabled) {
            return;
        }

        submitTask("LoadFactor", () -> {
            try {
                return retrieveLoadFactorMsgDbService.saveLoadFactorMsg(loadFactorMsg);
            } catch (Exception e) {
                if (Config.LOG_ERROR) {
                    logger.error("[AsyncDB] 保存满载率消息异常: " + e.getMessage());
                }
                return false;
            }
        }, "车辆=" + loadFactorMsg.getBusNo() + ",满载率=" + loadFactorMsg.getFactorPercentage());
    }

    /**
     * 提交异步任务
     */
    private void submitTask(String taskType, Callable<Boolean> task, String description) {
        totalSubmitted.incrementAndGet();

        CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                if (Config.LOG_ERROR) {
                    logger.error("[AsyncDB] " + taskType + "任务执行异常: " + e.getMessage());
                }
                return false;
            }
        }, asyncExecutor).handle((result, throwable) -> {
            if (throwable != null) {
                totalFailed.incrementAndGet();
                if (Config.LOG_ERROR) {
                    logger.error("[AsyncDB] " + taskType + "任务失败: " + description + ", 错误: " + throwable.getMessage());
                }
            } else if (result != null && result) {
                totalCompleted.incrementAndGet();
                if (Config.LOG_DEBUG) {
                    logger.info("[AsyncDB] " + taskType + "保存成功: " + description);
                }
            } else {
                totalFailed.incrementAndGet();
                if (Config.LOG_ERROR) {
                    logger.error("[AsyncDB] " + taskType + "保存失败: " + description);
                }
            }
            return result;
        });
    }

    /**
     * 获取性能统计信息
     */
    public String getPerformanceStats() {
        long submitted = totalSubmitted.get();
        long completed = totalCompleted.get();
        long failed = totalFailed.get();
        long pending = submitted - completed - failed;

        ThreadPoolExecutor tpe = (ThreadPoolExecutor) asyncExecutor;

        return String.format(
            "[AsyncDB统计] 提交=%d, 完成=%d, 失败=%d, 待处理=%d, " +
            "线程池[活跃=%d, 核心=%d, 最大=%d, 队列=%d]",
            submitted, completed, failed, pending,
            tpe.getActiveCount(), tpe.getCorePoolSize(), tpe.getMaximumPoolSize(), tpe.getQueue().size()
        );
    }

    /**
     * 定期打印性能统计（可选）
     */
    public void printPerformanceStats() {
        if (Config.LOG_INFO) {
            logger.info(getPerformanceStats());
        }
    }

    /**
     * 关闭异步服务
     */
    public void shutdown() {
        if (Config.LOG_INFO) {
            logger.info("[AsyncDbServiceManager] 开始关闭异步数据库服务...");
            logger.info(getPerformanceStats());
        }

        try {
            // 停止接收新任务
            asyncExecutor.shutdown();

            // 等待现有任务完成，最多等待30秒
            if (!asyncExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                if (Config.LOG_INFO) {
                    logger.info("[AsyncDbServiceManager] 30秒内未完成所有任务，强制关闭");
                }
                asyncExecutor.shutdownNow();

                // 再等待10秒
                if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    if (Config.LOG_ERROR) {
                        logger.error("[AsyncDbServiceManager] 无法完全关闭异步执行器");
                    }
                }
            }
        } catch (InterruptedException e) {
            if (Config.LOG_ERROR) {
                logger.error("[AsyncDbServiceManager] 关闭过程被中断: " + e.getMessage());
            }
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 关闭数据库服务
        try {
            busCardSwipeDbService.close();
            busArriveLeaveDbService.close();
            openCloseDoorMsgDbService.close();
            retrieveDownUpMsgDbService.close();
            retrieveLoadFactorMsgDbService.close();

            if (Config.LOG_INFO) {
                logger.info("[AsyncDbServiceManager] 所有数据库连接已关闭");
                logger.info("[AsyncDbServiceManager] 最终统计: " + getPerformanceStats());
            }
        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                logger.error("[AsyncDbServiceManager] 关闭数据库连接时发生错误: " + e.getMessage());
            }
        }
    }

    /**
     * 异步保存所有消息
     */
    public void saveAllMessageAsync(RetrieveAllMsg allMsg) {
        if (!saveAllMessageEnabled) {
            return;
        }

        submitTask("AllMessage", () -> {
            try {
                return retrieveAllMsgDbService.saveAllMessage(allMsg);
            } catch (Exception e) {
                if (Config.LOG_ERROR) {
                    logger.error("[AsyncDB] 保存所有消息异常: " + e.getMessage());
                }
                return false;
            }
        }, "车辆=" + allMsg.getBusNo() + ",类型=" + allMsg.getMessageType() + ",来源=" + allMsg.getSource());
    }

    /**
     * 异步保存所有WebSocket消息
     */
    public void saveAllWebSocketMessageAsync(RetrieveAllWs allWs) {
        if (!saveAllWebSocketMessageEnabled) {
            return;
        }
        submitTask("AllWebSocket", () -> {
            try {
                return retrieveAllWsDbService.saveAllWebSocketMessage(allWs);
            } catch (Exception e) {
                if (Config.LOG_ERROR) {
                    logger.error("[AsyncDB] 保存WebSocket消息异常: " + e.getMessage());
                }
                return false;
            }
        }, "车辆=" + allWs.getBusNo() + ",事件=" + allWs.getEvent());
    }
}
