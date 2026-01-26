package ai.llm.service;

import ai.common.ModelService;
import ai.config.ContextLoader;
import ai.llm.adapter.ILlmAdapter;
import ai.manager.AIManager;
import ai.manager.LlmManager;
import cn.hutool.core.bean.BeanUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LlmRouterDispatcher {

    private static final Map<String, Integer> priorityMap = new ConcurrentHashMap<>();

    private static ILlmAdapter copyAdapter(ILlmAdapter adapter) {
        try {
            ILlmAdapter resAdapter = adapter.getClass().getDeclaredConstructor().newInstance();
            BeanUtil.copyProperties(adapter, resAdapter);
            return resAdapter;
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException ignored) {
        }
        return null;
    }

    public static List<ILlmAdapter> getRagAdapterV2(String indexData) {
        List<ILlmAdapter> adapters = LlmManager.getInstance().getAdapters();
        if (indexData == null) {
            return adapters;
        }
        return adapters.stream()
                .map(adapter ->{
                    ModelService m = (ModelService) copyAdapter(adapter);
                    if (m != null) {
                        m.setPriority(priorityMap.getOrDefault(m.getModel(), m.getPriority()));
                    }
                    return m;
                })
                .filter(Objects::nonNull)
                .sorted((o1, o2) -> {
                    Integer priority1 = o1.getPriority();
                    Integer priority2 = o2.getPriority();
                    if(0 > priority1) {
                        return 1;
                    }
                    if(0 > priority2) {
                        return -1;
                    }
                    String pattern1 = getPattern(o1.getRouter());
                    String pattern2 = getPattern(o2.getRouter());

                    boolean matches1 = matchPattern(pattern1, indexData);
                    boolean matches2 = matchPattern(pattern2, indexData);

                    int comparisonResult = 0;
                    if (matches1 && matches2) {
                        comparisonResult = ".+".equals(pattern1) ? 1 : (".+".equals(pattern2) ? -1 : 0);
                    } else if (matches1) {
                        comparisonResult = -1;
                    } else if (matches2) {
                        comparisonResult = 1;
                    }

                    if (comparisonResult == 0) {

                        if (priority1 == null) {
                            return priority2 == null ? 0 : 1;
                        } else if (priority2 == null) {
                            return -1;
                        } else {
                            return priority2.compareTo(priority1); // 使用compareTo方法比较优先级
                        }
                    }

                    return comparisonResult;
                })
                .map(modelService -> (ILlmAdapter) modelService)
                .collect(Collectors.toList());
    }

    public static List<ILlmAdapter> getRagAdapter(AIManager<ILlmAdapter> llmAdapterAIManager, String indexData) {
        if (indexData == null) {
            return llmAdapterAIManager.getAdapters();
        }
        return llmAdapterAIManager.getAdapters().stream()
                .map(adapter -> (ModelService) adapter)
                .sorted((o1, o2) -> {
                    String pattern1 = getPattern(o1.getRouter());
                    String pattern2 = getPattern(o2.getRouter());

                    boolean matches1 = matchPattern(pattern1, indexData);
                    boolean matches2 = matchPattern(pattern2, indexData);

                    int comparisonResult = 0;
                    if (matches1 && matches2) {
                        comparisonResult = ".+".equals(pattern1) ? 1 : (".+".equals(pattern2) ? -1 : 0);
                    } else if (matches1) {
                        comparisonResult = -1;
                    } else if (matches2) {
                        comparisonResult = 1;
                    }

                    if (comparisonResult == 0) {
                        Integer priority1 = o1.getPriority();
                        Integer priority2 = o2.getPriority();

                        if (priority1 == null) {
                            return priority2 == null ? 0 : 1;
                        } else if (priority2 == null) {
                            return -1;
                        } else {
                            return priority2.compareTo(priority1);
                        }
                    }

                    return comparisonResult;
                })
                .map(modelService -> (ILlmAdapter) modelService)
                .collect(Collectors.toList());
    }

    public static List<ILlmAdapter> getRagAdapter(String indexData) {
        return getRagAdapter(LlmManager.getInstance(), indexData);
    }

    private static String getPattern(String router) {
        if(router == null) {
            return null;
        }
        if(router.startsWith("rag(") && router.endsWith(")")) {
            return router.substring(4, router.length() - 1);
        }
        return null;
    }

    private static boolean matchPattern(String pattern, String indexData) {
        if(pattern == null) {
            return false;
        }
        try {
            return Pattern.matches(pattern, indexData);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void setPriority(String modelName, Integer priority) {
        priorityMap.put(modelName, priority);
    }

    public static void main(String[] args) {
        ContextLoader.loadContext();
        List<ILlmAdapter> llmAdapters = LlmRouterDispatcher.getRagAdapter("你好你是谁");
        llmAdapters.forEach(llmAdapter -> System.out.println(llmAdapter.getClass().getName()));
        LlmRouterDispatcher.setPriority("glm-3-turbo", -1);
        LlmRouterDispatcher.setPriority("moonshot-v1-8k", -1);
        llmAdapters = LlmRouterDispatcher.getRagAdapter("你好你是谁");
        llmAdapters.forEach(llmAdapter -> System.out.println(llmAdapter.getClass().getName()));
    }
}
