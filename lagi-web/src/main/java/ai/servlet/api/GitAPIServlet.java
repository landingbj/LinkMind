package ai.servlet.api;

import ai.git.*;
import ai.git.impl.GitLFSServiceImpl;
import ai.git.impl.GitServiceImpl;
import ai.servlet.RestfulServlet;
import ai.servlet.annotation.Body;
import ai.servlet.annotation.Get;
import ai.servlet.annotation.Param;
import ai.servlet.annotation.Post;
import cn.hutool.json.JSONObject;
import org.eclipse.jgit.api.Status;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

public class GitAPIServlet extends RestfulServlet {

    private final GitService gitService = new GitServiceImpl();
    private final GitLFSService lfsService = new GitLFSServiceImpl();
    private final GitAsyncTaskManager asyncTaskManager = new GitAsyncTaskManager();

    // 基本Git操作
    @Post("git-init")
    public String init(@Body JSONObject request) {
        String repoPath = request.getStr("repoPath");
        if (repoPath == null) {
            throw new IllegalArgumentException("repoPath is required");
        }
        gitService.init(repoPath);
        return "Git 仓库已初始化成功";
    }

    @Post("git-clone")
    public Map<String, String> clone(@Body JSONObject request) {
        String repoUrl = request.getStr("repoUrl");
        String targetPath = request.getStr("targetPath");
        if (repoUrl == null || targetPath == null) {
            throw new IllegalArgumentException("repoUrl and targetPath are required");
        }
        String taskId = asyncTaskManager.submitClone(gitService, repoUrl, targetPath);
        return Map.of("taskId", taskId, "status", "PENDING");
    }

    @Post("git-add")
    public String add(@Body JSONObject request) {
        String repoPath = request.getStr("repoPath");
        List<String> files = request.getJSONArray("files").toList(String.class);
        if (repoPath == null || files == null) {
            throw new IllegalArgumentException("repoPath and files are required");
        }
        gitService.add(repoPath, files);
        return "文件添加成功";
    }

    @Post("git-commit")
    public String commit(@Body JSONObject request) {
        String repoPath = request.getStr("repoPath");
        String message = request.getStr("message");
        if (repoPath == null || message == null) {
            throw new IllegalArgumentException("repoPath and message are required");
        }
        gitService.commit(repoPath, message);
        return "提交完成";
    }

    @Post("git-push")
    public Map<String, String> push(@Body JSONObject request) {
        String repoPath = request.getStr("repoPath");
        String remote = request.getStr("remote", "origin");
        String branch = request.getStr("branch", "main");
        if (repoPath == null) {
            throw new IllegalArgumentException("repoPath is required");
        }
        String taskId = asyncTaskManager.submitPush(gitService, repoPath, remote, branch);
        return Map.of("taskId", taskId, "status", "PENDING");
    }

    @Post("git-pull")
    public Map<String, String> pull(@Body JSONObject request) {
        String repoPath = request.getStr("repoPath");
        String remote = request.getStr("remote", "origin");
        String branch = request.getStr("branch", "main");
        if (repoPath == null) {
            throw new IllegalArgumentException("repoPath is required");
        }
        String taskId = asyncTaskManager.submitPull(gitService, repoPath, remote, branch);
        return Map.of("taskId", taskId, "status", "PENDING");
    }

    @Get("git-branch")
    public List<String> branch(@Param("repoPath") String repoPath) {
        if (repoPath == null) {
            throw new IllegalArgumentException("repoPath is required");
        }
        return gitService.getBranches(repoPath);
    }

    @Post("git-checkout")
    public String checkout(@Body JSONObject request) {
        String repoPath = request.getStr("repoPath");
        String branch = request.getStr("branch");
        if (repoPath == null || branch == null) {
            throw new IllegalArgumentException("repoPath and branch are required");
        }
        gitService.checkout(repoPath, branch);
        return "分支切换成功";
    }

