import logging
import re
import psycopg2
import psycopg2.extras
import pandas as pd
import numpy as np
from datetime import datetime, timedelta
from holidays import China
from sklearn.preprocessing import StandardScaler, OneHotEncoder
from xgboost import XGBRegressor
from sklearn.metrics import mean_squared_error, mean_absolute_error, r2_score
from sklearn.model_selection import TimeSeriesSplit
import warnings
import requests
from flask import Flask, jsonify
import joblib
import os
import time
from sklearn.model_selection import GridSearchCV
import json

warnings.filterwarnings("ignore")

# 配置
CONFIG = {
    'time_granularity': '15min',
    'window_days': 60,
    'db_config': {
        'host': '20.17.39.23',
        'port': 5432,
        'user': 'gj_dw_r1',
        'password': 'gj_dw_r1',
        'database': 'GJ_DW'
    },
    'model_dir': '/home/server/models',
    'service_end_buffer_minutes': 15
}
# 数据与特征存储目录
RAW_DIR = '/home/server/data/raw'
META_FILE = '/home/server/data/meta.json'
FEATURE_DIR = '/home/server/feature_store'
FEATURE_DATA_FILE = os.path.join(FEATURE_DIR, 'data.parquet')
FEATURES_FILE = os.path.join(FEATURE_DIR, 'features.json')
SCALER_FILE = os.path.join(FEATURE_DIR, 'scaler.pkl')
ENCODER_FILE = os.path.join(FEATURE_DIR, 'encoder.pkl')

# 初始化 Flask 应用
app = Flask(__name__)

# 配置日志（确保多进程安全）
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

# 通道与 route_id 映射
CHANNEL_MAPPING = {
    'C1': ['3301000100131008', '3301000100093210'],
    'C2': ['3301000100117308', '3301000100161820', '3301000100143609'],
    'C3': ['1001000535'],
    'C4': ['1001000018'],
    'C5': ['3301000100108408']
}

# 通道名称映射
CHANNEL_NAME_MAPPING = {
    'C1': "'黄龙体育中心至灵隐专线', '武林广场至灵隐专线'",
    'C2': "'278M路', '319M路', '西溪路停车场至灵隐接驳线'",
    'C3': "'505路'",
    'C4': "'7路'",
    'C5': "'龙翔桥至灵隐专线'"
}

# 通道索引（与实时表 inboundcount1..5 / outboundcount1..5 对应）
CHANNEL_INDEX = {
    'C1': 1,
    'C2': 2,
    'C3': 3,
    'C4': 4,
    'C5': 5
}

# 缓存目录与文件
CACHE_DIR = '/home/server/cache'
PRED_CACHE_FILE = os.path.join(CACHE_DIR, 'predictions.json')

def _ensure_cache_dir():
    try:
        os.makedirs(CACHE_DIR, exist_ok=True)
    except Exception:
        pass

def save_predictions_cache(predictions):
    _ensure_cache_dir()
    payload = {
        'generated_at': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
        'data': predictions
    }
    try:
        with open(PRED_CACHE_FILE, 'w', encoding='utf-8') as f:
            json.dump(payload, f, ensure_ascii=False)
        logger.info("预测结果已写入缓存: %s", PRED_CACHE_FILE)
    except Exception as e:
        logger.error("写入预测缓存失败: %s", str(e))

def load_predictions_cache(max_age_seconds=180):
    try:
        if not os.path.exists(PRED_CACHE_FILE):
            return None
        mtime = os.path.getmtime(PRED_CACHE_FILE)
        age = time.time() - mtime
        if age > max_age_seconds:
            logger.warning("预测缓存已过期(%.1fs)，文件: %s", age, PRED_CACHE_FILE)
        with open(PRED_CACHE_FILE, 'r', encoding='utf-8') as f:
            payload = json.load(f)
        return payload.get('data')
    except Exception as e:
        logger.error("读取预测缓存失败: %s", str(e))
        return None

# ----------------------------
# 增量抽取到本地 Parquet（raw）
# ----------------------------

def _ensure_dirs():
    for d in [RAW_DIR, FEATURE_DIR, CACHE_DIR, CONFIG['model_dir'], os.path.dirname(META_FILE)]:
        try:
            os.makedirs(d, exist_ok=True)
        except Exception:
            pass

def _read_meta():
    try:
        with open(META_FILE, 'r', encoding='utf-8') as f:
            return json.load(f)
    except Exception:
        return {}

def _write_meta(meta):
    try:
        os.makedirs(os.path.dirname(META_FILE), exist_ok=True)
        with open(META_FILE, 'w', encoding='utf-8') as f:
            json.dump(meta, f, ensure_ascii=False, indent=2)
    except Exception as e:
        logger.error("写入meta失败: %s", str(e))

def incremental_extract_to_parquet(window_days):
    start_time = time.time()
    _ensure_dirs()
    logger.info("开始增量抽取到Parquet，窗口: %d天", window_days)
    meta = _read_meta()

    conn = psycopg2.connect(**CONFIG['db_config'])
    cursor = conn.cursor(cursor_factory=psycopg2.extras.DictCursor)

    now_dt = datetime.now()
    window_start = now_dt - timedelta(days=window_days)
    ws = window_start.strftime('%Y-%m-%d %H:%M:%S')
    we = now_dt.strftime('%Y-%m-%d %H:%M:%S')

    # trade
    last_trade_ts = meta.get('last_trade_ts')
    trade_cond = "trade_time > %s AND trade_time <= %s"
    trade_params = [last_trade_ts or ws, we]
    logger.info("增量抽取trade，起点: %s", trade_params[0])
    cursor.execute(
        """
        SELECT trade_time, stop_id, route_id
        FROM ods.trade
        WHERE (stop_id = '1001001154' OR off_stop_id = '1001001154')
          AND """ + trade_cond + """
        ORDER BY trade_time
        """,
        trade_params
    )
    trade_df = pd.DataFrame([dict(r) for r in cursor.fetchall()])
    if not trade_df.empty:
        trade_path = os.path.join(RAW_DIR, 'trade.parquet')
        try:
            if os.path.exists(trade_path):
                old = pd.read_parquet(trade_path)
                trade_df = pd.concat([old, trade_df], ignore_index=True)
                trade_df.drop_duplicates(subset=['trade_time', 'stop_id', 'route_id'], inplace=True)
            trade_df.to_parquet(trade_path, index=False)
            meta['last_trade_ts'] = trade_df['trade_time'].max().strftime('%Y-%m-%d %H:%M:%S')
        except Exception as e:
            logger.error("写入trade parquet失败: %s", str(e))

    # sim_station
    last_brd_ts = meta.get('last_broadcast_ts')
    brd_cond = "arrive_time > %s AND arrive_time <= %s"
    brd_params = [last_brd_ts or ws, we]
    logger.info("增量抽取sim_station，起点: %s", brd_params[0])
    cursor.execute(
        """
        SELECT arrive_time, leave_time, stop_id, route_id, board_amount, off_amount
        FROM ods.sim_station
        WHERE stop_id = '1001001154' AND """ + brd_cond + """
        ORDER BY arrive_time
        """,
        brd_params
    )
    brd_df = pd.DataFrame([dict(r) for r in cursor.fetchall()])
    if not brd_df.empty:
        brd_path = os.path.join(RAW_DIR, 'broadcast.parquet')
        try:
            if os.path.exists(brd_path):
                old = pd.read_parquet(brd_path)
                brd_df = pd.concat([old, brd_df], ignore_index=True)
                brd_df.drop_duplicates(subset=['arrive_time', 'stop_id', 'route_id'], inplace=True)
            brd_df.to_parquet(brd_path, index=False)
            meta['last_broadcast_ts'] = brd_df['arrive_time'].max().strftime('%Y-%m-%d %H:%M:%S')
        except Exception as e:
            logger.error("写入broadcast parquet失败: %s", str(e))

    # assign_schedule
    last_sch_ts = meta.get('last_schedule_ts')
    sch_cond = "dispatch_departure_time > %s AND dispatch_departure_time <= %s"
    sch_params = [last_sch_ts or ws, we]
    logger.info("增量抽取assign_schedule，起点: %s", sch_params[0])
    cursor.execute(
        """
        SELECT dispatch_departure_time, dispatch_end_time, route_id, route_name, single_trip_duration, assign_status, is_delete
        FROM ods.assign_schedule
        WHERE (origin_id = '1001001154' OR terminal_id = '1001001154')
          AND assign_status = 'RELEASE' AND is_delete = false AND """ + sch_cond + """
        ORDER BY dispatch_departure_time
        """,
        sch_params
    )
    sch_df = pd.DataFrame([dict(r) for r in cursor.fetchall()])
    if not sch_df.empty:
        sch_path = os.path.join(RAW_DIR, 'schedule.parquet')
        try:
            if os.path.exists(sch_path):
                old = pd.read_parquet(sch_path)
                sch_df = pd.concat([old, sch_df], ignore_index=True)
                sch_df.drop_duplicates(subset=['dispatch_departure_time', 'route_id'], inplace=True)
            sch_df.to_parquet(sch_path, index=False)
            meta['last_schedule_ts'] = sch_df['dispatch_departure_time'].max().strftime('%Y-%m-%d %H:%M:%S')
        except Exception as e:
            logger.error("写入schedule parquet失败: %s", str(e))

    cursor.close()
    conn.close()
    _write_meta(meta)
    logger.info("增量抽取完成，耗时: %.2f秒", time.time() - start_time)

