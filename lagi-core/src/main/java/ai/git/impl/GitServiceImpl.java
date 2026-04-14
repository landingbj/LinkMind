package ai.git.impl;

import ai.git.GitService;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GitServiceImpl implements GitService {

    private File getRepoDir(String repoPath) {
        File repoDir = new File(repoPath);
        if (!repoDir.exists() || !new File(repoDir, ".git").exists()) {
            throw new RuntimeException("Invalid Git repository: " + repoPath);
        }
        return repoDir;
    }

    @Override
    public void init(String repoPath) {
        try {
            Git.init().setDirectory(new File(repoPath)).call();
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
    }

    @Override
    public void clone(String repoUrl, String targetPath) {
        try {
            Git.cloneRepository().setURI(repoUrl).setDirectory(new File(targetPath)).call();
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
    }

    @Override
    public void add(String repoPath, List<String> files) {
        try (Git git = Git.open(getRepoDir(repoPath))) {
            // 确保 LFS 已初始化
            Repository repo = git.getRepository();
            org.eclipse.jgit.lib.StoredConfig config = repo.getConfig();
            
            // 配置使用 JGit 内置 LFS 实现
            config.setBoolean("filter", "lfs", "required", true);
            
            // 配置 LFS 服务器 URL（如果未配置）
            String lfsUrl = config.getString("lfs", null, "url");
            if (lfsUrl == null || lfsUrl.isEmpty()) {
                // 从远程仓库 URL 构建 LFS URL
                String remoteUrl = config.getString("remote", "origin", "url");
                if (remoteUrl != null) {
                    lfsUrl = remoteUrl.replace(".git", "/info/lfs");
                    config.setString("lfs", null, "url", lfsUrl);
                }
            }
            
            config.save();
            
            // 统一仓库路径格式
            String normalizedRepoPath = repoPath.replace("\\", "/").replace("//", "/");
            
            files.forEach(file -> {
                try {
                    // 统一文件路径格式
                    String normalizedFile = file.replace("\\", "/").replace("//", "/");
                    
                    // 计算相对路径
                    String relativePath;
                    if (normalizedFile.startsWith(normalizedRepoPath)) {
                        relativePath = normalizedFile.substring(normalizedRepoPath.length());
                        // 移除开头的斜杠
                        if (relativePath.startsWith("/")) {
                            relativePath = relativePath.substring(1);
                        }
                    } else {
                        // 如果路径不包含仓库路径，尝试从文件名开始
                        File fileObj = new File(file);
                        relativePath = fileObj.getName();
                    }
                    
                    // 确保使用正斜杠
                    relativePath = relativePath.replace("\\", "/");
                    
                    // 执行添加操作
                    git.add().addFilepattern(relativePath).call();
                } catch (Exception e) {
                    throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
    }

    @Override
    public void commit(String repoPath, String message) {
        try (Git git = Git.open(getRepoDir(repoPath))) {
            // 确保提交所有更改，包括新添加的文件
            git.commit().setMessage(message).setAll(true).call();
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
    }

    private CredentialsProvider getCredentialsProvider(String repoPath) {
        try {
            ai.git.config.GitConfigLoader configLoader = new ai.git.config.GitConfigLoader("git-lfs.yml");
            ai.git.config.GitCredential credential = configLoader.getCredential(repoPath);
            if (credential != null && credential.getUsername() != null && credential.getPassword() != null) {
                return new UsernamePasswordCredentialsProvider(credential.getUsername(), credential.getPassword());
            }
        } catch (Exception e) {
            // 忽略配置加载错误，使用无认证方式
        }
        return null;
    }

    @Override
    public void push(String repoPath, String remote, String branch) {
        try (Git git = Git.open(getRepoDir(repoPath))) {
            CredentialsProvider credentialsProvider = getCredentialsProvider(repoPath);
            PushCommand pushCommand = git.push().setRemote(remote).setRefSpecs(new RefSpec("refs/heads/" + branch + ":refs/heads/" + branch));
            if (credentialsProvider != null) pushCommand.setCredentialsProvider(credentialsProvider);
            pushCommand.call();
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
    }

    @Override
    public void pull(String repoPath, String remote, String branch) {
        try (Git git = Git.open(getRepoDir(repoPath))) {
            // 确保 LFS 已初始化并配置为使用内置实现
            Repository repo = git.getRepository();
            org.eclipse.jgit.lib.StoredConfig config = repo.getConfig();
            
            // 配置使用 JGit 内置 LFS 实现
            config.setBoolean("filter", "lfs", "required", true);
            config.save();
            
            CredentialsProvider credentialsProvider = getCredentialsProvider(repoPath);
            PullCommand pullCommand = git.pull().setRemote(remote);
            if (credentialsProvider != null) pullCommand.setCredentialsProvider(credentialsProvider);
            pullCommand.call();
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
    }

    @Override
    public List<String> getBranches(String repoPath) {
        List<String> branches = new ArrayList<>();
        try (Git git = Git.open(getRepoDir(repoPath))) {
            git.branchList().call().forEach(ref -> branches.add(ref.getName().substring(11)));
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
        return branches;
    }

    @Override
    public void checkout(String repoPath, String branch) {
        try (Git git = Git.open(getRepoDir(repoPath))) {
            git.checkout().setName(branch).call();
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> getRepositories(String basePath) {
        List<Map<String, Object>> repos = new ArrayList<>();
        File baseDir = new File(basePath);
        if (baseDir.exists() && baseDir.isDirectory()) {
            File[] dirs = baseDir.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File dir : dirs) {
                    if (new File(dir, ".git").exists()) {
                        Map<String, Object> repoInfo = new HashMap<>();
                        repoInfo.put("name", dir.getName());
                        repoInfo.put("path", dir.getAbsolutePath());
                        repos.add(repoInfo);
                    }
                }
            }
        }
        return repos;
    }

    @Override
    public List<Map<String, Object>> getFiles(String repoPath, String dirPath) {
        List<Map<String, Object>> files = new ArrayList<>();
        File dir = new File(repoPath, dirPath);
        if (dir.exists() && dir.isDirectory()) {
            File[] fileList = dir.listFiles();
            if (fileList != null) {
                for (File file : fileList) {
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("name", file.getName());
                    fileInfo.put("path", file.getAbsolutePath().substring(repoPath.length()));
                    fileInfo.put("size", file.length());
                    fileInfo.put("isDirectory", file.isDirectory());
                    files.add(fileInfo);
                }
            }
        }
        return files;
    }

    @Override
    public List<Map<String, Object>> getDirectories(String repoPath, String dirPath) {
        List<Map<String, Object>> dirs = new ArrayList<>();
        File dir = new File(repoPath, dirPath);
        if (dir.exists() && dir.isDirectory()) {
            File[] fileList = dir.listFiles();
            if (fileList != null) {
                for (File file : fileList) {
                    if (file.isDirectory()) {
                        Map<String, Object> dirInfo = new HashMap<>();
                        dirInfo.put("name", file.getName());
                        dirInfo.put("path", file.getAbsolutePath().substring(repoPath.length()));
                        dirInfo.put("isDirectory", true);
                        dirs.add(dirInfo);
                    }
                }
            }
        }
        return dirs;
    }

    @Override
    public List<Map<String, Object>> getFileHistory(String repoPath, String filePath) {
        List<Map<String, Object>> history = new ArrayList<>();
        try (Git git = Git.open(getRepoDir(repoPath))) {
            git.log().addPath(filePath).call().forEach(commit -> {
                Map<String, Object> commitInfo = new HashMap<>();
                commitInfo.put("commitHash", commit.getName());
                commitInfo.put("author", commit.getAuthorIdent().getName());
                commitInfo.put("date", commit.getAuthorIdent().getWhen().toString());
                commitInfo.put("message", commit.getFullMessage());
                commitInfo.put("changes", 0);
                history.add(commitInfo);
            });
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
        return history;
    }

    @Override
    public Map<String, Object> getFileDiff(String repoPath, String filePath, String commitHash) {
        Map<String, Object> result = new HashMap<>();
        result.put("diff", "- old line\n+ new line");
        return result;
    }

    @Override
    public List<Map<String, Object>> getCommitLog(String repoPath, int limit) {
        List<Map<String, Object>> log = new ArrayList<>();
        try (Git git = Git.open(getRepoDir(repoPath))) {
            git.log().setMaxCount(limit).call().forEach(commit -> {
                Map<String, Object> commitInfo = new HashMap<>();
                commitInfo.put("commitHash", commit.getName());
                commitInfo.put("author", commit.getAuthorIdent().getName());
                commitInfo.put("date", commit.getAuthorIdent().getWhen().toString());
                commitInfo.put("message", commit.getFullMessage());
                log.add(commitInfo);
            });
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
        return log;
    }

    @Override
    public void createBranch(String repoPath, String branchName) {
        try (Git git = Git.open(getRepoDir(repoPath))) {
            git.branchCreate().setName(branchName).call();
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
    }

    @Override
    public void deleteBranch(String repoPath, String branchName) {
        try (Git git = Git.open(getRepoDir(repoPath))) {
            git.branchDelete().setBranchNames(branchName).setForce(true).call();
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
    }

    @Override
    public void mergeBranch(String repoPath, String branchName) {
        try (Git git = Git.open(getRepoDir(repoPath))) {
            git.merge().include(git.getRepository().resolve(branchName)).call();
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
    }

    @Override
    public void createTag(String repoPath, String tagName, String message) {
        try (Git git = Git.open(getRepoDir(repoPath))) {
            git.tag().setName(tagName).setMessage(message).call();
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
    }

    @Override
    public List<String> getTags(String repoPath) {
        List<String> tags = new ArrayList<>();
        try (Git git = Git.open(getRepoDir(repoPath))) {
            git.tagList().call().forEach(ref -> tags.add(ref.getName().substring(10)));
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
        return tags;
    }

    @Override
    public void deleteTag(String repoPath, String tagName) {
        try (Git git = Git.open(getRepoDir(repoPath))) {
            git.tagDelete().setTags(tagName).call();
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
    }

    @Override
    public void addRemote(String repoPath, String name, String url) {
        try (Git git = Git.open(getRepoDir(repoPath))) {
            git.remoteAdd().setName(name).setUri(new URIish(url)).call();
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
    }

    @Override
    public void deleteRemote(String repoPath, String name) {
        try (Git git = Git.open(getRepoDir(repoPath))) {
            RemoteRemoveCommand removeCmd = git.remoteRemove();
            removeCmd.setName(name);
            removeCmd.call();
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> getRemotes(String repoPath) {
        List<Map<String, Object>> remotes = new ArrayList<>();
        try (Git git = Git.open(getRepoDir(repoPath))) {
            git.remoteList().call().forEach(remote -> {
                Map<String, Object> remoteInfo = new HashMap<>();
                remoteInfo.put("name", remote.getName());
                remoteInfo.put("url", remote.getURIs().iterator().next().toString());
                remotes.add(remoteInfo);
            });
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
        return remotes;
    }

    @Override
    public Map<String, Object> getStatus(String repoPath) {
        Map<String, Object> status = new HashMap<>();
        try (Git git = Git.open(getRepoDir(repoPath))) {
            org.eclipse.jgit.api.Status gitStatus = git.status().call();
            status.put("modified", gitStatus.getModified());
            status.put("untracked", gitStatus.getUntracked());
            status.put("staged", gitStatus.getAdded());
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
        return status;
    }

    @Override
    public void reset(String repoPath, String commitHash, String mode) {
        try (Git git = Git.open(getRepoDir(repoPath))) {
            ResetCommand resetCommand = git.reset();
            resetCommand.setMode("soft".equals(mode) ? ResetType.SOFT : "hard".equals(mode) ? ResetType.HARD : ResetType.MIXED);
            resetCommand.setRef(commitHash).call();
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
    }

    @Override
    public void checkoutFile(String repoPath, String filePath) {
        try (Git git = Git.open(getRepoDir(repoPath))) {
            git.checkout().addPath(filePath).call();
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
    }
}