    // 仓库文件管理
    @Get("git-repos")
    public List<Map<String, Object>> repos(@Param("basePath") String basePath) {
        if (basePath == null) {
            throw new IllegalArgumentException("basePath is required");
        }
        return gitService.getRepositories(basePath);
    }

    @Get("git-files")
    public List<Map<String, Object>> files(@Param("repoPath") String repoPath, @Param("dirPath") String dirPath) {
        if (repoPath == null) {
            throw new IllegalArgumentException("repoPath is required");
        }
        if (dirPath == null) {
            dirPath = "/";
        }
        return gitService.getFiles(repoPath, dirPath);
    }

    @Get("git-dirs")
    public List<Map<String, Object>> dirs(@Param("repoPath") String repoPath, @Param("dirPath") String dirPath) {
        if (repoPath == null) {
            throw new IllegalArgumentException("repoPath is required");
        }
        if (dirPath == null) {
            dirPath = "/";
        }
        return gitService.getDirectories(repoPath, dirPath);
    }

    // 文件历史记录
    @Get("git-history")
    public List<Map<String, Object>> history(@Param("repoPath") String repoPath, @Param("filePath") String filePath) {
        if (repoPath == null || filePath == null) {
            throw new IllegalArgumentException("repoPath and filePath are required");
        }
        return gitService.getFileHistory(repoPath, filePath);
    }

    @Get("git-diff")
    public Map<String, Object> diff(@Param("repoPath") String repoPath, @Param("filePath") String filePath, @Param("commitHash") String commitHash) {
        if (repoPath == null || filePath == null || commitHash == null) {
            throw new IllegalArgumentException("repoPath, filePath and commitHash are required");
        }
        return gitService.getFileDiff(repoPath, filePath, commitHash);
    }

    @Get("git-log")
    public List<Map<String, Object>> log(@Param("repoPath") String repoPath, @Param("limit") Integer limit) {
        if (repoPath == null) {
            throw new IllegalArgumentException("repoPath is required");
        }
        if (limit == null) {
            limit = 10;
        }
        return gitService.getCommitLog(repoPath, limit);
    }

    // 分支管理
    @Post("git-branch/create")
    public String createBranch(@Body JSONObject request) {
        String repoPath = request.getStr("repoPath");
        String branchName = request.getStr("branchName");
        if (repoPath == null || branchName == null) {
            throw new IllegalArgumentException("repoPath and branchName are required");
        }
        gitService.createBranch(repoPath, branchName);
        return "分支创建成功";
    }

    @Post("git-branch/delete")
    public String deleteBranch(@Body JSONObject request) {
        String repoPath = request.getStr("repoPath");
        String branchName = request.getStr("branchName");
        if (repoPath == null || branchName == null) {
            throw new IllegalArgumentException("repoPath and branchName are required");
        }
        gitService.deleteBranch(repoPath, branchName);
        return "分支删除成功";
    }

    @Post("git-branch/merge")
    public String mergeBranch(@Body JSONObject request) {
        String repoPath = request.getStr("repoPath");
        String branchName = request.getStr("branchName");
        if (repoPath == null || branchName == null) {
            throw new IllegalArgumentException("repoPath and branchName are required");
        }
        gitService.mergeBranch(repoPath, branchName);
        return "分支合并成功";
    }

    // 标签管理
    @Post("git-tag/create")
    public String createTag(@Body JSONObject request) {
        String repoPath = request.getStr("repoPath");
        String tagName = request.getStr("tagName");
        String message = request.getStr("message");
        if (repoPath == null || tagName == null) {
            throw new IllegalArgumentException("repoPath and tagName are required");
        }
        gitService.createTag(repoPath, tagName, message);
        return "标签创建成功";
    }

    @Get("git-tag/list")
    public List<String> listTags(@Param("repoPath") String repoPath) {
        if (repoPath == null) {
            throw new IllegalArgumentException("repoPath is required");
        }
        return gitService.getTags(repoPath);
    }

