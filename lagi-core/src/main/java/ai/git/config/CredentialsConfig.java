package ai.git.config;

import java.util.Map;

public class CredentialsConfig {
    private GitCredential defaultCredential;
    private Map<String, GitCredential> repositories;

    public GitCredential getDefault() {
        return defaultCredential;
    }

    public void setDefault(GitCredential defaultCredential) {
        this.defaultCredential = defaultCredential;
    }

    public Map<String, GitCredential> getRepositories() {
        return repositories;
    }

    public void setRepositories(Map<String, GitCredential> repositories) {
        this.repositories = repositories;
    }
}