# ----------------------------
# 物化特征层
# ----------------------------

def materialize_feature_store(window_days, time_granularity):
    _ensure_dirs()
    start_time = time.time()
    logger.info("开始生成物化特征层，窗口: %d天，粒度: %s", window_days, time_granularity)

    # 读取raw parquet并裁剪窗口
    now_dt = datetime.now()
    window_start = now_dt - timedelta(days=window_days)

    trade_path = os.path.join(RAW_DIR, 'trade.parquet')
    brd_path = os.path.join(RAW_DIR, 'broadcast.parquet')
    sch_path = os.path.join(RAW_DIR, 'schedule.parquet')

    trade_data = pd.read_parquet(trade_path) if os.path.exists(trade_path) else pd.DataFrame(columns=['trade_time','stop_id','route_id'])
    broadcast_data = pd.read_parquet(brd_path) if os.path.exists(brd_path) else pd.DataFrame(columns=['arrive_time','leave_time','stop_id','route_id','board_amount','off_amount'])
    schedule_data = pd.read_parquet(sch_path) if os.path.exists(sch_path) else pd.DataFrame(columns=['dispatch_departure_time','dispatch_end_time','route_id','route_name','single_trip_duration','assign_status','is_delete'])

    if not trade_data.empty:
        trade_data = trade_data[pd.to_datetime(trade_data['trade_time']) >= window_start]
    if not broadcast_data.empty:
        broadcast_data = broadcast_data[pd.to_datetime(broadcast_data['arrive_time']) >= window_start]
    if not schedule_data.empty:
        schedule_data = schedule_data[pd.to_datetime(schedule_data['dispatch_departure_time']) >= window_start]

    # 生成天气（仅用于合并一致性；训练阶段仍会用到当日天气）
    today = datetime.now().strftime('%Y-%m-%d')
    weather_data = fetch_real_time_weather(today, today, time_granularity)

    data, scaler, enc, weather_data, features = preprocess_data(trade_data, broadcast_data, schedule_data, weather_data, time_granularity)

    os.makedirs(FEATURE_DIR, exist_ok=True)
    data.to_parquet(FEATURE_DATA_FILE, index=False)
    joblib.dump(scaler, SCALER_FILE)
    joblib.dump(enc, ENCODER_FILE)
    with open(FEATURES_FILE, 'w', encoding='utf-8') as f:
        json.dump({'features': features}, f, ensure_ascii=False)

    logger.info("物化特征层已生成：%s，耗时: %.2f秒", FEATURE_DATA_FILE, time.time() - start_time)

def load_materialized():
    try:
        data = pd.read_parquet(FEATURE_DATA_FILE)
        scaler = joblib.load(SCALER_FILE) if os.path.exists(SCALER_FILE) else None
        enc = joblib.load(ENCODER_FILE) if os.path.exists(ENCODER_FILE) else None
        with open(FEATURES_FILE, 'r', encoding='utf-8') as f:
            features = json.load(f).get('features', [])
        return data, scaler, enc, features
    except Exception as e:
        logger.error("加载物化特征失败: %s", str(e))
        return None, None, None, []

def train_from_materialized(window_days):
    start_time = time.time()
    logger.info("从物化特征层训练模型，窗口: %d天", window_days)
    data, _, _, features = load_materialized()
    if data is None or not features:
        logger.warning("物化特征缺失或无特征定义，回退至在线训练")
        trade_data, broadcast_data, schedule_data = fetch_data_from_db(window_days)
        today = datetime.now().strftime('%Y-%m-%d')
        weather_data = fetch_real_time_weather(today, today, CONFIG['time_granularity'])
        data, scaler, enc, weather_data, features = preprocess_data(trade_data, broadcast_data, schedule_data, weather_data, CONFIG['time_granularity'])
        return train_and_save_models(data, features, window_days)
    models = train_and_save_models(data, features, window_days)
    logger.info("从物化特征层训练完成，耗时: %.2f秒", time.time() - start_time)
    return models

# ----------------------------
# 实时客流与服务时间辅助方法
# ----------------------------

def _parse_pf_current_time(value):
    """解析 ads.passenger_flow.current_time，支持仅有时间字符串如 '20:57:49 +0800'。"""
    if not value:
        return None
    try:
        # 仅时间(可带时区)的情况，补上今天日期
        if isinstance(value, str) and re.match(r"^\d{2}:\d{2}:\d{2}(?:\s*[+-]\d{4})?$", value.strip()):
            today = datetime.now().date()
            # 去掉时区标识，仅用本地时区
            time_part = value.strip().split()[0]
            t = datetime.strptime(time_part, '%H:%M:%S').time()
            return datetime.combine(today, t)
        return pd.to_datetime(value)
    except Exception:
        return None


