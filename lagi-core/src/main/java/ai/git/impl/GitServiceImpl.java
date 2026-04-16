package ai.git.impl;

import ai.git.GitService;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.lib.Repository;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.*;

public class GitServiceImpl implements GitService {

    private File getRepoDir(String repoPath) {
        File repoDir = new File(repoPath);
        if (!repoDir.exists() || !new File(repoDir, ".git").exists()) {
            throw new RuntimeException("Invalid Git repository: " + repoPath);
        }
        return repoDir;
    }

    // 执行外部命令的工具方法
    private void executeCommand(String workingDir, String... command) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDir != null) processBuilder.directory(new File(workingDir));
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        // 读取命令输出
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 静默处理输出
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code: " + exitCode + ", command: " + String.join(" ", command));
        }
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
            try {
                // 优先使用 命令行 实现
                executeCommand(null, "git", "clone", repoUrl, targetPath);
                // 检查并配置 LFS
                configureLFSIfNeeded(targetPath, repoUrl);
                return;
            } catch (Exception e) {
                //使用 JGit 实现
                System.out.println("使用命令行实现时报错:" + e);
            }
            Git.cloneRepository().setURI(repoUrl).setDirectory(new File(targetPath)).call();
            // 检查并配置 LFS
            configureLFSIfNeeded(targetPath, repoUrl);
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
    }

    // 检查并配置 LFS
    private void configureLFSIfNeeded(String repoPath, String repoUrl) throws Exception {
        try (Git git = Git.open(new File(repoPath))) {
            Repository repo = git.getRepository();
            org.eclipse.jgit.lib.StoredConfig config = repo.getConfig();

            // 检查 .gitattributes 文件
            boolean hasLfsAttributes = false;
            File gitAttributesFile = new File(repo.getWorkTree(), ".gitattributes");
            if (gitAttributesFile.exists()) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(gitAttributesFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("filter=lfs")) {
                            hasLfsAttributes = true;
                            break;
                        }
                    }
                }
            }

            // 检查 lfs.url 是否已配置
            String lfsUrl = config.getString("lfs", null, "url");

            // 如果启用了 LFS 但未配置 lfs.url，自动设置
            if (hasLfsAttributes && (lfsUrl == null || lfsUrl.isEmpty())) {
                // 从 repoUrl 推导 LFS URL（默认对接 GitLab）
                String derivedLfsUrl = deriveLfsUrl(repoUrl);
                if (derivedLfsUrl != null) {
                    config.setString("lfs", null, "url", derivedLfsUrl);
                    config.save();
                }
            }
        }
    }

    // 从 Git URL 推导 LFS URL
    private String deriveLfsUrl(String repoUrl) {
        // 处理 HTTP/HTTPS URL
        if (repoUrl.startsWith("http://") || repoUrl.startsWith("https://")) {
            // GitLab 格式：http(s)://gitlab.com/{namespace}/{project}.git
            // LFS URL：http(s)://gitlab.com/{namespace}/{project}.git/info/lfs
            return repoUrl.endsWith(".git") ? repoUrl + "/info/lfs" : repoUrl + ".git/info/lfs";
        }
        // 处理 SSH URL
        else if (repoUrl.startsWith("git@")) {
            // GitLab 格式：git@gitlab.com:{namespace}/{project}.git
            // 转换为 HTTPS 格式
            String httpsUrl = repoUrl.replace("git@", "https://").replace(":", "/");
            return httpsUrl.endsWith(".git") ? httpsUrl + "/info/lfs" : httpsUrl + ".git/info/lfs";
        }
        return null;
    }

    @Override
    public void add(String repoPath, List<String> files) {
        try {
            File repoDir = getRepoDir(repoPath);
            for (String file : files) {
                String normalizedFile = file.replace("\\", "/").replace("//", "/");
                String normalizedRepoPath = repoPath.replace("\\", "/").replace("//", "/");

                String relativePath;
                if (normalizedFile.startsWith(normalizedRepoPath)) {
                    relativePath = normalizedFile.substring(normalizedRepoPath.length());
                    if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
                } else {
                    relativePath = new File(file).getName();
                }

                executeCommand(repoDir.getAbsolutePath(), "git", "add", relativePath.replace("/", File.separator));
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
    }

    @Override
    public void commit(String repoPath, String message) {
        try {
            executeCommand(getRepoDir(repoPath).getAbsolutePath(), "git", "commit", "-m", message, "-a");
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
        //  try (Git git = Git.open(getRepoDir(repoPath))) {
        //            // 确保提交所有更改，包括新添加的文件
        //            git.commit().setMessage(message).setAll(true).call();
        //        } catch (Exception e) {
        //            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        //        }
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
        try {
            executeCommand(getRepoDir(repoPath).getAbsolutePath(), "git", "push", remote, branch);
        } catch (Exception e) {
            System.out.println(e);
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
        //        try (Git git = Git.open(getRepoDir(repoPath))) {
        //            CredentialsProvider credentialsProvider = getCredentialsProvider(repoPath);
        //            PushCommand pushCommand = git.push().setRemote(remote).setRefSpecs(new RefSpec("refs/heads/" + branch + ":refs/heads/" + branch));
        //            if (credentialsProvider != null) pushCommand.setCredentialsProvider(credentialsProvider);
        //            pushCommand.call();
        //        } catch (Exception e) {
        //            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        //        }
    }

    @Override
    public boolean hasUnpushedCommits(String repoPath, String remote, String branch) {
        try {
            String repoDir = getRepoDir(repoPath).getAbsolutePath();
            ProcessBuilder pb = new ProcessBuilder("git", "log", remote + "/" + branch + ".." + branch, "--oneline");
            pb.directory(new File(repoDir));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.readLine() != null;
            } finally {
                process.waitFor();
            }
        } catch (Exception e) {
            return false;
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

            // 检查仓库状态，如果处于合并状态，先尝试完成合并
            if (repo.getRepositoryState().equals(org.eclipse.jgit.lib.RepositoryState.MERGING_RESOLVED) || 
                repo.getRepositoryState().equals(org.eclipse.jgit.lib.RepositoryState.MERGING)) {
                // 仓库处于合并状态，尝试完成合并
                try {
                    git.commit().setMessage("Merge remote-tracking branch '" + remote + "/" + branch + "'").call();
                } catch (Exception e) {
                    // 如果提交失败，尝试中止合并
                    git.reset().setMode(ResetType.HARD).call();
                }
            }

            CredentialsProvider credentialsProvider = getCredentialsProvider(repoPath);
            PullCommand pullCommand = git.pull()
                    .setRemote(remote)
                    .setStrategy(org.eclipse.jgit.merge.MergeStrategy.RECURSIVE);
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
                // 读取 .gitattributes 文件，获取 LFS 跟踪的文件模式
                List<String> lfsPatterns = new ArrayList<>();
                try {
                    File gitAttributesFile = new File(repoPath, ".gitattributes");
                    if (gitAttributesFile.exists()) {
                        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(gitAttributesFile))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                line = line.trim();
                                if (!line.isEmpty() && !line.startsWith("#") && line.contains("filter=lfs")) {
                                    String[] parts = line.split("\\s+");
                                    if (parts.length > 0) {
                                        lfsPatterns.add(parts[0]);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // 忽略解析错误
                }

                for (File file : fileList) {
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("name", file.getName());
                    String relativePath = file.getAbsolutePath().substring(repoPath.length()).replaceFirst("^/+", "");
                    fileInfo.put("path", relativePath);
                    fileInfo.put("size", file.length());
                    fileInfo.put("isDirectory", file.isDirectory());

                    // 判断文件是否为 LFS 文件
                    boolean isLfsEnabled = false;
                    if (!file.isDirectory() && !lfsPatterns.isEmpty()) {
                        String fileName = file.getName();
                        for (String pattern : lfsPatterns) {
                            if (fileName.matches(pattern.replace(".", "\\.").replace("*", ".*"))) {
                                isLfsEnabled = true;
                                break;
                            }
                        }
                    }
                    fileInfo.put("isLfsEnabled", isLfsEnabled);

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
    public Status getStatus(String repoPath) {
        try {
            try (Git git = Git.open(getRepoDir(repoPath))) {
                Status status = git.status().call();
                return status;
            } catch (Exception e) {
                throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
            }
        }catch (Exception e){
            throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        }
    }
    @Override
    public Map<String, Object> getStatus(String repoPath, Object filePath) {
        Map<String, Object> status = new HashMap<>();
        try (Git git = Git.open(getRepoDir(repoPath))) {
            Status gitStatus = git.status().call();
            status.put("modified", gitStatus.getModified());
            status.put("untracked", gitStatus.getUntracked());
            status.put("staged", gitStatus.getAdded());
            status.put("removed", gitStatus.getRemoved());
            status.put("conflicting", gitStatus.getConflicting());
            status.put("ignored", gitStatus.getIgnoredNotInIndex());
            status.put("uncommittedChanges", gitStatus.hasUncommittedChanges());
            status.put("clean", gitStatus.isClean());
            status.put("unpushedCommits", gitStatus.getUncommittedChanges());
            status.put("unpushedCommitsCount", gitStatus.getUncommittedChanges().size());
            status.put("unpushedCommitsHash", gitStatus.getUncommittedChanges());
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
