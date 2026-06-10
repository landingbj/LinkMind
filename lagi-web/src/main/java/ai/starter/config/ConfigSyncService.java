package ai.starter.config;

import ai.starter.config.impl.DeerFlowSyncServiceImpl;
import ai.starter.config.impl.HermesSyncServiceImpl;
import ai.starter.config.impl.OpenClawSyncServiceImpl;
import ai.starter.config.impl.OpenHumanSyncServiceImpl;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
public class ConfigSyncService {

    public static final int OpenClaw = 1;
    public static final int deerFlow = 1 << 1;
    public static final int Hermes = 1 << 2;
    public static final int OpenHuman = 1 << 3;


    @Getter
    private int all = 0;

    private int port = 8080;


    private final Map<Integer, IConfigSyncService> configSyncServices = new HashMap<>();

    public ConfigSyncService(String[] paths) {
        init(paths);
    }

    public ConfigSyncService(int port, String[] paths) {
        init(paths);
        this.port = port;
    }

    private void init(String[] paths) {
        configSyncServices.put(OpenClaw, new OpenClawSyncServiceImpl(pathAt(paths, 0)));
        configSyncServices.put(deerFlow, new DeerFlowSyncServiceImpl(pathAt(paths, 1)));
        configSyncServices.put(Hermes, new HermesSyncServiceImpl(pathAt(paths, 2)));
        configSyncServices.put(OpenHuman, new OpenHumanSyncServiceImpl(pathAt(paths, 3)));
        all = OpenClaw | deerFlow | Hermes | OpenHuman;
    }

    private String pathAt(String[] paths, int index) {
        if (paths == null || paths.length <= index) {
            return "";
        }
        return paths[index];
    }

    public int selected(int [] selected) {
        int selects = 0;
        for (int i : selected) {
            selects |= i;
        }
        return selects & all;
    }


    private String getBasePath() {
        return "http://127.0.0.1:" + port + "/v1";
    }

    public void sync(int exportWhich, int loadWhich) {
        Set<Integer> keys = configSyncServices.keySet();
        for (Integer key : keys) {
            IConfigSyncService service = configSyncServices.get(key);
            if(!service.check()) {
                log.warn("{} is not installed, skipping configuration synchronization", service.name());
                continue;
            }
            if ((loadWhich & key) == 0) {
                log.info("Skipped: {} to LinkMind configuration synchronization", service.name());
            } else {
                try {
                    service.load(getBasePath());
                } catch (Exception e) {
                    log.error("Failed to load {} configuration", service.name(), e);
                }
            }
            if ((exportWhich & key) == 0) {
                log.info("Skipped: LinkMind to {} configuration synchronization", service.name());
            } else {
                try {
                    service.export(getBasePath());
                } catch (Exception e) {
                    log.error("Failed to export {} configuration", service.name(), e);
                }
            }
        }
    }

}