def fetch_latest_passenger_flow():
    """获取实时表 ads.passenger_flow 最新两条记录，计算每通道增量。
    返回: {
        'current_time': datetime | None,
        'inbound': {'C1': int, ...},
        'outbound': {'C1': int, ...},
        'recent_increment': {'C1': int, ...}
    }，获取失败时返回默认0。
    """
    start_time = time.time()
    logger.info("开始获取实时客流数据...")
    try:
        conn = psycopg2.connect(**CONFIG['db_config'])
        cursor = conn.cursor(cursor_factory=psycopg2.extras.DictCursor)
        cursor.execute("""
            SELECT id, station_no, current_time,
                   inboundcount1, inboundcount2, inboundcount3, inboundcount4, inboundcount5,
                   outboundcount1, outboundcount2, outboundcount3, outboundcount4, outboundcount5
            FROM ads.passenger_flow
            ORDER BY id DESC
            LIMIT 2
        """)
        rows = cursor.fetchall()
        cursor.close()
        conn.close()

        if not rows:
            logger.warning("实时客流表为空，无数据可获取")
            return {
                'current_time': None,
                'inbound': {ch: 0 for ch in CHANNEL_INDEX.keys()},
                'outbound': {ch: 0 for ch in CHANNEL_INDEX.keys()},
                'recent_increment': {ch: 0 for ch in CHANNEL_INDEX.keys()}
            }

        latest = rows[0]
        prev = rows[1] if len(rows) > 1 else None
        logger.info("获取到实时客流记录 - 最新ID: %s, 时间: %s", 
                   latest.get('id'), latest.get('current_time'))
        if prev:
            logger.info("获取到实时客流记录 - 上一条ID: %s, 时间: %s", 
                       prev.get('id'), prev.get('current_time'))

        current_dt = _parse_pf_current_time(latest.get('current_time'))
        logger.info("解析后的最新时间: %s", current_dt)

        inbound_latest = {}
        outbound_latest = {}
        inbound_prev = {ch: 0 for ch in CHANNEL_INDEX.keys()}
        outbound_prev = {ch: 0 for ch in CHANNEL_INDEX.keys()}
        for ch, idx in CHANNEL_INDEX.items():
            inbound_latest[ch] = int(latest.get(f'inboundcount{idx}', 0) or 0)
            outbound_latest[ch] = int(latest.get(f'outboundcount{idx}', 0) or 0)
            if prev is not None:
                inbound_prev[ch] = int(prev.get(f'inboundcount{idx}', 0) or 0)
                outbound_prev[ch] = int(prev.get(f'outboundcount{idx}', 0) or 0)

        recent_increment = {}
        for ch in CHANNEL_INDEX.keys():
            inc = max(0, inbound_latest[ch] - inbound_prev[ch]) + max(0, outbound_latest[ch] - outbound_prev[ch])
            recent_increment[ch] = inc
            logger.info("通道 %s - 最新进站: %d, 最新出站: %d, 增量: %d", 
                       ch, inbound_latest[ch], outbound_latest[ch], inc)

        logger.info("实时客流数据获取完成，耗时: %.2f秒", time.time() - start_time)
        return {
            'current_time': current_dt,
            'inbound': inbound_latest,
            'outbound': outbound_latest,
            'recent_increment': recent_increment
        }
    except Exception as e:
        logger.error("获取实时客流失败: %s", str(e), exc_info=True)
        return {
            'current_time': None,
            'inbound': {ch: 0 for ch in CHANNEL_INDEX.keys()},
            'outbound': {ch: 0 for ch in CHANNEL_INDEX.keys()},
            'recent_increment': {ch: 0 for ch in CHANNEL_INDEX.keys()}
        }


def fetch_route_service_times(route_ids):
    """从 ods.route_portrai 读取指定线路的 service_time。
    返回: {route_id: service_time_str}
    """
    start_time = time.time()
    logger.info("开始获取线路服务时间，线路数量: %d", len(route_ids))
    if not route_ids:
        logger.warning("线路ID列表为空，跳过服务时间获取")
        return {}
    try:
        conn = psycopg2.connect(**CONFIG['db_config'])
        cursor = conn.cursor(cursor_factory=psycopg2.extras.DictCursor)
        logger.info("查询线路服务时间，SQL: SELECT route_id, service_time FROM ods.route_portrai WHERE route_id = ANY(%s)", route_ids)
        cursor.execute(
            """
            SELECT route_id, service_time
            FROM ods.route_portrai
            WHERE route_id = ANY(%s)
            """,
            (list(route_ids),)
        )
        rows = cursor.fetchall()
        cursor.close()
        conn.close()
        result = {str(r['route_id']): (r['service_time'] or '') for r in rows}
        logger.info("成功获取 %d 条线路服务时间记录", len(rows))
        for route_id, service_time in result.items():
            logger.info("线路 %s 服务时间: %s", route_id, service_time[:100] + "..." if len(service_time) > 100 else service_time)
        logger.info("线路服务时间获取完成，耗时: %.2f秒", time.time() - start_time)
        return result
    except Exception as e:
        logger.error("获取线路服务时间失败: %s", str(e), exc_info=True)
        return {rid: '' for rid in route_ids}


def parse_service_windows(service_time_str, target_date, route_id=None):
    """解析 service_time 字段，返回当天的服务时间窗口列表[(start_dt, end_dt), ...]。
    优先根据 '工作日' 与 '节假日双休日' 判定，如果没有匹配标签则使用其他可用标签作为兜底。
    """
    logger.debug("开始解析服务时间: %s, 目标日期: %s", service_time_str[:100] + "..." if len(service_time_str) > 100 else service_time_str, target_date)
    if not service_time_str:
        logger.debug("服务时间字符串为空，返回空列表")
        return []

    is_holiday_today = target_date in China(years=target_date.year) or target_date.weekday() >= 5
    logger.debug("当前日期类型: %s", "节假日/双休日" if is_holiday_today else "工作日")
    expected_flag = '节假日双休日' if is_holiday_today else '工作日'

    windows = []
    # 示例片段: 下行(冬令时-工作日-非定时班):07:00:00-17:30:00
    # 以 ';' 分段
    parts = [p.strip() for p in service_time_str.split(';') if p.strip()]
    
    # 第一轮：尝试匹配期望的日期类型标签
    for part in parts:
        try:
            # 抽取括号内标签与时间段
            # 方向(标签...):HH:MM:SS-HH:MM:SS
            m = re.search(r"\((.*?)\)\s*:\s*([0-9]{2}:[0-9]{2}:[0-9]{2})\s*-\s*([0-9]{2}:[0-9]{2}:[0-9]{2})", part)
            if not m:
                logger.debug("无法解析服务时间片段: %s", part)
                continue
            flags = m.group(1)  # 例如: 冬令时-工作日-非定时班
            start_str = m.group(2)
            end_str = m.group(3)

            if expected_flag not in flags:
                logger.debug("标签不匹配，期望: %s, 实际: %s", expected_flag, flags)
                continue

            start_dt = datetime.combine(target_date, datetime.strptime(start_str, '%H:%M:%S').time())
            end_dt = datetime.combine(target_date, datetime.strptime(end_str, '%H:%M:%S').time())

            # 若跨天，简单处理为截断至当天 23:59:59
            if end_dt <= start_dt:
                end_dt = end_dt + timedelta(days=1)

            windows.append((start_dt, end_dt))
            logger.debug("成功解析服务时间窗口: %s - %s", start_dt, end_dt)
        except Exception:
            logger.debug("解析服务时间片段失败: %s", part)
            continue

    # 第二轮：如果没有找到匹配的标签，使用其他可用标签作为兜底
    if not windows:
        logger.warning("线路 %s 没有 %s 标签，尝试使用其他可用标签作为兜底", route_id or "未知", expected_flag)
        for part in parts:
            try:
                m = re.search(r"\((.*?)\)\s*:\s*([0-9]{2}:[0-9]{2}:[0-9]{2})\s*-\s*([0-9]{2}:[0-9]{2}:[0-9]{2})", part)
                if not m:
                    continue
                flags = m.group(1)
                start_str = m.group(2)
                end_str = m.group(3)
                
                # 检查是否包含日期类型标签
                if '工作日' in flags or '节假日双休日' in flags:
                    start_dt = datetime.combine(target_date, datetime.strptime(start_str, '%H:%M:%S').time())
                    end_dt = datetime.combine(target_date, datetime.strptime(end_str, '%H:%M:%S').time())
                    
                    # 若跨天，简单处理为截断至当天 23:59:59
                    if end_dt <= start_dt:
                        end_dt = end_dt + timedelta(days=1)
                    
                    windows.append((start_dt, end_dt))
                    logger.debug("兜底解析服务时间窗口: %s - %s (使用标签: %s)", start_dt, end_dt, flags)
            except Exception:
                logger.debug("兜底解析服务时间片段失败: %s", part)
                continue

    logger.debug("解析完成，共 %d 个服务时间窗口", len(windows))
    return windows


