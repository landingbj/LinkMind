package ai.config;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class UploadConfig {

    private DatasetConfig dataset;

    private CommonUploadConfig common;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @ToString
    public static class CommonUploadConfig {
        @JsonProperty("host_upload_path")
        private String hostUploadPath;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @ToString
    public static class DatasetConfig {
        private StorageConfig storage;
        private FileUploadConfig file_upload;
        private UrlDownloadConfig url_download;
        private StorageTypeConfig storage_type;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @ToString
    public static class StorageConfig {
        private String container_base_path;
        private String host_mount_path;
        private String container_temp_path;
        private String host_temp_mount_path;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @ToString
    public static class FileUploadConfig {
        private long chunk_size;
        private String chunk_file_prefix;
        private long max_file_size;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @ToString
    public static class UrlDownloadConfig {
        private int timeout;
        private int retry_count;
        private boolean clean_failed_temp_file;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @ToString
    public static class StorageTypeConfig {
        private int absolute_path;
        private int url_download;
        private int file_upload;
    }
}

