package ai.git.config;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;

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
            Yaml yaml = new Yaml();
            File configFile = new File(configPath);
            if (configFile.exists()) {
                return yaml.loadAs(new FileReader(configFile), GitAuthConfig.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}