def build_channel_service_windows(route_service_times):
    """根据线路 service_time 构建通道服务窗口合集。
    route_service_times: {route_id: service_time_str}
    返回: {channel: [(start_dt, end_dt), ...]}
    """
    start_time = time.time()
    logger.info("开始构建通道服务时间窗口...")
    today = datetime.now().date()
    logger.info("目标日期: %s (是否为节假日: %s)", today, today in China(years=today.year) or today.weekday() >= 5)
    channel_windows = {ch: [] for ch in CHANNEL_MAPPING.keys()}
    for channel, route_ids in CHANNEL_MAPPING.items():
        logger.info("处理通道 %s，包含线路: %s", channel, route_ids)
        for rid in route_ids:
            service_str = route_service_times.get(rid, '')
            windows = parse_service_windows(service_str, today, rid)
            logger.info("线路 %s 解析出 %d 个服务窗口", rid, len(windows))
            channel_windows[channel].extend(windows)
    # 合并重叠窗口（简单按开始时间排序后线性合并）
    for ch in channel_windows:
        intervals = sorted(channel_windows[ch], key=lambda x: x[0])
        merged = []
        for interval in intervals:
            if not merged:
                merged.append(list(interval))
            else:
                if interval[0] <= merged[-1][1]:
                    merged[-1][1] = max(merged[-1][1], interval[1])
                else:
                    merged.append(list(interval))
        channel_windows[ch] = [(s, e) for s, e in merged]
        if not channel_windows[ch]:
            logger.warning("通道 %s 当天未解析到服务时间窗口，默认视为全时段服务", ch)
        else:
            logger.info("通道 %s 最终服务时间窗口: %s", ch, 
                       [(s.strftime('%H:%M'), e.strftime('%H:%M')) for s, e in channel_windows[ch]])

    logger.info("通道服务时间窗口构建完成，耗时: %.2f秒", time.time() - start_time)
    return channel_windows


def is_active_at(dt_point, windows, buffer_minutes):
    """判断时间点是否处于任一服务窗口内，或在窗口结束后 buffer_minutes 内。
    若 windows 为空，则默认返回 True（兜底）。
    """
    if not windows:
        logger.debug("无服务时间窗口，默认返回活跃状态")
        return True
    for start_dt, end_dt in windows:
        if start_dt <= dt_point <= (end_dt + timedelta(minutes=buffer_minutes)):
            logger.debug("时间点 %s 在服务窗口内: %s - %s (缓冲: %d分钟)", 
                        dt_point.strftime('%H:%M'), start_dt.strftime('%H:%M'), 
                        end_dt.strftime('%H:%M'), buffer_minutes)
            return True
    logger.debug("时间点 %s 不在任何服务窗口内", dt_point.strftime('%H:%M'))
    return False

# 模拟天气数据
def generate_simulated_weather_data(start_date, end_date, time_granularity):
    start_time = time.time()
    logger.info("生成模拟天气数据...")
    dates = pd.date_range(start=start_date, end=end_date, freq=time_granularity)
    np.random.seed(42)
    weather_data = {
        'datetime': dates,
        'temperature': np.random.normal(25, 5, len(dates)),
        'precipitation': np.random.exponential(0.5, len(dates)),
        'wind_speed': np.random.uniform(0, 10, len(dates)),
        'weather_type': np.random.choice(['sunny', 'cloudy', 'rainy'], len(dates), p=[0.5, 0.3, 0.2])
    }
    weather_df = pd.DataFrame(weather_data)
    weather_df['temperature'] = np.clip(weather_df['temperature'], -20, 45)
    weather_df['precipitation'] = np.clip(weather_df['precipitation'], 0, 100)
    weather_df['wind_speed'] = np.clip(weather_df['wind_speed'], 0, 30)
    logger.info("模拟天气数据生成完成，记录数: %d, 耗时: %.2f秒", len(weather_df), time.time() - start_time)
    return weather_df

# 实时天气 API 接口
def fetch_real_time_weather(start_date, end_date, time_granularity):
    start_time = time.time()
    logger.info("从 Open-Meteo 获取实时天气数据...")
    cache_file = '/home/server/weather_cache.pkl'
    try:
        with open(cache_file, 'rb') as f:
            cached_weather = joblib.load(f)
            if cached_weather['date'] == start_date:
                logger.info("使用缓存天气数据，耗时: %.2f秒", time.time() - start_time)
                return cached_weather['data']
    except:
        pass
    url = "https://api.open-meteo.com/v1/forecast"
    params = {
        "latitude": 30.27,
        "longitude": 120.15,
        "hourly": "temperature_2m,precipitation,wind_speed_10m,weathercode",
        "start_date": start_date,
        "end_date": end_date
    }
    try:
        response = requests.get(url, params=params, timeout=5)
        response.raise_for_status()
        data = response.json()
        weather_df = pd.DataFrame({
            'datetime': pd.to_datetime(data['hourly']['time']),
            'temperature': data['hourly']['temperature_2m'],
            'precipitation': data['hourly']['precipitation'],
            'wind_speed': data['hourly']['wind_speed_10m'],
            'weather_type': [map_weather_code(code) for code in data['hourly']['weathercode']]
        })
        weather_df['time_slot'] = weather_df['datetime'].dt.floor(time_granularity)
        weather_df = weather_df.groupby('time_slot').agg({
            'temperature': 'mean',
            'precipitation': 'sum',
            'wind_speed': 'mean',
            'weather_type': lambda x: x.mode()[0] if not x.empty else 'cloudy'
        }).reset_index()
        weather_df.rename(columns={'time_slot': 'datetime'}, inplace=True)
        joblib.dump({'date': start_date, 'data': weather_df}, cache_file)
        logger.info("实时天气数据获取完成，记录数: %d, 耗时: %.2f秒", len(weather_df), time.time() - start_time)
        return weather_df
    except Exception as e:
        logger.error("获取天气数据失败: %s", str(e))
        logger.warning("使用模拟数据作为后备")
        return generate_simulated_weather_data(start_date, end_date, time_granularity)

def map_weather_code(code):
    if code in [0, 1]: return 'sunny'
    elif code in [2, 3]: return 'cloudy'
    elif code in [51, 53, 55, 61, 63, 65, 80, 81, 82]: return 'rainy'
    return 'cloudy'