    @Post("git-tag/delete")
    public String deleteTag(@Body JSONObject request) {
        String repoPath = request.getStr("repoPath");
        String tagName = request.getStr("tagName");
        if (repoPath == null || tagName == null) {
            throw new IllegalArgumentException("repoPath and tagName are required");
        }
        gitService.deleteTag(repoPath, tagName);
        return "标签删除成功";
    }

    // 远程仓库管理
    @Post("git-remote/add")
    public String addRemote(@Body JSONObject request) {
        String repoPath = request.getStr("repoPath");
        String name = request.getStr("name");
        String url = request.getStr("url");
        if (repoPath == null || name == null || url == null) {
            throw new IllegalArgumentException("repoPath, name and url are required");
        }
        gitService.addRemote(repoPath, name, url);
        return "远程仓库添加成功";
    }

    @Post("git-remote/delete")
    public String deleteRemote(@Body JSONObject request) {
        String repoPath = request.getStr("repoPath");
        String name = request.getStr("name");
        if (repoPath == null || name == null) {
            throw new IllegalArgumentException("repoPath and name are required");
        }
        gitService.deleteRemote(repoPath, name);
        return "远程仓库删除成功";
    }

    @Get("git-remote/list")
    public List<Map<String, Object>> listRemotes(@Param("repoPath") String repoPath) {
        if (repoPath == null) {
            throw new IllegalArgumentException("repoPath is required");
        }
        return gitService.getRemotes(repoPath);
    }

    // 暂存区管理
    @Get("git-status")
    public Map<String, Object> status(@Param("repoPath") String repoPath) {
        if (repoPath == null) {
            throw new IllegalArgumentException("repoPath is required");
        }
        return gitService.getStatus(repoPath, null);
    }

    // 撤销操作
    @Post("git-reset")
    public String reset(@Body JSONObject request) {
        String repoPath = request.getStr("repoPath");
        String commitHash = request.getStr("commitHash");
        String mode = request.getStr("mode", "mixed");
        if (repoPath == null || commitHash == null) {
            throw new IllegalArgumentException("repoPath and commitHash are required");
        }
        gitService.reset(repoPath, commitHash, mode);
        return "重置成功";
    }

    @Post("git-checkout-file")
    public String checkoutFile(@Body JSONObject request) {
        String repoPath = request.getStr("repoPath");
        String filePath = request.getStr("filePath");
        if (repoPath == null || filePath == null) {
            throw new IllegalArgumentException("repoPath and filePath are required");
        }
        gitService.checkoutFile(repoPath, filePath);
        return "文件修改撤销成功";
    }

    // Git LFS操作
    @Post("git-lfs-init")
    public String initLFS(@Body JSONObject request) {
        String repoPath = request.getStr("repoPath");
        String lfsServerUrl = request.getStr("lfsServerUrl");
        if (repoPath == null || lfsServerUrl == null) {
            throw new IllegalArgumentException("repoPath and lfsServerUrl are required");
        }
        lfsService.init(repoPath, lfsServerUrl);
        return "LFS 初始化成功";
    }

    @Post("git-lfs-track")
    public String track(@Body JSONObject request) {
        String repoPath = request.getStr("repoPath");
        String pattern = request.getStr("pattern");
        if (repoPath == null || pattern == null) {
            throw new IllegalArgumentException("repoPath and pattern are required");
        }
        lfsService.track(repoPath, pattern);
        return "模式跟踪成功";
    }

    @Post("git-lfs-push")
    public Map<String, String> pushLFS(@Body JSONObject request) {
        String repoPath = request.getStr("repoPath");
        if (repoPath == null) {
            throw new IllegalArgumentException("repoPath is required");
        }
        String taskId = asyncTaskManager.submitLFSPush(lfsService, repoPath);
        return Map.of("taskId", taskId, "status", "PENDING");
    }

