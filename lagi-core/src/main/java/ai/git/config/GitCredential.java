package ai.git.config;

public class GitCredential {
    private String username;
    private String password;
    private String lfsServerUrl;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getLfsServerUrl() {
        return lfsServerUrl;
    }

    public void setLfsServerUrl(String lfsServerUrl) {
        this.lfsServerUrl = lfsServerUrl;
    }
}