# 数据提取
def fetch_data_from_db(window_days, time_granularity=None):
    start_time = time.time()
    logger.info("开始从数据库提取数据...")
    try:
        conn_start = time.time()
        conn = psycopg2.connect(**CONFIG['db_config'])
        cursor = conn.cursor(cursor_factory=psycopg2.extras.DictCursor)
        logger.info("数据库连接耗时: %.2f秒", time.time() - conn_start)

        end_date = datetime.now()
        start_date = end_date - timedelta(days=window_days)
        start_date_str = start_date.strftime('%Y-%m-%d %H:%M:%S')
        end_date_str = end_date.strftime('%Y-%m-%d %H:%M:%S')

        # 仅支持 15 分钟聚合（当前配置默认）
        if time_granularity is None:
            time_granularity = CONFIG.get('time_granularity', '15min')
        if time_granularity != '15min':
            logger.warning("目前SQL端仅实现15分钟聚合，收到: %s，将按15分钟处理", time_granularity)

        query_start = time.time()
        # 交易数据：SQL端按15分钟聚合，减少传输体量
        query_trade = """
        SELECT 
            (date_trunc('hour', trade_time) + make_interval(mins => 15 * floor(extract(minute from trade_time)::int / 15))) AS time_slot,
            route_id,
            COUNT(*) AS trade_count
        FROM ods.trade
        WHERE (stop_id = '1001001154' OR off_stop_id = '1001001154')
          AND trade_time >= %s AND trade_time < %s
        GROUP BY 1, 2
        ORDER BY 1, 2
        """
        cursor.execute(query_trade, (start_date_str, end_date_str))
        trade_data = pd.DataFrame([dict(row) for row in cursor.fetchall()])
        logger.info("提取交易聚合记录数: %d, 查询耗时: %.2f秒", len(trade_data), time.time() - query_start)

        query_start = time.time()
        # 报站数据：SQL端按15分钟聚合
        query_broadcast = """
        SELECT 
            (date_trunc('hour', arrive_time) + make_interval(mins => 15 * floor(extract(minute from arrive_time)::int / 15))) AS time_slot,
            route_id,
            SUM(board_amount) AS board_amount,
            SUM(off_amount) AS off_amount
        FROM ods.sim_station
        WHERE stop_id = '1001001154'
          AND arrive_time >= %s AND arrive_time < %s
        GROUP BY 1, 2
        ORDER BY 1, 2
        """
        cursor.execute(query_broadcast, (start_date_str, end_date_str))
        broadcast_data = pd.DataFrame([dict(row) for row in cursor.fetchall()])
        logger.info("提取报站聚合记录数: %d, 查询耗时: %.2f秒", len(broadcast_data), time.time() - query_start)

        query_start = time.time()
        # 调度数据：SQL端按15分钟聚合，统计发班次数和平均单程时长
        query_schedule = """
        SELECT 
            (date_trunc('hour', dispatch_departure_time) + make_interval(mins => 15 * floor(extract(minute from dispatch_departure_time)::int / 15))) AS time_slot,
            route_id,
            COUNT(*) AS dispatch_count,
            AVG(GREATEST(5, LEAST(60, COALESCE(single_trip_duration, 10))))::int AS single_trip_duration
        FROM ods.assign_schedule
        WHERE (origin_id = '1001001154' OR terminal_id = '1001001154')
          AND dispatch_departure_time >= %s AND dispatch_departure_time < %s
          AND assign_status = 'RELEASE' AND is_delete = false
        GROUP BY 1, 2
        ORDER BY 1, 2
        """
        cursor.execute(query_schedule, (start_date_str, end_date_str))
        schedule_data = pd.DataFrame([dict(row) for row in cursor.fetchall()])
        logger.info("提取调度聚合记录数: %d, 查询耗时: %.2f秒", len(schedule_data), time.time() - query_start)

        cursor.close()
        conn.close()
        logger.info("数据库提取总耗时: %.2f秒", time.time() - start_time)
        return trade_data, broadcast_data, schedule_data
    except Exception as e:
        logger.error("数据库查询失败: %s", str(e))
        raise

# 调度数据处理与通道映射
def process_schedule_dates(schedule_data):
    start_time = time.time()
    logger.info("开始处理调度数据...")
    schedule_data['plan_departure_time'] = pd.to_datetime(schedule_data['dispatch_departure_time'], errors='coerce')
    schedule_data['plan_end_time'] = pd.to_datetime(schedule_data['dispatch_end_time'], errors='coerce')

    start_date = (datetime.now() - timedelta(days=CONFIG['window_days'])).replace(hour=0, minute=0, second=0, microsecond=0)
    end_date = datetime.now().replace(hour=23, minute=59, second=59, microsecond=999999)
    schedule_data = schedule_data[
        (schedule_data['plan_departure_time'] >= start_date) &
        (schedule_data['plan_departure_time'] < end_date)
        ]

    schedule_data['single_trip_duration'] = np.clip(schedule_data['single_trip_duration'], 5, 60)
    schedule_data['single_trip_duration'] = schedule_data['single_trip_duration'].fillna(10).astype(int)

    invalid_count = schedule_data['plan_departure_time'].isna().sum()
    if invalid_count > 0:
        logger.warning("发现 %d 条记录的 plan_departure_time 无效，将被删除", invalid_count)
    schedule_data.dropna(subset=['plan_departure_time'], inplace=True)

    invalid_routes = []
    def map_to_channel(route_id, route_name):
        if pd.isna(route_id) or route_id is None:
            invalid_routes.append('None')
            return None
        route_id = str(route_id).strip()
        for channel, routes in CHANNEL_MAPPING.items():
            if route_id in routes:
                return channel
        invalid_routes.append(f"{route_id}:{route_name}")
        return None

    schedule_data['channel'] = schedule_data.apply(lambda x: map_to_channel(x['route_id'], x['route_name']), axis=1)
    invalid_channel_count = schedule_data['channel'].isna().sum()
    if invalid_channel_count > 0:
        logger.warning("发现 %d 条记录的 route_id 无法映射，将被删除", invalid_channel_count)
    schedule_data.dropna(subset=['channel'], inplace=True)

    channel_counts = schedule_data['channel'].value_counts().to_dict()
    logger.info("通道分配统计: %s, 耗时: %.2f秒", channel_counts, time.time() - start_time)
    return schedule_data