    @Post("git-lfs-pull")
    public void pullLFS(@Body JSONObject request, HttpServletResponse response) {
        var filePath = request.getStr("filePath");
        if (filePath == null) {
            throw new IllegalArgumentException("filePath is required");
        }

        try (var inputStream = lfsService.downloadLargeFile(filePath);
             var outputStream = response.getOutputStream()) {
            // 设置响应头
            var file = new java.io.File(filePath);
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
            response.setContentLengthLong(file.length());

            // 将文件流写入响应
            inputStream.transferTo(outputStream);
        } catch (Exception e) {
            response.setStatus(500);
            try {
                response.getWriter().write("Error downloading file: " + e.getMessage());
            } catch (java.io.IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Get("git-lfs-versions")
    public List<Map<String, Object>> lfsVersions(@Param("repoPath") String repoPath, @Param("filePath") String filePath) {
        if (repoPath == null || filePath == null) {
            throw new IllegalArgumentException("repoPath and filePath are required");
        }
        return lfsService.getFileVersions(repoPath, filePath);
    }

    @Post("git-lfs-rollback/file")
    public String rollbackFileLFS(@Body JSONObject request) {
        String repoPath = request.getStr("repoPath");
        String filePath = request.getStr("filePath");
        String commitHash = request.getStr("commitHash");
        if (repoPath == null || filePath == null || commitHash == null) {
            throw new IllegalArgumentException("repoPath, filePath and commitHash are required");
        }
        lfsService.rollbackToVersion(repoPath, filePath, commitHash);
        return "文件回滚成功";
    }

    @Post("git-lfs-rollback/repo")
    public String rollbackRepoLFS(@Body JSONObject request) {
        String repoPath = request.getStr("repoPath");
        String targetVersion = request.getStr("targetVersion");
        if (repoPath == null || targetVersion == null) {
            throw new IllegalArgumentException("repoPath and targetVersion are required");
        }
        lfsService.rollbackRepository(repoPath, targetVersion);
        return "仓库回滚成功";
    }

    @Get("git-lfs-status")
    public Map<String, Object> lfsStatus(@Param("repoPath") String repoPath) {
        if (repoPath == null) {
            throw new IllegalArgumentException("repoPath is required");
        }
        return lfsService.getStatus(repoPath);
    }

    @Get("git-task-status")
    public AsyncTaskStatus getTaskStatus(@Param("taskId") String taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId is required");
        }
        AsyncTaskStatus status = asyncTaskManager.getTaskStatus(taskId);
        if (status == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        return status;
    }

    // 一键上传：添加文件到暂存区 + 提交更改 + 推送更改
    @Post("git-upload")
    public Map<String, String> upload(@Body JSONObject request) {
        String repoPath = request.getStr("repoPath");
        List<String> files = request.getJSONArray("files").toList(String.class);
        String message = request.getStr("message");
        String remote = request.getStr("remote", "origin");
        String branch = request.getStr("branch", "main");

        if (repoPath == null || files == null || message == null) {
            return Map.of( "code","400","message", "failed", "errorMsg", "repoPath, files and message are required");
        }

        // 1. 添加文件到暂存区
        gitService.add(repoPath, files);

        // 2. 检查指定路径下的文件是否有变化
        Status status = gitService.getStatus(repoPath);
        boolean hasChanges =  status.hasUncommittedChanges();

        // 3. 只有当有变化时才提交
        if (hasChanges) {
            gitService.commit(repoPath, message);
        }

        // 4. 判断是否有未推送的内容
        if (gitService.hasUnpushedCommits(repoPath, remote, branch)) {
            String taskId = asyncTaskManager.submitPush(gitService, repoPath, remote, branch);
            return Map.of("taskId", taskId, "status", "PENDING");
        } else {
            return Map.of( "code","400","message", "failed", "errorMsg", "NO_CHANGES");
        }
    }
}
