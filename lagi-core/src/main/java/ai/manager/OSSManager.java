package ai.manager;


import ai.config.pojo.OSSConfig;
import ai.oss.UniversalOSS;
import cn.hutool.core.bean.BeanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OSSManager {

    private final Logger log = LoggerFactory.getLogger(OSSManager.class);

    private final Map<String, UniversalOSS> ossMap = new ConcurrentHashMap<>();

    private OSSManager() {}
    private static final OSSManager INSTANCE = new OSSManager();
    public static OSSManager getInstance() {
        return INSTANCE;
    }

    public void  register(List<OSSConfig> ossConfigs) {
        ossConfigs.forEach(ossConfig -> {
            if(!Boolean.TRUE.equals(ossConfig.getEnable())) {
                return;
            }
            Class<?> clazz = null;
            try {
                clazz = Class.forName(ossConfig.getDriver());
                UniversalOSS oss = (UniversalOSS) clazz.getDeclaredConstructor().newInstance();
                BeanUtil.copyProperties(ossConfig, oss);
                UniversalOSS temp = ossMap.putIfAbsent(ossConfig.getName(), oss);
                if(temp != null) {
                    log.error("oss {} name {} is already exists!!", oss.getClass().getName(), ossConfig.getName());
                }
            } catch (Exception e) {
                log.error("oss {} name {} register failed error : {}", ossConfig.getDriver(), ossConfig.getName(), e.getMessage());
            }
        });
    }

    public UniversalOSS getOss(String name) {
        return ossMap.getOrDefault(name, null);
    }
}