# 数据清洗与特征工程
def preprocess_data(trade_data, broadcast_data, schedule_data, weather_data, time_granularity):
    start_time = time.time()
    logger.info("开始数据清洗与特征工程，时间粒度: %s", time_granularity)

    # 去重
    trade_data = trade_data.drop_duplicates(subset=['trade_time', 'stop_id', 'route_id'])
    broadcast_data = broadcast_data.drop_duplicates(subset=['arrive_time', 'stop_id', 'route_id'])
    schedule_data = schedule_data.drop_duplicates(subset=['dispatch_departure_time', 'route_id'])
    logger.info("去重后数据量 - 交易: %d, 报站: %d, 调度: %d", len(trade_data), len(broadcast_data), len(schedule_data))

    # 时间转换
    trade_data['trade_time'] = pd.to_datetime(trade_data['trade_time'])
    broadcast_data['arrive_time'] = pd.to_datetime(broadcast_data['arrive_time'])
    broadcast_data['leave_time'] = pd.to_datetime(broadcast_data['leave_time'])
    weather_data['datetime'] = pd.to_datetime(weather_data['datetime'])

    # 调度数据处理
    if not schedule_data.empty:
        schedule_data = process_schedule_dates(schedule_data)
    else:
        logger.warning("调度数据为空，生成默认调度数据")

    # 时间槽
    trade_data['time_slot'] = trade_data['trade_time'].dt.floor(time_granularity)
    broadcast_data['time_slot'] = broadcast_data['arrive_time'].dt.floor(time_granularity)
    if not schedule_data.empty:
        schedule_data['time_slot'] = schedule_data['plan_departure_time'].dt.floor(time_granularity)

    # 通道映射
    invalid_routes = []
    def map_to_channel(route_id):
        if pd.isna(route_id) or route_id is None:
            invalid_routes.append('None')
            return None
        route_id = str(route_id).strip()
        for channel, routes in CHANNEL_MAPPING.items():
            if route_id in routes:
                return channel
        invalid_routes.append(route_id)
        return None

    trade_data['channel'] = trade_data['route_id'].apply(map_to_channel)
    broadcast_data['channel'] = broadcast_data['route_id'].apply(map_to_channel)
    trade_data.dropna(subset=['channel'], inplace=True)
    broadcast_data.dropna(subset=['channel'], inplace=True)
    logger.info("无效通道 - 交易: %d, 报站: %d", trade_data['channel'].isna().sum(), broadcast_data['channel'].isna().sum())

    # 客流聚合，确保整数
    trade_flow = trade_data.groupby(['time_slot', 'channel']).size().reset_index(name='total_flow')  # size 返回 int
    broadcast_flow = broadcast_data.groupby(['time_slot', 'channel']).agg({
        'board_amount': 'sum',
        'off_amount': 'sum'
    }).reset_index()
    broadcast_flow['total_flow'] = (broadcast_flow['board_amount'] + broadcast_flow['off_amount']).astype(int)
    flow_data = trade_flow.merge(
        broadcast_flow[['time_slot', 'channel', 'total_flow']],
        on=['time_slot', 'channel'],
        how='outer',
        suffixes=('_trade', '_broadcast')
    )
    flow_data['total_flow'] = flow_data['total_flow_trade'].combine_first(flow_data['total_flow_broadcast']).astype(int)
    flow_data.drop(['total_flow_trade', 'total_flow_broadcast'], axis=1, inplace=True)
    logger.info("合并客流数据记录数: %d", len(flow_data))

    # 异常值处理（优化 Winsorizing：用 0.01/0.99 quantile，并添加绝对上限）
    Q1 = flow_data['total_flow'].quantile(0.01)
    Q3 = flow_data['total_flow'].quantile(0.99)
    logger.info("客流 Winsorizing 前分布: %s", flow_data['total_flow'].describe())
    logger.info("Q1: %.2f, Q3: %.2f", Q1, Q3)
    flow_data['total_flow'] = np.clip(flow_data['total_flow'], Q1, min(Q3, 500))  # 绝对上限 500，基于公交容量
    flow_data['total_flow'] = np.round(flow_data['total_flow']).astype(int)
    logger.info("客流异常值处理完成，Winsorizing 范围: [%.2f, %.2f]", Q1, min(Q3, 500))
    logger.info("客流 Winsorizing 后分布: %s", flow_data['total_flow'].describe())

    # 检查零值和高值比例
    for channel in ['C1', 'C2', 'C3', 'C4', 'C5']:
        channel_flow = flow_data[flow_data['channel'] == channel]['total_flow']
        zero_ratio = (channel_flow == 0).mean() if not channel_flow.empty else 1.0
        high_ratio = (channel_flow > 200).mean() if not channel_flow.empty else 0.0
        logger.info("通道 %s 零值比例: %.2f%%, 高值 (>200) 比例: %.2f%%", channel, zero_ratio * 100, high_ratio * 100)

    # 调度特征
    if schedule_data.empty:
        time_slots = flow_data['time_slot'].unique()
        channels = list(CHANNEL_MAPPING.keys())
        schedule_data = pd.DataFrame([
            {'time_slot': ts, 'channel': ch, 'dispatch_count': 1, 'single_trip_duration': 10}
            for ts in time_slots for ch in channels
        ])
    else:
        dispatch_count = schedule_data.groupby(['time_slot', 'channel']).size().reset_index(name='dispatch_count')
        schedule_data = schedule_data.merge(dispatch_count, on=['time_slot', 'channel'], how='left')
        schedule_data['dispatch_count'] = schedule_data['dispatch_count'].fillna(1).astype(int)
        schedule_data['single_trip_duration'] = schedule_data['single_trip_duration'].fillna(10).astype(int)

    # 时间特征
    flow_data['hour'] = flow_data['time_slot'].dt.hour
    flow_data['minute'] = flow_data['time_slot'].dt.minute
    flow_data['hour_sin'] = np.sin(2 * np.pi * flow_data['hour'] / 24)
    flow_data['hour_cos'] = np.cos(2 * np.pi * flow_data['hour'] / 24)
    flow_data['minute_sin'] = np.sin(2 * np.pi * flow_data['minute'] / 60)
    flow_data['minute_cos'] = np.cos(2 * np.pi * flow_data['minute'] / 60)
    flow_data['weekday'] = flow_data['time_slot'].dt.weekday
    flow_data['is_holiday'] = flow_data['time_slot'].dt.date.apply(lambda x: 1 if x in China(years=datetime.now().year) else 0)
    flow_data['is_peak'] = flow_data['hour'].apply(lambda x: 1 if x in [7, 8, 17, 18] else 0)
    flow_data['is_weekend'] = flow_data['weekday'].apply(lambda x: 1 if x >= 5 else 0)
    logger.info("时间特征提取完成，节假日记录数: %d, 高峰期记录数: %d", flow_data['is_holiday'].sum(), flow_data['is_peak'].sum())

    # 天气特征
    weather_data['time_slot'] = weather_data['datetime'].dt.floor(time_granularity)
    weather_data['temp_comfort'] = weather_data['temperature'].apply(lambda x: 1 if 15 <= x <= 25 else 0)
    weather_data['rain_category'] = pd.cut(
        weather_data['precipitation'],
        bins=[-1, 0, 2, 5, np.inf],
        labels=['no_rain', 'light_rain', 'moderate_rain', 'heavy_rain']
    )
    logger.info("天气数据列: %s", weather_data.columns.tolist())
    logger.info("降雨分类分布: %s", weather_data['rain_category'].value_counts().to_dict())

    enc = OneHotEncoder(sparse=False, handle_unknown='ignore')
    rain_encoded = enc.fit_transform(weather_data[['rain_category']].fillna('no_rain'))
    rain_df = pd.DataFrame(rain_encoded, columns=enc.get_feature_names_out(['rain_category']))
    weather_data = pd.concat([weather_data, rain_df], axis=1)
    weather_data.drop(['rain_category'], axis=1, inplace=True, errors='ignore')
    logger.info("天气特征处理完成，编码后列: %s", weather_data.columns.tolist())

    # 合并数据
    data = flow_data.merge(
        schedule_data[['time_slot', 'channel', 'dispatch_count', 'single_trip_duration']],
        on=['time_slot', 'channel'],
        how='left'
    )
    data = data.merge(
        weather_data[['time_slot', 'temperature', 'precipitation', 'wind_speed', 'temp_comfort'] + list(enc.get_feature_names_out(['rain_category']))],
        on='time_slot',
        how='left'
    )

    # 添加滞后特征，确保整数
    for lag in [1, 2]:
        data[f'lag_flow_{lag}'] = data.groupby('channel')['total_flow'].shift(lag)
        data[f'lag_flow_{lag}'] = data[f'lag_flow_{lag}'].fillna(data['total_flow'].mean()).astype(int)

    # 填充缺失值
    for column in data.columns:
        nan_count = data[column].isna().sum()
        if nan_count > 0:
            logger.warning("特征 %s 包含 %d 个NaN值", column, nan_count)
            if column in ['dispatch_count']:
                data[column].fillna(1, inplace=True)
            elif column in ['single_trip_duration']:
                data[column].fillna(10, inplace=True)
            elif column in ['temperature']:
                data[column].fillna(data[column].mean() if not data[column].isna().all() else 20, inplace=True)
            elif column in ['precipitation', 'wind_speed']:
                data[column].fillna(0, inplace=True)
            elif column in ['temp_comfort']:
                data[column].fillna(1 if 15 <= data['temperature'].mean() <= 25 else 0, inplace=True)
            elif column in enc.get_feature_names_out(['rain_category']):
                data[column].fillna(1 if column == 'rain_category_no_rain' else 0, inplace=True)
            elif column.startswith('lag_flow_'):
                data[column].fillna(data['total_flow'].mean(), inplace=True)
            else:
                data[column].fillna(0, inplace=True)

    # 特征缩放
    features = ['hour_sin', 'hour_cos', 'minute_sin', 'minute_cos', 'weekday', 'is_holiday', 'is_peak', 'is_weekend',
                'dispatch_count', 'single_trip_duration', 'temperature', 'precipitation', 'wind_speed', 'temp_comfort'] + \
               list(enc.get_feature_names_out(['rain_category'])) + [f'lag_flow_{lag}' for lag in [1, 2]]
    scaler = StandardScaler()
    data[features] = scaler.fit_transform(data[features])
    logger.info("数据集合并完成，记录数: %d, 耗时: %.2f秒", len(data), time.time() - start_time)
    return data, scaler, enc, weather_data, features

# 计算 MAPE
def mean_absolute_percentage_error(y_true, y_pred, epsilon=1.0):
    y_true, y_pred = np.array(y_true), np.array(y_pred)
    return np.mean(np.abs((y_true - y_pred) / (y_true + epsilon))) * 100

