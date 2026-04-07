package ai.git.config;

public class GitConfig {
    private CredentialsConfig credentials;
    private SshConfig ssh;

    public CredentialsConfig getCredentials() {
        return credentials;
    }

    public void setCredentials(CredentialsConfig credentials) {
        this.credentials = credentials;
    }

    public SshConfig getSsh() {
        return ssh;
    }

    public void setSsh(SshConfig ssh) {
        this.ssh = ssh;
    }
}