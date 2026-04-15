package ai.git.impl;

import ai.git.GitLFSService;
import ai.git.config.GitConfigLoader;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GitLFSServiceImpl implements GitLFSService {

    private static final Logger logger = LoggerFactory.getLogger(GitLFSServiceImpl.class);
    
    private final GitConfigLoader configLoader;

    public GitLFSServiceImpl() {
        // 从 resources 目录加载配置文件
        String configPath = "git-lfs.yml";
        this.configLoader = new GitConfigLoader(configPath);
    }

    private File getRepoDir(String repoPath) {
        File repoDir = new File(repoPath);
        if (!repoDir.exists() || !new File(repoDir, ".git").exists()) {
            throw new IllegalArgumentException("Invalid Git repository: " + repoPath);
        }
        return repoDir;
    }
    
    // 从文件路径向上查找仓库路径
    private String findRepoPath(String filePath) {
        var file = new File(filePath);
        var currentDir = file.getParentFile();
        while (currentDir != null) {
            if (new File(currentDir, ".git").exists()) {
                return currentDir.getAbsolutePath();
            }
            currentDir = currentDir.getParentFile();
        }
        throw new RuntimeException("Not a Git repository: " + filePath);
    }
    
    // 确保 LFS 被正确初始化
    private void ensureLFSInitialized(Repository repo) throws Exception {
        StoredConfig config = repo.getConfig();
        
        // 检查 LFS 是否已启用
        boolean lfsEnabled = config.getBoolean("filter", "lfs", "required", false);
        
        if (!lfsEnabled) {
            // 启用 LFS
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
            
            // 保存配置
            config.save();
        }
    }

    @Override
    public void init(String repoPath, String lfsServerUrl) {
        logger.info("Initializing LFS for repository: {}", repoPath);
        try (Git git = Git.open(getRepoDir(repoPath))) {
            // 配置 LFS 过滤器使用 JGit 内置实现
            Repository repo = git.getRepository();
            StoredConfig config = repo.getConfig();
            
            // 启用 LFS（JGit 会自动使用内置过滤器）
            config.setBoolean("filter", "lfs", "required", true);
            
            // 配置 LFS 服务器 URL
            config.setString("lfs", null, "url", lfsServerUrl);
            config.save();
            
            logger.info("LFS initialized successfully for repository: {}", repoPath);
        } catch (Exception e) {
            logger.error("Failed to initialize LFS for repository: {}", repoPath, e);
            throw new RuntimeException("Failed to initialize LFS", e);
        }
    }

    @Override
    public void track(String repoPath, String pattern) {
        logger.info("Tracking pattern: {} for repository: {}", pattern, repoPath);
        try (Git git = Git.open(getRepoDir(repoPath))) {
            // 读取 .gitattributes 文件
            File gitAttributes = new File(repoPath, ".gitattributes");
            List<String> lines = new ArrayList<>();

            if (gitAttributes.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(gitAttributes))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                }
            }

            // 添加 LFS 跟踪规则
            String newRule = pattern + " filter=lfs diff=lfs merge=lfs -text";
            boolean ruleExists = false;
            for (String line : lines) {
                if (line.trim().startsWith(pattern + " ")) {
                    ruleExists = true;
                    break;
                }
            }

            if (!ruleExists) {
                lines.add(newRule);
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(gitAttributes))) {
                    for (String line : lines) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
                logger.info("Pattern: {} tracked successfully for repository: {}", pattern, repoPath);
            } else {
                logger.info("Pattern: {} is already tracked for repository: {}", pattern, repoPath);
            }
        } catch (Exception e) {
            logger.error("Failed to track pattern: {} for repository: {}", pattern, repoPath, e);
            throw new RuntimeException("Failed to track pattern", e);
        }
    }

    @Override
    public void push(String repoPath) {
        logger.info("Pushing LFS objects for repository: {}", repoPath);
        try (Git git = Git.open(getRepoDir(repoPath))) {
            // 获取认证信息
            ai.git.config.GitCredential credential = configLoader.getCredential(repoPath);
            CredentialsProvider credentialsProvider = null;
            
            if (credential != null && credential.getUsername() != null && credential.getPassword() != null) {
                credentialsProvider = new UsernamePasswordCredentialsProvider(credential.getUsername(), credential.getPassword());
                logger.info("Using credentials for user: {}", credential.getUsername());
            }
            
            // JGit 6.10.1 会自动处理 LFS 对象的推送
            if (credentialsProvider != null) {
                git.push().setCredentialsProvider(credentialsProvider).call();
            } else {
                git.push().call();
            }
            
            logger.info("LFS objects pushed successfully for repository: {}", repoPath);
        } catch (Exception e) {
            logger.error("Failed to push LFS objects for repository: {}", repoPath, e);
            throw new RuntimeException("Failed to push LFS objects", e);
        }
    }

    @Override
    public void pull(String repoPath) {
        logger.info("Pulling LFS objects for repository: {}", repoPath);
        try (Git git = Git.open(getRepoDir(repoPath))) {
            // 确保 LFS 已初始化
            ensureLFSInitialized(git.getRepository());
            
            // 获取认证信息
            ai.git.config.GitCredential credential = configLoader.getCredential(repoPath);
            CredentialsProvider credentialsProvider = null;
            
            if (credential != null && credential.getUsername() != null && credential.getPassword() != null) {
                credentialsProvider = new UsernamePasswordCredentialsProvider(credential.getUsername(), credential.getPassword());
                logger.info("Using credentials for user: {}", credential.getUsername());
            }
            
            // 执行拉取，JGit 会自动处理 LFS 对象
            PullCommand pullCommand = git.pull();
            if (credentialsProvider != null) {
                pullCommand.setCredentialsProvider(credentialsProvider);
            }
            pullCommand.call();
            
            logger.info("LFS objects pulled successfully for repository: {}", repoPath);
        } catch (Exception e) {
            logger.error("Failed to pull LFS objects for repository: {}", repoPath, e);
            throw new RuntimeException("Failed to pull LFS objects", e);
        }
    }
    
    @Override
    public void pull(String repoPath, String filePath) {
        logger.info("Pulling LFS object for file: {} in repository: {}", filePath, repoPath);
        try (Git git = Git.open(getRepoDir(repoPath))) {
            // 确保 LFS 已初始化
            ensureLFSInitialized(git.getRepository());
            
            // 获取认证信息
            ai.git.config.GitCredential credential = configLoader.getCredential(repoPath);
            CredentialsProvider credentialsProvider = null;
            
            if (credential != null && credential.getUsername() != null && credential.getPassword() != null) {
                credentialsProvider = new UsernamePasswordCredentialsProvider(credential.getUsername(), credential.getPassword());
                logger.info("Using credentials for user: {}", credential.getUsername());
            }
            
            // 首先拉取最新代码
            PullCommand pullCommand = git.pull();
            if (credentialsProvider != null) {
                pullCommand.setCredentialsProvider(credentialsProvider);
            }
            pullCommand.call();
            
            // 检查文件是否为 LFS 指针文件
            File file = new File(repoPath, filePath);
            if (file.exists() && isLFSPointer(file.getAbsolutePath())) {
                // 如果是 LFS 指针文件，执行 git lfs pull 命令拉取具体文件
                logger.info("File is LFS pointer, executing git lfs pull for specific file");
                executeCommand(repoPath, "git", "lfs", "pull", "--include", filePath);
            }
            
            logger.info("LFS object pulled successfully for file: {} in repository: {}", filePath, repoPath);
        } catch (Exception e) {
            logger.error("Failed to pull LFS object for file: {} in repository: {}", filePath, repoPath, e);
            throw new RuntimeException("Failed to pull LFS object", e);
        }
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
                logger.debug("Command output: {}", line);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code: " + exitCode + ", command: " + String.join(" ", command));
        }
    }

    @Override
    public boolean isLFSPointer(String filePath) {
        logger.debug("Checking if file is LFS pointer: {}", filePath);
        try {
            File file = new File(filePath);
            if (file.length() < 100) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String firstLine = reader.readLine();
                    boolean isPointer = firstLine != null && firstLine.contains("git-lfs");
                    logger.debug("File: {} is LFS pointer: {}", filePath, isPointer);
                    return isPointer;
                }
            }
            return false;
        } catch (IOException e) {
            logger.error("Failed to check if file is LFS pointer: {}", filePath, e);
            return false;
        }
    }

    // 新增方法：解析 LFS 指针文件
    public Map<String, String> parseLFSPointer(String filePath) {
        logger.info("Parsing LFS pointer file: {}", filePath);
        Map<String, String> pointerInfo = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("oid sha256:")) {
                    pointerInfo.put("oid", line.substring("oid sha256:".length()));
                } else if (line.startsWith("size ")) {
                    pointerInfo.put("size", line.substring("size ".length()));
                }
            }
            logger.info("LFS pointer file parsed successfully: {}", filePath);
        } catch (IOException e) {
            logger.error("Failed to parse LFS pointer file: {}", filePath, e);
            throw new RuntimeException("Failed to parse LFS pointer", e);
        }
        return pointerInfo;
    }

    @Override
    public List<Map<String, Object>> getFileVersions(String repoPath, String filePath) {
        logger.info("Getting file versions for: {} in repository: {}", filePath, repoPath);
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
            logger.info("Retrieved {} versions for file: {} in repository: {}", versions.size(), filePath, repoPath);
        } catch (Exception e) {
            logger.error("Failed to get file versions for: {} in repository: {}", filePath, repoPath, e);
            throw new RuntimeException("Failed to get file versions", e);
        }
        return versions;
    }

    @Override
    public void rollbackToVersion(String repoPath, String filePath, String commitHash) {
        logger.info("Rolling back file: {} to commit: {} in repository: {}", filePath, commitHash, repoPath);
        try (Git git = Git.open(getRepoDir(repoPath))) {
            // 切换到指定提交
            git.checkout().setStartPoint(commitHash).addPath(filePath).call();

            // 拉取 LFS 对象
            git.pull().call();
            logger.info("File: {} rolled back to commit: {} successfully in repository: {}", filePath, commitHash, repoPath);
        } catch (Exception e) {
            logger.error("Failed to rollback file: {} to commit: {} in repository: {}", filePath, commitHash, repoPath, e);
            throw new RuntimeException("Failed to rollback file", e);
        }
    }

    @Override
    public void rollbackRepository(String repoPath, String targetVersion) {
        logger.info("Rolling back repository: {} to version: {}", repoPath, targetVersion);
        try (Git git = Git.open(getRepoDir(repoPath))) {
            // 切换到指定版本
            git.checkout().setName(targetVersion).call();

            // 拉取 LFS 对象
            git.pull().call();
            logger.info("Repository: {} rolled back to version: {} successfully", repoPath, targetVersion);
        } catch (Exception e) {
            logger.error("Failed to rollback repository: {} to version: {}", repoPath, targetVersion, e);
            throw new RuntimeException("Failed to rollback repository", e);
        }
    }

    @Override
    public Map<String, Object> getStatus(String repoPath) {
        logger.info("Getting LFS status for repository: {}", repoPath);
        Map<String, Object> status = new HashMap<>();
        try (Git git = Git.open(getRepoDir(repoPath))) {
            // 检查 LFS 配置
            String lfsUrl = git.getRepository().getConfig().getString("lfs", null, "url");
            boolean isLfsEnabled = lfsUrl != null;

            // 读取跟踪规则
            List<String> trackedPatterns = new ArrayList<>();
            File gitAttributes = new File(repoPath, ".gitattributes");
            if (gitAttributes.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(gitAttributes))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("filter=lfs")) {
                            String pattern = line.split(" ")[0];
                            trackedPatterns.add(pattern);
                        }
                    }
                }
            }

            status.put("isLfsEnabled", isLfsEnabled);
            status.put("lfsServerUrl", lfsUrl);
            status.put("trackedPatterns", trackedPatterns);
            logger.info("LFS status retrieved for repository: {}", repoPath);
        } catch (Exception e) {
            logger.error("Failed to get LFS status for repository: {}", repoPath, e);
            status.put("isLfsEnabled", false);
            status.put("error", e.getMessage());
        }
        return status;
    }
    
    @Override
    public InputStream downloadLargeFile(String filePath) {
        logger.info("Downloading large file: {}", filePath);
        try {
            // 构建文件对象
            var file = new File(filePath);
            var absoluteFilePath = file.getAbsolutePath();
            
            if (!file.exists()) {
                throw new RuntimeException("File does not exist: " + absoluteFilePath);
            }
            
            // 检查文件是否为 LFS 指针文件
            if (isLFSPointer(absoluteFilePath)) {
                logger.info("File is LFS pointer, pulling LFS object");
                // 找到仓库路径
                var repoPath = findRepoPath(absoluteFilePath);
                
                // 计算相对路径
                var relativePath = absoluteFilePath.substring(repoPath.length() + 1);
                
                // 拉取 LFS 对象
                try (var git = Git.open(new File(repoPath))) {
                    // 确保 LFS 已初始化
                    ensureLFSInitialized(git.getRepository());
                    
                    // 执行 git lfs pull 命令拉取具体文件
                    executeCommand(repoPath, "git", "lfs", "pull", "--include", relativePath);
                }
            }
            
            // 检查文件是否存在
            if (!file.exists()) {
                throw new RuntimeException("File does not exist after pull: " + absoluteFilePath);
            }
            
            // 返回文件输入流
            logger.info("Large file downloaded successfully: {}", absoluteFilePath);
            return new FileInputStream(file);
        } catch (Exception e) {
            logger.error("Failed to download large file: {}", filePath, e);
            throw new RuntimeException("Failed to download large file", e);
        }
    }
}
