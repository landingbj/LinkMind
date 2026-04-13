package ai.git.impl;

import ai.git.GitLFSService;
import org.eclipse.jgit.api.Git;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GitLFSServiceImpl implements GitLFSService {

    private File getRepoDir(String repoPath) {
        File repoDir = new File(repoPath);
        if (!repoDir.exists() || !new File(repoDir, ".git").exists()) {
            throw new IllegalArgumentException("Invalid Git repository: " + repoPath);
        }
        return repoDir;
    }

    @Override
    public void init(String repoPath, String lfsServerUrl) {
        try (Git git = Git.open(getRepoDir(repoPath))) {
            // 配置LFS服务器URL
            git.getRepository().getConfig().setString("lfs", null, "url", lfsServerUrl);
            git.getRepository().getConfig().save();
        } catch (Exception e) {
            Throwable cause = e.getCause();
            throw new RuntimeException(cause != null ? cause.getMessage() : e.getMessage(), e);
        }
    }

    @Override
    public void track(String repoPath, String pattern) {
        try (Git git = Git.open(getRepoDir(repoPath))) {
            // 这里简化实现，实际需要更新.gitattributes文件
            File gitAttributes = new File(repoPath, ".gitattributes");
            // 向.gitattributes文件添加跟踪规则
            // 例如：*.zip filter=lfs diff=lfs merge=lfs -text
        } catch (Exception e) {
            Throwable cause = e.getCause();
            throw new RuntimeException(cause != null ? cause.getMessage() : e.getMessage(), e);
        }
    }

    @Override
    public void push(String repoPath) {
        try (Git git = Git.open(getRepoDir(repoPath))) {
            // 执行git lfs push
            // 这里简化实现，实际需要调用LFS相关命令
        } catch (Exception e) {
            Throwable cause = e.getCause();
            throw new RuntimeException(cause != null ? cause.getMessage() : e.getMessage(), e);
        }
    }

    @Override
    public void pull(String repoPath) {
        try (Git git = Git.open(getRepoDir(repoPath))) {
            // 执行git lfs pull
            // 这里简化实现，实际需要调用LFS相关命令
        } catch (Exception e) {
            Throwable cause = e.getCause();
            throw new RuntimeException(cause != null ? cause.getMessage() : e.getMessage(), e);
        }
    }

    @Override
    public boolean isLFSPointer(String filePath) {
        try {
            File file = new File(filePath);
            if (file.length() < 100) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String firstLine = reader.readLine();
                reader.close();
                return firstLine != null && firstLine.contains("git-lfs");
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public List<Map<String, Object>> getFileVersions(String repoPath, String filePath) {
        List<Map<String, Object>> versions = new ArrayList<>();
        try (Git git = Git.open(getRepoDir(repoPath))) {
            git.log().addPath(filePath).call().forEach(commit -> {
                Map<String, Object> version = new HashMap<>();
                version.put("commitHash", commit.getName());
                version.put("author", commit.getAuthorIdent().getName());
                version.put("date", commit.getAuthorIdent().getWhen().toString());
                version.put("message", commit.getFullMessage());
                versions.add(version);
            });
        } catch (Exception e) {
            Throwable cause = e.getCause();
            throw new RuntimeException(cause != null ? cause.getMessage() : e.getMessage(), e);
        }
        return versions;
    }

    @Override
    public void rollbackToVersion(String repoPath, String filePath, String commitHash) {
        try (Git git = Git.open(getRepoDir(repoPath))) {
            // 切换到指定提交
            git.checkout().setStartPoint(commitHash).addPath(filePath).call();
            // 执行git lfs pull
        } catch (Exception e) {
            Throwable cause = e.getCause();
            throw new RuntimeException(cause != null ? cause.getMessage() : e.getMessage(), e);
        }
    }

    @Override
    public void rollbackRepository(String repoPath, String targetVersion) {
        try (Git git = Git.open(getRepoDir(repoPath))) {
            // 切换到指定版本
            git.checkout().setName(targetVersion).call();
            // 执行git lfs pull
        } catch (Exception e) {
            Throwable cause = e.getCause();
            throw new RuntimeException(cause != null ? cause.getMessage() : e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> getStatus(String repoPath) {
        Map<String, Object> status = new HashMap<>();
        status.put("isLfsEnabled", true);
        status.put("trackedPatterns", List.of("*.zip", "*.tar.gz"));
        return status;
    }
}
