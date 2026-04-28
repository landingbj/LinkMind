package ai.starter;

import ai.config.ConfigUtil;
import ai.starter.config.ConfigSyncService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;
import java.nio.file.Path;

@Slf4j
public class Application {
    public static void main(String[] args) throws Exception {
        File jarFile = InstallerUtil.getJarFile();
        boolean devMode = jarFile == null;

        InstallerUtil.applyConfigAndDataDir(args, jarFile);
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        int port = InstallerUtil.resolvePort(args);
        String host = InstallerUtil.resolveHost(args);
        boolean enableSync = InstallerUtil.resolveEnableSync(args);

        if (!devMode && enableSync) {
//        if (enableSync) {
            String deerFlowPath = InstallerUtil.parseArg(args, InstallerUtil.DEER_FLOW_PATH);
            ConfigSyncService configSyncService = new ConfigSyncService(port, new String[] {"", deerFlowPath, ""});
            configSyncService.sync(configSyncService.getAll(), configSyncService.getAll());
        }

        Path webappDir = TomcatUtil.resolveWebappDir(Application.class);
        if (host.equals("0.0.0.0")) {
            ConfigUtil.setAppHost("localhost");
        } else {
            ConfigUtil.setAppHost(host);
        }
        ConfigUtil.setAppPort(port);
        TomcatUtil.startAndAwait(host, port, webappDir, devMode);
    }
}