# 训练模型并保存
def train_and_save_models(data, features, window_days):
    start_time = time.time()
    logger.info("开始训练并保存 XGBoost 模型，窗口天数: %d", window_days)
    window_start = data['time_slot'].max() - timedelta(days=window_days)
    train_data = data[data['time_slot'] >= window_start].copy()
    logger.info("训练数据记录数: %d", len(train_data))

    channels = ['C1', 'C2', 'C3', 'C4', 'C5']
    models = {}
    os.makedirs(CONFIG['model_dir'], exist_ok=True)

    for channel in channels:
        try:
            channel_data = train_data[train_data['channel'] == channel].copy()
            if len(channel_data) < 10:
                logger.warning("通道 %s 训练数据不足: %d", channel, len(channel_data))
                continue

            X_train = channel_data[features].values
            y_train = channel_data['total_flow'].values

            param_grid = {
                'n_estimators': [100, 200],
                'max_depth': [4, 5],
                'learning_rate': [0.05, 0.1]
            }
            grid_search = GridSearchCV(
                XGBRegressor(random_state=42, min_child_weight=1, subsample=0.8, colsample_bytree=0.8),
                param_grid,
                cv=TimeSeriesSplit(n_splits=3),
                scoring='neg_mean_squared_error',
                n_jobs=4
            )
            grid_search.fit(X_train, y_train)
            models[channel] = grid_search.best_estimator_
            joblib.dump(models[channel], os.path.join(CONFIG['model_dir'], f'model_{channel}.pkl'))
            logger.info("通道 %s 模型训练完成，最佳参数: %s, 最佳 MSE: %.2f",
                        channel, grid_search.best_params_, -grid_search.best_score_)
        except Exception as e:
            logger.error("通道 %s 训练失败: %s", channel, str(e))
            models[channel] = XGBRegressor(n_estimators=200, max_depth=5, learning_rate=0.05, random_state=42)
            models[channel].fit(X_train, y_train)
            joblib.dump(models[channel], os.path.join(CONFIG['model_dir'], f'model_{channel}.pkl'))
    logger.info("模型训练并保存总耗时: %.2f秒", time.time() - start_time)
    return models

# 加载模型
def load_models():
    models = {}
    for channel in ['C1', 'C2', 'C3', 'C4', 'C5']:
        model_path = os.path.join(CONFIG['model_dir'], f'model_{channel}.pkl')
        logger.info("尝试加载模型文件: %s", model_path)
        if os.path.exists(model_path):
            models[channel] = joblib.load(model_path)
            logger.info("成功加载通道 %s 模型", channel)
        else:
            logger.warning("通道 %s 模型文件不存在，使用默认模型", channel)
            models[channel] = XGBRegressor(n_estimators=100, max_depth=4, learning_rate=0.1, random_state=42)
    return models

# 预测与验证
def predict_and_validate(data, scaler, enc, weather_data, window_days, time_granularity, features, models, validate=False):
    start_time = time.time()
    logger.info("开始预测与验证，窗口天数: %d, 验证模式: %s", window_days, validate)
    window_start = data['time_slot'].max() - timedelta(days=window_days)

    if validate:
        train_end = data['time_slot'].max() - timedelta(days=7)
        train_data = data[(data['time_slot'] >= window_start) & (data['time_slot'] < train_end)].copy()
        valid_data = data[data['time_slot'] >= train_end].copy()
        logger.info("训练数据记录数: %d, 验证数据记录数: %d", len(train_data), len(valid_data))
    else:
        train_data = data[data['time_slot'] >= window_start].copy()
        valid_data = pd.DataFrame()
        logger.info("训练数据记录数: %d", len(train_data))

    channels = ['C1', 'C2', 'C3', 'C4', 'C5']
    valid_results = []
    metrics = {'overall': {}, 'by_channel': {}}

    if validate:
        for channel in channels:
            try:
                valid_channel_data = valid_data[valid_data['channel'] == channel].copy()
                if valid_channel_data.empty:
                    logger.warning("通道 %s 验证数据为空", channel)
                    continue
                X_valid = valid_channel_data[features].values
                y_valid = valid_channel_data['total_flow'].values
                model = models.get(channel, XGBRegressor(n_estimators=100, max_depth=4, learning_rate=0.1, random_state=42))
                y_pred = model.predict(X_valid)
                y_pred = np.clip(np.round(y_pred), 0, None).astype(int)
                valid_channel_data['predicted_flow'] = y_pred
                valid_channel_data['actual_flow'] = y_valid
                valid_results.append(valid_channel_data[['time_slot', 'channel', 'actual_flow', 'predicted_flow']])

                metrics['by_channel'][channel] = {
                    'MSE': mean_squared_error(y_valid, y_pred),
                    'MAE': mean_absolute_error(y_valid, y_pred),
                    'MAPE': mean_absolute_percentage_error(y_valid, y_pred),
                    'R2': r2_score(y_valid, y_pred)
                }
                logger.info("通道 %s 验证指标: MSE=%.2f, MAE=%.2f, MAPE=%.2f%%, R2=%.2f",
                            channel, metrics['by_channel'][channel]['MSE'], metrics['by_channel'][channel]['MAE'],
                            metrics['by_channel'][channel]['MAPE'], metrics['by_channel'][channel]['R2'])
            except Exception as e:
                logger.error("通道 %s 验证失败: %s", channel, str(e))
                metrics['by_channel'][channel] = {'MSE': 0, 'MAE': 0, 'MAPE': 0, 'R2': 0}

    if validate and valid_results:
        valid_results_df = pd.concat(valid_results, ignore_index=True)
        metrics['overall'] = {
            'MSE': mean_squared_error(valid_results_df['actual_flow'], valid_results_df['predicted_flow']),
            'MAE': mean_absolute_error(valid_results_df['actual_flow'], valid_results_df['predicted_flow']),
            'MAPE': mean_absolute_percentage_error(valid_results_df['actual_flow'], valid_results_df['predicted_flow']),
            'R2': r2_score(valid_results_df['actual_flow'], valid_results_df['predicted_flow'])
        }
        logger.info("整体验证指标: MSE=%.2f, MAE=%.2f, MAPE=%.2f%%, R2=%.2f",
                    metrics['overall']['MSE'], metrics['overall']['MAE'], metrics['overall']['MAPE'], metrics['overall']['R2'])
    else:
        valid_results_df = pd.DataFrame()

    predict_start = time.time()
    now = datetime.now()

    # 构建通道服务时间窗口
    all_route_ids = set()
    for routes in CHANNEL_MAPPING.values():
        all_route_ids.update(routes)
    route_service_times = fetch_route_service_times(list(all_route_ids))
    channel_windows = build_channel_service_windows(route_service_times)

    # 获取实时客流最新记录，用于近端滞后特征
    latest_pf = fetch_latest_passenger_flow()
    latest_time = latest_pf.get('current_time')
    recent_valid = False
    if latest_time is not None:
        try:
            recent_valid = (now - latest_time) <= timedelta(minutes=20)
        except Exception:
            recent_valid = False
    granularity_minutes = {'15min': 15, '30min': 30, '1h': 60}.get(time_granularity, 15)
    future_times = [now + timedelta(minutes=x) for x in range(15, 61, 15)]
    predictions = []
    for channel_idx, channel in enumerate(channels, 1):
        try:
            cumulative_flow = {15: 0, 30: 0, 60: 0}
            for t in future_times:
                minutes_ahead = int((t - now).total_seconds() / 60)
                if minutes_ahead > 60:
                    continue
                future_slot = pd.Timestamp(t).replace(second=0, microsecond=0).floor(time_granularity)
                future_data = pd.DataFrame({
                    'time_slot': [future_slot],
                    'hour': [future_slot.hour],
                    'minute': [future_slot.minute],
                    'hour_sin': [np.sin(2 * np.pi * future_slot.hour / 24)],
                    'hour_cos': [np.cos(2 * np.pi * future_slot.hour / 24)],
                    'minute_sin': [np.sin(2 * np.pi * future_slot.minute / 60)],
                    'minute_cos': [np.cos(2 * np.pi * future_slot.minute / 60)],
                    'weekday': [future_slot.weekday()],
                    'is_holiday': [1 if future_slot.date() in China(years=datetime.now().year) else 0],
                    'is_peak': [1 if future_slot.hour in [7, 8, 17, 18] else 0],
                    'is_weekend': [1 if future_slot.weekday() >= 5 else 0],
                    'dispatch_count': [train_data[train_data['channel'] == channel]['dispatch_count'].mean() if not train_data[train_data['channel'] == channel].empty else 1],
                    'single_trip_duration': [train_data[train_data['channel'] == channel]['single_trip_duration'].mean() if not train_data[train_data['channel'] == channel].empty else 10],
                    'temperature': [weather_data['temperature'].mean() if not weather_data['temperature'].isna().all() else 20],
                    'precipitation': [weather_data['precipitation'].mean() if not weather_data['precipitation'].isna().all() else 0],
                    'wind_speed': [weather_data['wind_speed'].mean() if not weather_data['wind_speed'].isna().all() else 0],
                    'temp_comfort': [1 if 15 <= weather_data['temperature'].mean() <= 25 else 0]
                })
                for lag in [1, 2]:
                    lag_time = future_slot - timedelta(minutes=15*lag)
                    lag_flow = train_data[(train_data['channel'] == channel) & (train_data['time_slot'] == lag_time)]['total_flow']
                    fallback = lag_flow.mean() if not lag_flow.empty else train_data['total_flow'].mean()
                    future_data[f'lag_flow_{lag}'] = [fallback]

                # 使用实时通道计数加强近端滞后
                if recent_valid:
                    try:
                        latest_inc = latest_pf.get('recent_increment', {}).get(channel, 0)
                        # 仅覆盖 lag_flow_1，使用增量值更贴近最近一档流量
                        future_data['lag_flow_1'] = [max(int(latest_inc), int(future_data['lag_flow_1'].iloc[0]))]
                    except Exception:
                        pass
                for col in enc.get_feature_names_out(['rain_category']):
                    rain_mode = weather_data['rain_category'].mode()[0] if 'rain_category' in weather_data.columns and not weather_data['rain_category'].isna().all() else 'no_rain'
                    future_data[col] = [1 if col == f'rain_category_{rain_mode}' else 0]

                X_future = scaler.transform(future_data[features])
                model = models.get(channel, XGBRegressor(n_estimators=100, max_depth=4, learning_rate=0.1, random_state=42))

                # 服务时间关停校验：若非服务时段（含结束后缓冲）则置0
                active = is_active_at(future_slot.to_pydatetime(), channel_windows.get(channel, []), CONFIG.get('service_end_buffer_minutes', 15))
                if not active:
                    flow_pred = 0
                else:
                    flow_pred = int(np.clip(np.round(model.predict(X_future)[0]), 0, None))

                for period in [15, 30, 60]:
                    if minutes_ahead <= period:
                        cumulative_flow[period] += flow_pred

            prediction = {
                'passengewayIndex': channel_idx,
                'passengewayName': CHANNEL_NAME_MAPPING[channel],
                'instationMin15': cumulative_flow[15],
                'instationMin30': cumulative_flow[30],
                'instationMin60': cumulative_flow[60]
            }
            predictions.append(prediction)
        except Exception as e:
            logger.error("通道 %s 预测失败: %s", channel, str(e))
            predictions.append({
                'passengewayIndex': channel_idx,
                'passengewayName': CHANNEL_NAME_MAPPING[channel],
                'instationMin15': 1,
                'instationMin30': 1,
                'instationMin60': 1
            })

    logger.info("通道客流预测结果: %s, 预测耗时: %.2f秒", predictions, time.time() - predict_start)
    logger.info("预测与验证总耗时: %.2f秒", time.time() - start_time)
    return predictions, valid_results_df, metrics

