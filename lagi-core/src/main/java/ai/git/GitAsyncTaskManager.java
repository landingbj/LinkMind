package ai.git;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class GitAsyncTaskManager {
    private static final ExecutorService executor = Executors.newFixedThreadPool(10);
    private static final Map<String, AsyncTaskStatus> taskMap = new ConcurrentHashMap<>();

    public String submitClone(GitService gitService, String repoUrl, String targetPath) {
        String taskId = "clone_" + UUID.randomUUID().toString().substring(0, 8);
        AsyncTaskStatus status = new AsyncTaskStatus(taskId, TaskStatus.PENDING);
        taskMap.put(taskId, status);

        executor.submit(() -> {
            try {
                status.setStatus(TaskStatus.RUNNING);
                gitService.clone(repoUrl, targetPath);
                status.setStatus(TaskStatus.COMPLETED);
                status.setMessage("Clone completed successfully");
            } catch (Exception e) {
                status.setStatus(TaskStatus.FAILED);
                status.setMessage(e.getMessage());
            }
        });

        return taskId;
    }

    public String submitPull(GitService gitService, String repoPath, String remote, String branch) {
        String taskId = "pull_" + UUID.randomUUID().toString().substring(0, 8);
        AsyncTaskStatus status = new AsyncTaskStatus(taskId, TaskStatus.PENDING);
        taskMap.put(taskId, status);

        executor.submit(() -> {
            try {
                status.setStatus(TaskStatus.RUNNING);
                gitService.pull(repoPath, remote, branch);
                status.setStatus(TaskStatus.COMPLETED);
                status.setMessage("Pull completed successfully");
            } catch (Exception e) {
                status.setStatus(TaskStatus.FAILED);
                status.setMessage(e.getMessage());
            }
        });

        return taskId;
    }

    public String submitPush(GitService gitService, String repoPath, String remote, String branch) {
        String taskId = "push_" + UUID.randomUUID().toString().substring(0, 8);
        AsyncTaskStatus status = new AsyncTaskStatus(taskId, TaskStatus.PENDING);
        taskMap.put(taskId, status);

        executor.submit(() -> {
            try {
                status.setStatus(TaskStatus.RUNNING);
                gitService.push(repoPath, remote, branch);
                status.setStatus(TaskStatus.COMPLETED);
                status.setMessage("Push completed successfully");
            } catch (Exception e) {
                status.setStatus(TaskStatus.FAILED);
                status.setMessage(e.getMessage());
            }
        });

        return taskId;
    }

    public String submitLFSPull(GitLFSService lfsService, String repoPath) {
        String taskId = "lfs_pull_" + UUID.randomUUID().toString().substring(0, 8);
        AsyncTaskStatus status = new AsyncTaskStatus(taskId, TaskStatus.PENDING);
        taskMap.put(taskId, status);

        executor.submit(() -> {
            try {
                status.setStatus(TaskStatus.RUNNING);
                lfsService.pull(repoPath);
                status.setStatus(TaskStatus.COMPLETED);
                status.setMessage("LFS pull completed successfully");
            } catch (Exception e) {
                status.setStatus(TaskStatus.FAILED);
                status.setMessage(e.getMessage());
            }
        });

        return taskId;
    }

    public String submitLFSPush(GitLFSService lfsService, String repoPath) {
        String taskId = "lfs_push_" + UUID.randomUUID().toString().substring(0, 8);
        AsyncTaskStatus status = new AsyncTaskStatus(taskId, TaskStatus.PENDING);
        taskMap.put(taskId, status);

        executor.submit(() -> {
            try {
                status.setStatus(TaskStatus.RUNNING);
                lfsService.push(repoPath);
                status.setStatus(TaskStatus.COMPLETED);
                status.setMessage("LFS push completed successfully");
            } catch (Exception e) {
                status.setStatus(TaskStatus.FAILED);
                status.setMessage(e.getMessage());
            }
        });

        return taskId;
    }

    public AsyncTaskStatus getTaskStatus(String taskId) {
        return taskMap.get(taskId);
    }
}
