package ai.config;



public class DatasetUploadConfig {

    private DatasetConfig dataset;

    public DatasetConfig getDataset() {
        return dataset;
    }

    public void setDataset(DatasetConfig dataset) {
        this.dataset = dataset;
    }

    public static class DatasetConfig {
        private StorageConfig storage;
        private FileUploadConfig file_upload;
        private UrlDownloadConfig url_download;
        private StorageTypeConfig storage_type;

        public StorageConfig getStorage() {
            return storage;
        }

        public void setStorage(StorageConfig storage) {
            this.storage = storage;
        }

        public FileUploadConfig getFile_upload() {
            return file_upload;
        }

        public void setFile_upload(FileUploadConfig file_upload) {
            this.file_upload = file_upload;
        }

        public UrlDownloadConfig getUrl_download() {
            return url_download;
        }

        public void setUrl_download(UrlDownloadConfig url_download) {
            this.url_download = url_download;
        }

        public StorageTypeConfig getStorageType() {
            return storage_type;
        }

        public void setStorage_type(StorageTypeConfig storage_type) {
            this.storage_type = storage_type;
        }
    }

    public static class StorageConfig {
        private String container_base_path;
        private String host_mount_path;
        private String container_temp_path;
        private String host_temp_mount_path;

        // getter & setter
        public String getContainer_base_path() {
            return container_base_path;
        }

        public void setContainer_base_path(String container_base_path) {
            this.container_base_path = container_base_path;
        }

        public String getHost_mount_path() {
            return host_mount_path;
        }

        public void setHost_mount_path(String host_mount_path) {
            this.host_mount_path = host_mount_path;
        }

        public String getContainer_temp_path() {
            return container_temp_path;
        }

        public void setContainer_temp_path(String container_temp_path) {
            this.container_temp_path = container_temp_path;
        }

        public String getHost_temp_mount_path() {
            return host_temp_mount_path;
        }

        public void setHost_temp_mount_path(String host_temp_mount_path) {
            this.host_temp_mount_path = host_temp_mount_path;
        }
    }

    public static class FileUploadConfig {
        private long chunk_size;
        private String chunk_file_prefix;
        private long max_file_size;

        // getter & setter
        public long getChunk_size() {
            return chunk_size;
        }

        public void setChunk_size(long chunk_size) {
            this.chunk_size = chunk_size;
        }

        public String getChunk_file_prefix() {
            return chunk_file_prefix;
        }

        public void setChunk_file_prefix(String chunk_file_prefix) {
            this.chunk_file_prefix = chunk_file_prefix;
        }

        public long getMax_file_size() {
            return max_file_size;
        }

        public void setMax_file_size(long max_file_size) {
            this.max_file_size = max_file_size;
        }
    }

    public static class UrlDownloadConfig {
        private int timeout;
        private int retry_count;
        private boolean clean_failed_temp_file;

        // getter & setter
        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public int getRetry_count() {
            return retry_count;
        }

        public void setRetry_count(int retry_count) {
            this.retry_count = retry_count;
        }

        public boolean isClean_failed_temp_file() {
            return clean_failed_temp_file;
        }

        public void setClean_failed_temp_file(boolean clean_failed_temp_file) {
            this.clean_failed_temp_file = clean_failed_temp_file;
        }
    }

    public static class StorageTypeConfig {
        private int absolute_path;
        private int url_download;
        private int file_upload;

        // getter & setter
        public int getAbsolute_path() {
            return absolute_path;
        }

        public void setAbsolute_path(int absolute_path) {
            this.absolute_path = absolute_path;
        }

        public int getUrl_download() {
            return url_download;
        }

        public void setUrl_download(int url_download) {
            this.url_download = url_download;
        }

        public int getFile_upload() {
            return file_upload;
        }

        public void setFile_upload(int file_upload) {
            this.file_upload = file_upload;
        }
    }
}

