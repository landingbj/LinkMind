package ai.git.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.InputStreamReader;

public class GitConfigLoader {
    private final String configPath;

    public GitConfigLoader(String configPath) {
        this.configPath = configPath;
    }

    public GitCredential getCredential(String repoPath) {
        GitAuthConfig config = loadConfig();
        if (config == null) return null;
        if (config.getGit().getCredentials().getRepositories() != null && config.getGit().getCredentials().getRepositories().containsKey(repoPath)) {
            return config.getGit().getCredentials().getRepositories().get(repoPath);
        }
        return config.getGit().getCredentials().getDefault();
    }

    private GitAuthConfig loadConfig() {
        try {
            // 从 resources 目录读取配置文件
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(configPath);
            if (inputStream != null) {
                Yaml yaml = new Yaml();
                return yaml.loadAs(new InputStreamReader(inputStream), GitAuthConfig.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}