def compute_and_cache_predictions(window_days=None, time_granularity=None):
    total_start = time.time()
    try:
        wd = window_days or CONFIG['window_days']
        tg = time_granularity or CONFIG['time_granularity']
        logger.info("开始离线计算并缓存预测结果，窗口天数: %d, 粒度: %s", wd, tg)
        trade_data, broadcast_data, schedule_data = fetch_data_from_db(wd)
        today = datetime.now().strftime('%Y-%m-%d')
        weather_data = fetch_real_time_weather(today, today, tg)
        data, scaler, enc, weather_data, features = preprocess_data(trade_data, broadcast_data, schedule_data, weather_data, tg)
        models = load_models()
        predictions, _, _ = predict_and_validate(data, scaler, enc, weather_data, wd, tg, features, models, validate=False)
        save_predictions_cache(predictions)
        logger.info("离线预测完成并写入缓存，总耗时: %.2f秒", time.time() - total_start)
        return predictions
    except Exception as e:
        logger.error("离线计算预测失败: %s", str(e), exc_info=True)
        return None

# Flask 接口 - 预测
@app.route('/station/passenger/forecast/passengewayList', methods=['GET'])
def get_passenger_forecast():
    total_start = time.time()
    try:
        logger.info("接收到客流预测请求")
        # 先读缓存，避免每次请求查库
        cached = load_predictions_cache(max_age_seconds=300)
        if cached is not None:
            predictions = cached
            logger.info("命中预测缓存，直接返回")
        else:
            logger.warning("预测缓存缺失，执行一次计算(可能较慢)")
            predictions = compute_and_cache_predictions()

        response = {
            'code': '0',
            'data': predictions,
            'message': 'success',
            'status': 200
        }
        logger.info("预测接口总耗时: %.2f秒", time.time() - total_start)
        return jsonify(response)
    except Exception as e:
        logger.error("预测接口处理失败: %s", str(e), exc_info=True)
        return jsonify({
            'code': '1',
            'data': [],
            'message': f'Error: {str(e)}',
            'status': 500
        })

# Flask 接口 - 验证
@app.route('/station/passenger/forecast/validate', methods=['GET'])
def validate_passenger_forecast():
    total_start = time.time()
    try:
        logger.info("接收到客流预测验证请求")
        trade_data, broadcast_data, schedule_data = fetch_data_from_db(CONFIG['window_days'])
        today = datetime.now().strftime('%Y-%m-%d')
        weather_data = fetch_real_time_weather(today, today, CONFIG['time_granularity'])
        preprocess_start = time.time()
        data, scaler, enc, weather_data, features = preprocess_data(trade_data, broadcast_data, schedule_data, weather_data, CONFIG['time_granularity'])
        logger.info("数据预处理耗时: %.2f秒", time.time() - preprocess_start)
        models = load_models()
        predictions, valid_results, metrics = predict_and_validate(data, scaler, enc, weather_data, CONFIG['window_days'], CONFIG['time_granularity'], features, models, validate=True)

        response = {
            'code': '0',
            'data': {
                'predictions': predictions,
                'validation_results': valid_results[['time_slot', 'channel', 'actual_flow', 'predicted_flow']].to_dict(orient='records') if not valid_results.empty else [],
                'metrics': {
                    'overall': metrics.get('overall', {}),
                    'by_channel': metrics.get('by_channel', {})
                }
            },
            'message': '验证成功',
            'status': 200
        }
        logger.info("验证接口总耗时: %.2f秒", time.time() - total_start)
        return jsonify(response)
    except Exception as e:
        logger.error("验证接口处理失败: %s", str(e), exc_info=True)
        return jsonify({
            'code': '1',
            'data': {
                'predictions': [],
                'validation_results': [],
                'metrics': {}
            },
            'message': f'Error: {str(e)}',
            'status': 500
        })

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8849, debug=False)