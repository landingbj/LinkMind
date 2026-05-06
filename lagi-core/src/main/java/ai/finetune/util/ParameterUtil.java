package ai.finetune.util;

import ai.config.pojo.ModelMapper;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ParameterUtil {

    public static void attrMapping(String mappingStr, Object source, Object target) {
        if(mappingStr == null) {
            return;
        }
        List<List<String>> mappingList = Arrays.stream(mappingStr.split(","))
                .map(mapping -> Arrays.stream(mapping.split("[:]")).collect(Collectors.toList())).collect(Collectors.toList());
        for (List<String> mapping : mappingList) {
            if (mapping.size() == 2) {
                String sourceKey = mapping.get(0);
                String targetKey = mapping.get(1);

                Object fieldValue1 = BeanUtil.getFieldValue(target, targetKey);
                if(fieldValue1 instanceof String) {
                    StringBuilder targetValue = new StringBuilder();
                    String[] sourceKeys = sourceKey.split("\\.");
                    for (String key : sourceKeys) {
                        Object fieldValue = BeanUtil.getFieldValue(source, key);
                        targetValue.append(fieldValue);
                    }
                    BeanUtil.setFieldValue(target, targetKey, targetValue.toString());
                } else {
                    BeanUtil.setFieldValue(target, targetKey, BeanUtil.getFieldValue(source, sourceKey));
                }
            }
        }
    }

    public static void attrAccess(String mappingStr, Object dataSource, Object target) {
        Map<String, Object> map = BeanUtil.beanToMap(dataSource);
        String[] mappingList = mappingStr.split(",");
        for (String mapping : mappingList) {
            String[] pair = mapping.split("=");
            if (pair.length == 2) {
                String key = pair[0];
                String value = pair[1];
                Object fieldValue = BeanUtil.getFieldValue(target, key);
                if(fieldValue == null || fieldValue instanceof String) {
                    BeanUtil.setFieldValue(target, key, StrUtil.format(value, map));
                }
            }
        }
    }


    public static String buildCmd(ModelMapper modelMapper, JSONObject config, String cmdPattern, String volumes, String envs) {
        attrAccess(modelMapper.getAttrAccess(), config, config);
        String taskId = config.getStr("task_id");
        String parseType = modelMapper.getParseType();
        if(parseType == null) {
            parseType = "unparsed";
        }
        if(parseType.equals("unparsed")) {
            // 创建配置副本，过滤掉不需要传递给容器的字段
            JSONObject filteredConfig = filterContainerConfig(config);
            return StrUtil.format(cmdPattern, "--name " + taskId + " " + volumes + " " + envs + " " , filteredConfig.toString());
        }
        return null;
    }

    private static JSONObject filterContainerConfig(JSONObject originalConfig) {
        JSONObject filtered = new JSONObject();
        // 复制所有字段
        for (String key : originalConfig.keySet()) {
            // 排除这三个字段
            if (!"user_id".equals(key) && !"template_id".equals(key) && !"model_name".equals(key)) {
                filtered.set(key, originalConfig.get(key));
            }
        }
        return filtered;
    }

    /**
     * 匹配模型配置
     */
    public static ModelMapper matchModel(List<ModelMapper> models, JSONObject config) {
        String modelName = config.getStr("model_name", "");
        if (models == null) {
            return null;
        }
        for (ModelMapper model : models) {
            if(model == null) {
                continue;
            }
            String modelPattern = model.getModelPattern();
            if (modelPattern != null && Pattern.matches(modelPattern, modelName)) {
                return model;
            }
        }
        return null;
    }

}
