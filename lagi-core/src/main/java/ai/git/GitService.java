package ai.git;

import org.eclipse.jgit.api.Status;

import java.util.List;
import java.util.Map;

public interface GitService {
    void init(String repoPath);
    void clone(String repoUrl, String targetPath);
    void add(String repoPath, List<String> files);
    void commit(String repoPath, String message);
    void push(String repoPath, String remote, String branch);
    boolean hasUnpushedCommits(String repoPath, String remote, String branch);
    void pull(String repoPath, String remote, String branch);
    List<String> getBranches(String repoPath);
    void checkout(String repoPath, String branch);
    List<Map<String, Object>> getRepositories(String basePath);
    List<Map<String, Object>> getFiles(String repoPath, String dirPath);
    List<Map<String, Object>> getDirectories(String repoPath, String dirPath);
    List<Map<String, Object>> getFileHistory(String repoPath, String filePath);
    Map<String, Object> getFileDiff(String repoPath, String filePath, String commitHash);
    List<Map<String, Object>> getCommitLog(String repoPath, int limit);
    void createBranch(String repoPath, String branchName);
    void deleteBranch(String repoPath, String branchName);
    void mergeBranch(String repoPath, String branchName);
    void createTag(String repoPath, String tagName, String message);
    List<String> getTags(String repoPath);
    void deleteTag(String repoPath, String tagName);
    void addRemote(String repoPath, String name, String url);
    void deleteRemote(String repoPath, String name);
    List<Map<String, Object>> getRemotes(String repoPath);
    Status getStatus(String repoPath);
    Map<String, Object> getStatus(String repoPath,Object obj);
    void reset(String repoPath, String commitHash, String mode);
    void checkoutFile(String repoPath, String filePath);
}
