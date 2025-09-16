package ai.manager;

import ai.config.pojo.PnpConfig;
import ai.pnps.Pnp;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class PnpManager {
    @Getter
    private static PnpManager instance = new PnpManager();

    private PnpManager() {
    }

    private final Map<String, Pnp<?, ?>> pnpMap = new ConcurrentHashMap<>();

    public void register(List<PnpConfig> pnpConfigs) {
        if (pnpConfigs == null) {
            return;
        }
        pnpConfigs.forEach(conf -> {
            String driver = conf.getDriver();
            String name = conf.getName();
            try {
                Class<?> clazz = Class.forName(driver);
                Constructor<?> constructor = clazz.getConstructor(PnpConfig.class);
                Object o = constructor.newInstance(conf);
                pnpMap.put(name, (Pnp<?, ?>) o);
            } catch (Exception e) {
                log.error("Failed to register Pnp: {} with driver: {}", name, driver, e);
            }
        });
    }

    public Pnp<?, ?> get(String key) {
        return pnpMap.getOrDefault(key, null);
    }

    public List<Pnp<?, ?>> pnps() {
        return new ArrayList<>(pnpMap.values());
    }
}
