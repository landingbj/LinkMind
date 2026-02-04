package ai.finetune.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AttributeUtil {

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

}
