package ai.starter;

import ai.utils.YmlLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstallerUtilTest {

    @TempDir
    Path tempDir;

    private final String previousConfigProperty = System.getProperty(InstallerUtil.CONFIG_FILE_PROPERTY);

    @AfterEach
    void restoreConfigProperty() {
        if (previousConfigProperty == null) {
            System.clearProperty(InstallerUtil.CONFIG_FILE_PROPERTY);
        } else {
            System.setProperty(InstallerUtil.CONFIG_FILE_PROPERTY, previousConfigProperty);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void agentMatePreservesExistingSkillsSwitch() throws Exception {
        Path config = writeConfig("skills:\n  enable: true\n  roots:\n    - classpath:skills\n");
        System.setProperty(InstallerUtil.CONFIG_FILE_PROPERTY, config.toString());

        invokeApplyRuntimeSkillsConfig(new String[] {"--runtime-choice=mate"});

        Map<String, Object> root = YmlLoader.loadYamlAsMap(config.toString());
        Map<String, Object> skills = (Map<String, Object>) root.get("skills");
        assertEquals(Boolean.TRUE, skills.get("enable"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void agentServerEnablesSkills() throws Exception {
        Path config = writeConfig("skills:\n  enable: false\n  roots:\n    - classpath:skills\n");
        System.setProperty(InstallerUtil.CONFIG_FILE_PROPERTY, config.toString());

        invokeApplyRuntimeSkillsConfig(new String[] {"--runtime-choice=server"});

        Map<String, Object> root = YmlLoader.loadYamlAsMap(config.toString());
        Map<String, Object> skills = (Map<String, Object>) root.get("skills");
        assertEquals(Boolean.TRUE, skills.get("enable"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void agentServerCanInstallMedusaAcceleratorConfig() throws Exception {
        Path config = writeConfig("stores:\n  medusa:\n    enable: false\n    algorithm: hash,graph,llm\n    aheads: 1\n");
        System.setProperty(InstallerUtil.CONFIG_FILE_PROPERTY, config.toString());

        invokeApplyRuntimeSkillsConfig(new String[] {"--runtime-choice=server", "--install-medusa=true"});

        Map<String, Object> root = YmlLoader.loadYamlAsMap(config.toString());
        Map<String, Object> stores = (Map<String, Object>) root.get("stores");
        Map<String, Object> medusa = (Map<String, Object>) stores.get("medusa");
        assertEquals(Boolean.TRUE, medusa.get("enable"));
        assertEquals("hash", medusa.get("algorithm"));
        assertEquals(5, medusa.get("core_pool_size"));
        assertEquals(30, medusa.get("maximum_pool_size"));
        assertEquals(4, medusa.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void agentMateKeepsMedusaConfigEvenWithInstallMedusaArg() throws Exception {
        Path config = writeConfig("stores:\n  medusa:\n    enable: false\n    algorithm: hash,graph,llm\n    aheads: 1\n");
        System.setProperty(InstallerUtil.CONFIG_FILE_PROPERTY, config.toString());

        invokeApplyRuntimeSkillsConfig(new String[] {"--runtime-choice=mate", "--install-medusa=true"});

        Map<String, Object> root = YmlLoader.loadYamlAsMap(config.toString());
        Map<String, Object> stores = (Map<String, Object>) root.get("stores");
        Map<String, Object> medusa = (Map<String, Object>) stores.get("medusa");
        assertEquals(Boolean.FALSE, medusa.get("enable"));
        assertEquals("hash,graph,llm", medusa.get("algorithm"));
        assertEquals(1, medusa.get("aheads"));
        assertEquals(3, medusa.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void agentServerDisablesMedusaWhenAcceleratorIsNotInstalled() throws Exception {
        Path config = writeConfig("stores:\n  medusa:\n    enable: true\n    algorithm: hash\n    core_pool_size: 5\n    maximum_pool_size: 30\n");
        System.setProperty(InstallerUtil.CONFIG_FILE_PROPERTY, config.toString());

        invokeApplyRuntimeSkillsConfig(new String[] {"--runtime-choice=server", "--install-medusa=false"});

        Map<String, Object> root = YmlLoader.loadYamlAsMap(config.toString());
        Map<String, Object> stores = (Map<String, Object>) root.get("stores");
        Map<String, Object> medusa = (Map<String, Object>) stores.get("medusa");
        assertEquals(Boolean.FALSE, medusa.get("enable"));
        assertEquals("hash", medusa.get("algorithm"));
        assertEquals(5, medusa.get("core_pool_size"));
        assertEquals(30, medusa.get("maximum_pool_size"));
    }

    private Path writeConfig(String content) throws Exception {
        Path config = tempDir.resolve("lagi.yml");
        Files.write(config, content.getBytes(StandardCharsets.UTF_8));
        return config;
    }

    private void invokeApplyRuntimeSkillsConfig(String[] args) throws Exception {
        Method method = InstallerUtil.class.getDeclaredMethod("applyRuntimeSkillsConfig", String[].class);
        method.setAccessible(true);
        method.invoke(null, (Object) args);
    }
}
