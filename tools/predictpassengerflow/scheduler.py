# -*- coding: utf-8 -*-
import logging
import time
import schedule
import atexit
import fcntl
import os
from predict_passenger_flow import (
    fetch_data_from_db,
    fetch_real_time_weather,
    preprocess_data,
    train_and_save_models,
    CONFIG,
    compute_and_cache_predictions,
    incremental_extract_to_parquet,
    materialize_feature_store,
    train_from_materialized,
)

# 配置日志
log_file = '/home/server/passenger_flow_prediction.log'
logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

# 避免重复添加处理器
if not logger.handlers:
    file_handler = logging.FileHandler(log_file)
    file_handler.setFormatter(logging.Formatter('%(asctime)s - %(levelname)s - %(message)s'))
    logger.addHandler(file_handler)
    stream_handler = logging.StreamHandler()
    stream_handler.setFormatter(logging.Formatter('%(asctime)s - %(levelname)s - %(message)s'))
    logger.addHandler(stream_handler)

# 文件锁，用于控制调度
LOCK_FILE = '/home/server/pretrain.lock'

def acquire_lock():
    lock_fd = open(LOCK_FILE, 'w')
    try:
        fcntl.flock(lock_fd.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        return lock_fd
    except IOError:
        lock_fd.close()
        return None

def release_lock(lock_fd):
    if lock_fd:
        fcntl.flock(lock_fd.fileno(), fcntl.LOCK_UN)
        lock_fd.close()

# 定时任务函数，每次重新获取和处理数据
def train_job():
    try:
        logger.info("开始定时任务：增量抽取 -> 物化 -> 训练 -> 刷新缓存")
        incremental_extract_to_parquet(CONFIG['window_days'])
        materialize_feature_store(CONFIG['window_days'], CONFIG['time_granularity'])
        train_from_materialized(CONFIG['window_days'])
        # 训练完成后，离线计算并刷新预测缓存（读取物化层进行预测）
        compute_and_cache_predictions(CONFIG['window_days'], CONFIG['time_granularity'])
        logger.info("定时任务完成：模型已更新并刷新预测缓存")
    except Exception as e:
        logger.error("定时任务失败: %s", str(e))

# 定时任务调度
def run_schedule():
    logger.info("启动定时任务调度线程")
    while True:
        schedule.run_pending()
        time.sleep(60)

# 初始化调度
def initialize_scheduler():
    lock_fd = acquire_lock()
    if lock_fd is None:
        logger.info("另一个调度进程已运行，退出")
        exit(0)

    try:
        logger.info("初始化调度器：增量抽取 -> 物化 -> 训练 -> 刷新缓存")
        incremental_extract_to_parquet(CONFIG['window_days'])
        materialize_feature_store(CONFIG['window_days'], CONFIG['time_granularity'])
        train_from_materialized(CONFIG['window_days'])
        compute_and_cache_predictions(CONFIG['window_days'], CONFIG['time_granularity'])
        schedule.every(1).minutes.do(train_job)
        run_schedule()
        atexit.register(release_lock, lock_fd)
    except Exception as e:
        logger.error("调度器初始化失败: %s", str(e))
        release_lock(lock_fd)

if __name__ == '__main__':
    initialize_scheduler()