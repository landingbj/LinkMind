package ai.medusa.utils;

import ai.common.pojo.Medusa;
import ai.common.pojo.VectorStoreConfig;
import cn.hutool.core.util.StrUtil;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class PromptCacheConfig {
    public static final int POOL_INITIAL = 1;
    public static final int POOL_DIVERSIFIED = 2;
    public static final int POOL_CACHE_PUT = 3;
    public static int PRODUCER_THREADS = 1;
    public static int CONSUMER_THREADS = 1;
    public static int TOTAL_THREAD_COUNT;
    public static int THREAD_RUN_LIMIT;
    public static int LLM_DIVERSIFY_LIMIT = 1;
    public static int REASON_DIVERSIFY_LIMIT = 1;
    public static int TREE_DIVERSIFY_LIMIT = 1;
    public static final int PRODUCER_LIMIT = 1;
    public static final int POOL_CACHE_SIZE = 10000;
    public static final int COMPLETION_CACHE_SIZE = 200000;
    public static final int RAW_ANSWER_CACHE_SIZE = 10000;

    public static final String DIVERSIFY_PROMPT = "### 任务\n任务：以下提供一个提示词，请根据这个提示词推测用户之后可能输入的提示词。结果只返回提示词本身，" +
            "不需要提供相关描述和分析。如果有多种可能性，最多只返回%d个提示词，并将其以JSON结构输出。\n\n" +
            "### 提示词\n```\n%s\n```\n\n" +
            "### 执行要求\n" +
            "1. 仅输出JSON格式，不要输出任何解释性文字。\n" +
            "2. 输出的提示词是用户可能向大语言模型输入的。\n" +
            "3. 输出的提示词语言要与输入的提示词一致。\n\n" +
            "### 输出格式\n" +
            "输出格式为JSON，结构如下：\n" +
            "```json\n" +
            "{\n" +
            "  \"questions\": [\n    \"<问题1>\",\n    \"<问题2>\",\n    \"<问题3>\"\n  ]\n" +
            "}\n" +
            "```\n" +
            "输出示例：\n" +
            "```json\n" +
            "{\n" +
            "  \"questions\": [\n" +
            "    \"小说的主角可以设定为岳飞的后人吗？\",\n" +
            "    \"能否加入一些宋朝的科技元素，比如火药在战斗中的应用？\",\n" +
            "    \"小说中是否可以出现真实的历史人物，如秦桧或岳飞？\"\n" +
            "  ]\n" +
            "}\n" +
            "```";

    public static final String REASON_DIVERSIFY_PROMPT = "### 任务\n" +
            "任务：以下提供一个提示词和推理模型的思考过程，请根据两者总结推理过程的核心要点，推测用户之后最可能输入的提示词。结果只返回提示词本身，不需要提供相关描述和分析。如果有多种可能性，最多只返回%d个提示词，并将其以JSON结构输出。\n\n" +
            "### 提示词\n```\n%s\n```\n\n" +
            "### 思考过程\n```\n%s\n```\n\n" +
            "### 执行要求\n" +
            "1. 仅输出JSON格式，不要输出任何解释性文字。\n" +
            "2. 输出的提示词是用户可能向大语言模型输入的。\n" +
            "3. 输出的提示词语言要与输入的提示词一致。\n\n" +
            "### 输出格式\n" +
            "输出格式为JSON，结构如下：\n" +
            "```json\n" +
            "{\n" +
            "  \"questions\": [\n    \"<问题1>\",\n    \"<问题2>\",\n    \"<问题3>\"\n  ]\n" +
            "}\n" +
            "```\n" +
            "输出示例：\n" +
            "```json\n" +
            "{\n" +
            "  \"questions\": [\n" +
            "    \"小说的主角可以设定为岳飞的后人吗？\",\n" +
            "    \"能否加入一些宋朝的科技元素，比如火药在战斗中的应用？\",\n" +
            "    \"小说中是否可以出现真实的历史人物，如秦桧或岳飞？\"\n" +
            "  ]\n" +
            "}\n" +
            "```";

    public static String LOCATE_ALGORITHM = "hash";
    public static boolean MEDUSA_ENABLE = false;
    public static boolean MEDUSA_CACHE_ENABLE = false;
    public static boolean MEDUSA_MEMORY_ENABLE = false;
    public static String MEDUSA_CATEGORY = "medusa";
    public static boolean MEDUSA_FLUSH = true;
    public static String MEDUSA_TREE_CATEGORY = "medusa_tree";
    public static final int QA_SIMILARITY_TOP_K = 30;
    public static double MIN_SIMILARITY_CUTOFF = 0.2;
    public static int CACHE_HIT_WINDOW = 16;
    public static double CACHE_HIT_RATIO = 0.3;
    public static boolean DYNAMIC_SIMILARITY = true;
    public static double QA_SIMILARITY_CUTOFF;
    public static final int TREE_SIMILARITY_TOP_K = 3;

    public static final int SUBSTRING_THRESHOLD = 2;
    public static final int START_CORE_THRESHOLD = 3;
    public static final int ANSWER_CORE_THRESHOLD = 4;
    public static double LCS_RATIO_PROMPT_INPUT = 0.6;
    public static int MEDUSA_PRIORITY = 1;

    public static String CACHE_PERSISTENT_PATH = "./medusa_cache";
    public static int CACHE_PERSISTENT_BATCH_SIZE = 20;

    public static List<Double> TEMPERATURE_TOLERANCE;

    @Getter
    private static Boolean enableLlmDriver = false;
    @Getter
    private static Boolean enableTreeDriver = true;
    @Getter
    private static Boolean enableRagDriver = true;
    @Getter
    private static Boolean enablePageDriver = true;
    @Getter
    private static Boolean enableReasonDriver = false;
    @Getter
    private static Long consumeDelay = 0L;
    @Getter
    private static Long preDelay = 0L;
    @Getter
    private static String reasonModel = null;

    public static void init(List<VectorStoreConfig> vectorStoreList, Medusa config) {
        if (vectorStoreList != null && !vectorStoreList.isEmpty() && config != null) {
            MEDUSA_ENABLE = config.getEnable() != null ? config.getEnable() : MEDUSA_ENABLE;
            MEDUSA_CACHE_ENABLE = config.getCache() != null ? config.getCache() : MEDUSA_CACHE_ENABLE;
            MEDUSA_MEMORY_ENABLE = config.getMemory() != null ? config.getMemory() : MEDUSA_MEMORY_ENABLE;
            LOCATE_ALGORITHM = StrUtil.isBlank(config.getAlgorithm()) ? LOCATE_ALGORITHM : config.getAlgorithm();
            String defaultCategory = vectorStoreList.get(0).getDefaultCategory();
            reasonModel = config.getReasonModel();
            MEDUSA_CATEGORY = MEDUSA_CATEGORY + "-" + defaultCategory;
            enableTreeDriver = LOCATE_ALGORITHM.contains("graph");
            if (LOCATE_ALGORITHM.contains("llm")) {
                enableLlmDriver = true;
                if (reasonModel != null) {
                    enableReasonDriver = true;
                }
            }
            consumeDelay = config.getConsumeDelay() != null ? config.getConsumeDelay() : consumeDelay;
            preDelay = config.getPreDelay() != null ? config.getPreDelay() : preDelay;
            LCS_RATIO_PROMPT_INPUT = config.getLcsRatioPromptInput() != null ? config.getLcsRatioPromptInput() : LCS_RATIO_PROMPT_INPUT;
            initPipelineConfig(config);
        }
    }

    private static void initPipelineConfig(Medusa config) {
        PRODUCER_THREADS = config.getProducerThreadNum() != null ? config.getProducerThreadNum() : PRODUCER_THREADS;
        CONSUMER_THREADS = config.getConsumerThreadNum() != null ? config.getConsumerThreadNum() : CONSUMER_THREADS;
        TOTAL_THREAD_COUNT = PRODUCER_THREADS + CONSUMER_THREADS;
        THREAD_RUN_LIMIT = TOTAL_THREAD_COUNT * QA_SIMILARITY_TOP_K * 5;

        MEDUSA_PRIORITY = config.getPriority() != null ? config.getPriority() : MEDUSA_PRIORITY;

        LLM_DIVERSIFY_LIMIT = config.getAheads() != null ? config.getAheads() : LLM_DIVERSIFY_LIMIT;
        REASON_DIVERSIFY_LIMIT = config.getAheads() != null ? config.getAheads() * 2 : REASON_DIVERSIFY_LIMIT;
        TREE_DIVERSIFY_LIMIT = config.getAheads() != null ? config.getAheads() * 3 : LLM_DIVERSIFY_LIMIT;

        MIN_SIMILARITY_CUTOFF = config.getSimilarityCutoff() != null ? config.getSimilarityCutoff() : MIN_SIMILARITY_CUTOFF;
        QA_SIMILARITY_CUTOFF = MIN_SIMILARITY_CUTOFF;
        CACHE_PERSISTENT_PATH = config.getCachePersistentPath() != null ? config.getCachePersistentPath() : CACHE_PERSISTENT_PATH;
        CACHE_PERSISTENT_BATCH_SIZE = config.getCachePersistentBatchSize() != null ? config.getCachePersistentBatchSize() : CACHE_PERSISTENT_BATCH_SIZE;

        CACHE_HIT_WINDOW = config.getCacheHitWindow() != null ? config.getCacheHitWindow() : CACHE_HIT_WINDOW;
        CACHE_HIT_RATIO = config.getCacheHitRatio() != null ? config.getCacheHitRatio() : CACHE_HIT_RATIO;
        DYNAMIC_SIMILARITY = config.getDynamicSimilarity() != null ? config.getDynamicSimilarity() : DYNAMIC_SIMILARITY;

        MEDUSA_FLUSH = config.getFlush() != null ? config.getFlush() : MEDUSA_FLUSH;

        TEMPERATURE_TOLERANCE = cutIntervalByStep(config.getTemperatureTolerance() != null ? config.getTemperatureTolerance() : -1);
    }

    private static List<Double> cutIntervalByStep(double step) {
        if (step <= 0) {
            return null;
        }
        List<Double> points = new ArrayList<>();
        double current = 0.0;
        while (current < 2.0) {
            points.add(roundToTwoDecimalPlaces(current));
            current += step;
        }
        if (points.get(points.size() - 1) < 2.0 - 1e-9) {
            points.add(2.0);
        } else {
            points.set(points.size() - 1, 2.0);
        }
        return points;
    }

    private static double roundToTwoDecimalPlaces(double value) {
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
