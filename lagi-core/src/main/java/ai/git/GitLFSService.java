package ai.git;

import java.util.List;
import java.util.Map;

public interface GitLFSService {
    void init(String repoPath, String lfsServerUrl);
    void track(String repoPath, String pattern);
    void push(String repoPath);
    void pull(String repoPath);
    boolean isLFSPointer(String filePath);
    List<Map<String, Object>> getFileVersions(String repoPath, String filePath);
    void rollbackToVersion(String repoPath, String filePath, String commitHash);
    void rollbackRepository(String repoPath, String targetVersion);
    Map<String, Object> getStatus(String repoPath);
}