package ai.vector;

import ai.bigdata.BigdataService;
import ai.bigdata.pojo.TextIndexData;
import ai.common.pojo.FileChunkResponse;
import ai.common.pojo.FileInfo;
import ai.common.pojo.IndexSearchData;
import ai.common.pojo.VectorStoreConfig;
import ai.common.utils.FileUtils;
import ai.common.utils.ThreadPoolManager;
import ai.intent.IntentService;
import ai.intent.enums.IntentStatusEnum;
import ai.intent.impl.SampleIntentServiceImpl;
import ai.intent.pojo.IntentResult;
import ai.manager.VectorStoreManager;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatMessage;
import ai.utils.LagiGlobal;
import ai.utils.StoppingWordUtil;
import ai.utils.qa.ChatCompletionUtil;
import ai.vector.impl.BaseVectorStore;
import ai.vector.loader.DocumentLoader;
import ai.vector.loader.impl.*;
import ai.vector.loader.pojo.SplitConfig;
import ai.vector.loader.util.DocQaExtractor;
import ai.vector.pojo.*;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static ai.vector.VectorStoreConstant.FileChunkSource.*;

@Slf4j
public class VectorStoreService {
    private final Gson gson = new Gson();
    private final BaseVectorStore vectorStore;
    private static final ExecutorService executor;
    private final Map<String, DocumentLoader> loaderMap = new HashMap<>();
    private static final List<String> DEFAULT_SOURCES = new ArrayList<>();

    static {
        ThreadPoolManager.registerExecutor("vector-service", new ThreadPoolExecutor(30, 100, 10, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                (r, executor) -> {
                    log.error(StrUtil.format("线程池队({})任务过多请求被拒绝", "vector-service"));
                }
        ));
        executor = ThreadPoolManager.getExecutor("vector-service");
        DEFAULT_SOURCES.add(FILE_CHUNK_SOURCE_FILE);
        DEFAULT_SOURCES.add(FILE_CHUNK_SOURCE_QA);
    }

    private final IntentService intentService = new SampleIntentServiceImpl();
    private final BigdataService bigdataService = new BigdataService();
    private static final VectorCache vectorCache = VectorCache.getInstance();

    public VectorStoreService() {
        this.vectorStore = (BaseVectorStore) VectorStoreManager.getInstance().getAdapter();
        TxtLoader txtLoader = new TxtLoader();
        loaderMap.put("txt", txtLoader);

        ExcelLoader excelLoader = new ExcelLoader();
        loaderMap.put("xls", excelLoader);
        loaderMap.put("xlsx", excelLoader);

        CsvLoader csvLoader = new CsvLoader();
        loaderMap.put("csv", csvLoader);

        ImageLoader imageLoader = new ImageLoader();
        loaderMap.put("jpg", imageLoader);
        loaderMap.put("jpeg", imageLoader);
        loaderMap.put("webp", imageLoader);
        loaderMap.put("png", imageLoader);
        loaderMap.put("gif", imageLoader);
        loaderMap.put("bmp", imageLoader);

        DocLoader docLoader = new DocLoader();
        loaderMap.put("doc", docLoader);
        loaderMap.put("docx", docLoader);

        PptLoader pptLoader = new PptLoader();
        loaderMap.put("pptx", pptLoader);
        loaderMap.put("ppt", pptLoader);

        PdfLoader pdfLoader = new PdfLoader();
        loaderMap.put("pdf", pdfLoader);
        loaderMap.put("common", docLoader);

        HtmlLoader htmlLoader = new HtmlLoader();
        loaderMap.put("html", htmlLoader);

        MarkdownLoader mdLoader = new MarkdownLoader();
        loaderMap.put("md", mdLoader);
    }

    public VectorStoreConfig getVectorStoreConfig() {
        if (vectorStore == null) {
            return null;
        }
        return vectorStore.getConfig();
    }


    public List<List<String>> addFileVectors(File file, Map<String, Object> metadatas, String category) throws IOException {
        Integer wenben_type = 512;
        Integer biaoge_type = 512;
        Integer tuwen_type = 512;
        String suffix = file.getName().toLowerCase().split("\\.")[1];
        DocumentLoader documentLoader = loaderMap.getOrDefault(suffix, loaderMap.get("common"));
        List<List<FileChunkResponse.Document>> docs = documentLoader.load(file.getPath(), new SplitConfig(wenben_type, tuwen_type, biaoge_type, category, metadatas));
        String fileName = file.getName();
        if (fileName.endsWith(".docx") || fileName.endsWith(".doc") || fileName.endsWith(".txt") || fileName.endsWith(".pdf")) {
            docs = DocQaExtractor.parseText(docs);
        }
        List<Future<?>> futures = new ArrayList<>();
        List<List<FileInfo>> fileParseList = new ArrayList<>();
        for (List<FileChunkResponse.Document> docList : docs) {
            List<FileInfo> fileList = getFileInfoList(metadatas, docList);
            fileParseList.add(fileList);
            futures.add(executor.submit(() -> {
                try {
                    upsertFileVectors(fileList, category);
                } catch (IOException e) {
                    log.error("Error processing document chunk", e);
                }
            }));
        }

        IOException firstException = null;
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while waiting for task completion", e);
                if (firstException == null) {
                    firstException = new IOException("Interrupted while processing file", e);
                }
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                log.error("Error waiting for task completion", cause);
                if (firstException == null) {
                    if (cause instanceof IOException) {
                        firstException = (IOException) cause;
                    } else {
                        firstException = new IOException("Error processing document chunks", cause);
                    }
                }
            }
        }
        if (firstException != null) {
            throw firstException;
        }
        List<List<String>> results = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            List<String> vectorIds = fileParseList.get(i).stream().filter(fileInfo -> fileInfo.getOrder() != null && fileInfo.getOrder() > 0)
                    .sorted(Comparator.comparing(FileInfo::getOrder))
                    .map(FileInfo::getEmbedding_id)
                    .collect(Collectors.toList());
            results.add(vectorIds);
        }
        return results;
    }

    public CompletableFuture<List<List<String>>> addFileVectorsAsync(
            File file, Map<String, Object> metadatas, String category, IngestListener listener) {

        String fileId = (String) metadatas.computeIfAbsent("file_id",
                k -> UUID.randomUUID().toString().replace("-", ""));

        // 1) 切片放到线程池里跑
        CompletableFuture<List<List<FileChunkResponse.Document>>> splitFuture =
                CompletableFuture.supplyAsync(() -> {
                    Integer wenben_type = 512, biaoge_type = 512, tuwen_type = 256;
                    String suffix = file.getName().toLowerCase().split("\\.")[1];
                    DocumentLoader loader = loaderMap.getOrDefault(suffix, loaderMap.get("common"));
                    return loader.load(file.getPath(),
                            new SplitConfig(wenben_type, tuwen_type, biaoge_type, category, metadatas));
                }, executor).whenComplete((docs, ex) -> {
                    if (listener != null) {
                        if (ex == null) listener.onSplitReady(fileId, docs);    // —— 切片完成立刻可见 —— //
                        else listener.onError(fileId, ex);
                    }
                });

        // 2) 切片后→问答抽取（组级回调）
        CompletableFuture<List<List<FileChunkResponse.Document>>> qaFuture =
                splitFuture.thenCompose(docs -> {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".docx") || name.endsWith(".doc")
                            || name.endsWith(".txt") || name.endsWith(".pdf")) {
                        return DocQaExtractor.parseTextAsync(
                                docs, executor,
                                (gIdx, cIdx, block) -> {
//                                    ProgressTrackerEntity t = LRUCacheUtil.get(taskId);
//                                    if (t != null) {
//                                        t.saveQaChunk(file.getName(), gIdx, cIdx, block);
//                                        LRUCacheUtil.put(taskId, t);
//                                    }
                                    if (listener != null) listener.onQaChunk(fileId, gIdx, cIdx, block);
                                },
                                (groupIdx, qaDocs) -> { // —— 每组完成立刻回调 —— //
                                    if (listener != null) listener.onQaGroupReady(fileId, groupIdx, qaDocs);
                                });
                    } else {
                        // 非文本类，直接透传
                        return CompletableFuture.completedFuture(docs);
                    }
                });

        // 3) （可选）在这之后你再继续 upsert，每组 upsert 完成也可以回调 vectorIds
        return qaFuture.thenCompose(qaDocs -> {
            // 维持你原来的：把 qaDocs -> fileParseList -> 并发 upsert -> 归并 vectorIds 的逻辑
            List<CompletableFuture<List<String>>> upserts = new ArrayList<>();

            for (int i = 0; i < qaDocs.size(); i++) {
                final int idx = i;
                final List<FileChunkResponse.Document> group = qaDocs.get(i);
                final List<FileInfo> fileList = getFileInfoList(metadatas, group);

                CompletableFuture<List<String>> f = CompletableFuture.supplyAsync(() -> {
                    try {
                        upsertFileVectors(fileList, category);
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                    // 组内按 order 归并 vectorId
                    return fileList.stream()
                            .filter(fi -> fi.getOrder() != null && fi.getOrder() > 0)
                            .sorted(Comparator.comparing(FileInfo::getOrder))
                            .map(FileInfo::getEmbedding_id)
                            .collect(Collectors.toList());
                }, executor)
                        // 如果你也想“每组 upsert 完就把 vectorIds 回传”，这里再加一个 listener 方法：
                        // .whenComplete((vectorIds, ex) -> { if (listener != null) ... })
                        ;
                upserts.add(f);
            }

            return CompletableFuture
                    .allOf(upserts.toArray(new CompletableFuture[0]))
                    .thenApply(v -> upserts.stream().map(CompletableFuture::join).collect(Collectors.toList()));
        }).whenComplete((r, ex) -> {
            if (listener != null && ex != null) listener.onError(fileId, ex);
        });
    }

    private List<FileInfo> getFileInfoList(Map<String, Object> metadatas, List<FileChunkResponse.Document> docList) {
        List<FileInfo> fileList = new ArrayList<>();
//        String fileName = metadatas.get("filename").toString();
//        if (fileName != null) {
//            int dotIndex = fileName.lastIndexOf(".");
//            if (dotIndex != -1) {
//                fileName = fileName.substring(0, dotIndex);
//            }
//            FileInfo fi1 = new FileInfo();
//            String e1 = UUID.randomUUID().toString().replace("-", "");
//            fi1.setEmbedding_id(e1);
//            fi1.setText(fileName);
//            Map<String, Object> t1 = new HashMap<>(metadatas);
//            t1.remove("parent_id");
//            fi1.setMetadatas(t1);
//            fileList.add(fi1);
//        }
        Map<String, String> refrenceDocMap = new HashMap<>();
        Map<String, String> extraMetadatas = new HashMap<>();
        for (FileChunkResponse.Document doc : docList) {
            FileInfo fileInfo = new FileInfo();
            String embeddingId = UUID.randomUUID().toString().replace("-", "");
            fileInfo.setEmbedding_id(embeddingId);
            fileInfo.setText(doc.getText());
            fileInfo.setOrder(doc.getOrder());
            if (fileInfo.getOrder() != null && fileInfo.getOrder() > 0) {
                refrenceDocMap.put(fileInfo.getOrder().toString(), embeddingId);
            } else {
                if (doc.getReferenceDocumentId() != null && !doc.getReferenceDocumentId().isEmpty()) {
                    extraMetadatas.put(embeddingId, doc.getReferenceDocumentId());
                }
            }
            Map<String, Object> tmpMetadatas = new HashMap<>(metadatas);
            if (doc.getImages() != null) {
                tmpMetadatas.put("image", gson.toJson(doc.getImages()));
            }
            if (doc.getSource() != null) {
                tmpMetadatas.put("source", doc.getSource());
            } else {
                tmpMetadatas.put("source", FILE_CHUNK_SOURCE_FILE);
            }
            fileInfo.setMetadatas(tmpMetadatas);
            fileList.add(fileInfo);
        }

        sortFileListByOrder(fileList);

        String parentId = "";
        for (FileInfo fileInfo : fileList) {
            String source = (String) fileInfo.getMetadatas().get("source");
            if (FILE_CHUNK_SOURCE_FILE.equals(source)) {
                fileInfo.getMetadatas().put("parent_id", parentId);
                parentId = fileInfo.getEmbedding_id();
            } else {
                if (extraMetadatas.containsKey(fileInfo.getEmbedding_id())) {
                    String referenceId = refrenceDocMap.get(extraMetadatas.get(fileInfo.getEmbedding_id()));
                    if (referenceId != null) {
                        fileInfo.getMetadatas().put("reference_document_id", referenceId);
                        fileInfo.getMetadatas().put("parent_id", referenceId);
                    }
                }
            }
        }

        return fileList;
    }

    /**
     * Sort file list by order field in ascending order.
     * Items with null order values are placed at the end of the list.
     *
     * @param fileList the list of FileInfo objects to sort
     */
    private void sortFileListByOrder(List<FileInfo> fileList) {
        fileList.sort((f1, f2) -> {
            Integer order1 = f1.getOrder();
            Integer order2 = f2.getOrder();

            // Handle null orders - put them at the end
            if (order1 == null && order2 == null) {
                return 0;
            }
            if (order1 == null) {
                return 1;
            }
            if (order2 == null) {
                return -1;
            }

            return order1.compareTo(order2);
        });
    }

    public void upsertCustomVectors(List<UpsertRecord> upsertRecords, String category) {
        this.upsertCustomVectors(upsertRecords, category, false);
    }

    public void upsertCustomVectors(List<UpsertRecord> upsertRecords, String category, boolean isContextLinked) {
        for (UpsertRecord upsertRecord : upsertRecords) {
            String embeddingId = UUID.randomUUID().toString().replace("-", "");
            if (upsertRecord.getId() != null && !upsertRecord.getId().isEmpty()) {
                embeddingId = upsertRecord.getId();
            }
            upsertRecord.setId(embeddingId);
        }
        if (isContextLinked) {
            if (!upsertRecords.isEmpty()) {
                upsertRecords.get(0).getMetadata().put("parent_id", "");
            }
            for (int i = 1; i < upsertRecords.size(); i++) {
                String parentId = upsertRecords.get(i - 1).getId();
                upsertRecords.get(i).getMetadata().put("parent_id", parentId);
            }
        }
        this.upsert(upsertRecords, category);
    }

    private void upsertFileVectors(List<FileInfo> fileList, String category) throws IOException {
        List<UpsertRecord> upsertRecords = new ArrayList<>();
        for (FileInfo fileInfo : fileList) {
            upsertRecords.add(convertToUpsertRecord(fileInfo));
        }
        this.upsert(upsertRecords, category);
    }

    public UpsertRecord convertToUpsertRecord(FileInfo fileInfo) {
        UpsertRecord upsertRecord = new UpsertRecord();
        upsertRecord.setDocument(fileInfo.getText());
        upsertRecord.setId(fileInfo.getEmbedding_id());

        Map<String, String> metadata = new HashMap<>();
        for (Map.Entry<String, Object> entry : fileInfo.getMetadatas().entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue().toString();
            metadata.put(entry.getKey(), value);
        }
        upsertRecord.setMetadata(metadata);

        return upsertRecord;
    }

    public void upsert(List<UpsertRecord> upsertRecords) {
        this.upsert(upsertRecords, vectorStore.getConfig().getDefaultCategory());
    }

    public void upsert(List<UpsertRecord> upsertRecords, String category) {
        for (UpsertRecord upsertRecord : upsertRecords) {
            TextIndexData data = new TextIndexData();
            data.setId(upsertRecord.getId());
            data.setText(upsertRecord.getDocument());
            data.setCategory(category);
            bigdataService.upsert(data);
        }
        this.vectorStore.upsert(upsertRecords, category);
    }

    public List<IndexRecord> query(QueryCondition queryCondition) {
        return this.vectorStore.query(queryCondition);
    }

    public List<IndexRecord> fetch(List<String> ids, String category) {
        return this.vectorStore.fetch(ids, category);
    }

    public IndexRecord fetch(String id, String category) {
        List<String> ids = Collections.singletonList(id);
        List<IndexRecord> indexRecords = this.vectorStore.fetch(ids, category);
        IndexRecord result = null;
        if (indexRecords.size() == 1) {
            result = indexRecords.get(0);
        }
        return result;
    }

    public List<IndexRecord> fetch(Map<String, String> where) {
        return this.vectorStore.fetch(where);
    }

    public List<IndexRecord> fetch(Map<String, String> where, String category) {
        return this.vectorStore.fetch(where, category);
    }

    public List<IndexRecord> fetch(int limit, int offset, String category) {
        return this.vectorStore.fetch(limit, offset, category);
    }

    public void delete(List<String> ids) {
        this.vectorStore.delete(ids);
    }

    public void delete(List<String> ids, String category) {
        this.vectorStore.delete(ids, category);
    }

    public void deleteWhere(List<Map<String, String>> whereList) {
        this.vectorStore.deleteWhere(whereList);
    }

    public void deleteWhere(List<Map<String, String>> whereList, String category) {
        this.vectorStore.deleteWhere(whereList, category);
    }

    public void deleteCollection(String category) {
        this.vectorStore.deleteCollection(category);
    }

    public List<IndexSearchData> searchByIds(List<String> ids, String category) {
        List<IndexRecord> fetch = fetch(ids, category);
        if (fetch == null) {
            return Collections.emptyList();
        }
        List<IndexSearchData> indexSearchDataList = fetch.stream()
                .map(this::toIndexSearchData)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return processFutureResults(indexSearchDataList, category);
    }

    public List<IndexSearchData> search(ChatCompletionRequest request) {
        String lastMessage = ChatCompletionUtil.getLastMessage(request);
        List<IndexSearchData> indexSearchDataList = search(lastMessage, request.getCategory());
        return indexSearchDataList;
    }

    public List<IndexSearchData> searchByContext(ChatCompletionRequest request) {
        return searchByContext(request, null);
    }

    public List<IndexSearchData> searchByContext(ChatCompletionRequest request, Map<String, Object> where) {
        List<ChatMessage> messages = request.getMessages();
        IntentResult intentResult = intentService.detectIntent(request, where);
        if (intentResult.getIndexSearchDataList() != null) {
            return intentResult.getIndexSearchDataList();
        }
        String question = null;
        if (intentResult.getStatus() != null && intentResult.getStatus().equals(IntentStatusEnum.CONTINUE.getName())) {
            if (intentResult.getContinuedIndex() != null) {
                ChatMessage chatMessage = messages.get(intentResult.getContinuedIndex());
                String content = chatMessage.getContent();
                String[] split = content.split("[， ,.。！!?？]");
                String source = Arrays.stream(split).filter(StoppingWordUtil::containsStoppingWorlds).findAny().orElse("");
                if (StrUtil.isBlank(source)) {
                    source = content;
                }
                if (chatMessage.getRole().equals(LagiGlobal.LLM_ROLE_SYSTEM)) {
                    source = "";
                }
                question = source + ChatCompletionUtil.getLastMessage(request);
            } else {
                List<ChatMessage> userMessages = messages.stream().filter(m -> m.getRole().equals("user")).collect(Collectors.toList());
                if (userMessages.size() > 1) {
                    question = userMessages.get(userMessages.size() - 2).getContent().trim();
                }
            }
        }
        if (question == null) {
            question = ChatCompletionUtil.getLastMessage(request);
        }
        return search(question, request.getCategory());
    }

    public List<IndexSearchData> search(String question, String category) {
        return search(question, new HashMap<>(), category);
    }

    public List<IndexSearchData> search(String question, Map<String, Object> where, String category) {
        int similarity_top_k = vectorStore.getConfig().getSimilarityTopK();
        double similarity_cutoff = vectorStore.getConfig().getSimilarityCutoff();
        category = ObjectUtils.defaultIfNull(category, vectorStore.getConfig().getDefaultCategory());
        List<IndexSearchData> indexSearchDataList = search(question, similarity_top_k, similarity_cutoff, where, category);
        Set<String> esIds = bigdataService.getIds(question, category);
        if (esIds != null && !esIds.isEmpty()) {
            Set<String> indexIds = indexSearchDataList.stream().map(IndexSearchData::getId).collect(Collectors.toSet());
            indexIds.retainAll(esIds);
            indexSearchDataList = indexSearchDataList.stream()
                    .filter(indexSearchData -> indexIds.contains(indexSearchData.getId()))
                    .collect(Collectors.toList());
        }
        return processFutureResults(indexSearchDataList, category);
    }

    private List<IndexSearchData> processFutureResults(List<IndexSearchData> indexSearchDataList, String category) {
        List<Future<List<IndexSearchData>>> futureResultList = indexSearchDataList.stream()
                .map(indexSearchData -> executor.submit(() -> extendIndexSearchData(indexSearchData, category)))
                .collect(Collectors.toList());
        
        Set<String> seenTexts = ConcurrentHashMap.newKeySet();
        
        return futureResultList.stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                })
                .filter(dataList -> !dataList.isEmpty())
                .map(dataList -> dataList.stream()
                        .filter(data -> seenTexts.add(data.getText()))
                        .collect(Collectors.toList()))
                .filter(filteredList -> !filteredList.isEmpty())
                .map(this::mergeIndexSearchData)
                .collect(Collectors.toList());
    }

    private IndexSearchData mergeIndexSearchData(List<IndexSearchData> indexSearchDataList) {
        IndexSearchData mergedIndexSearchData = new IndexSearchData();
        BeanUtil.copyProperties(indexSearchDataList.get(0), mergedIndexSearchData);
        String splitChar = "";
        if (mergedIndexSearchData.getFilename() != null && mergedIndexSearchData.getFilename().size() == 1
                && mergedIndexSearchData.getFilename().get(0).isEmpty()) {
            splitChar = "\n";
        }
        StringBuilder sb = new StringBuilder(mergedIndexSearchData.getText());
        for (int i = 1; i < indexSearchDataList.size(); i++) {
            sb.append(splitChar).append(indexSearchDataList.get(i).getText());
        }
        mergedIndexSearchData.setText(sb.toString());
        return mergedIndexSearchData;
    }

    private List<IndexSearchData> extendIndexSearchData(IndexSearchData indexSearchData, String category) {
        List<IndexSearchData> extendedList = vectorCache.getFromVectorLinkCache(indexSearchData.getId());
        if (extendedList == null) {
            extendedList = extendText(indexSearchData, category);
            vectorCache.putToVectorLinkCache(indexSearchData.getId(), extendedList);
        }
        return extendedList;
    }

    public List<IndexSearchData> search(String question, int similarity_top_k, double similarity_cutoff,
                                        Map<String, Object> where, String category) {
        List<IndexSearchData> result = new ArrayList<>();
        QueryCondition queryCondition = new QueryCondition();
        queryCondition.setText(question);
        queryCondition.setN(similarity_top_k);
        queryCondition.setWhere(where);
        queryCondition.setCategory(category);
        List<IndexRecord> indexRecords = this.query(queryCondition);
        for (IndexRecord indexRecord : indexRecords) {
            if (indexRecord.getDistance() > similarity_cutoff) {
                continue;
            }
            IndexSearchData indexSearchData = toIndexSearchData(indexRecord);
            if (indexSearchData != null) {
                result.add(indexSearchData);
            }
        }
        return result;
    }

    public IndexSearchData toIndexSearchData(IndexRecord indexRecord) {
        if (indexRecord == null) {
            return null;
        }
        IndexSearchData indexSearchData = new IndexSearchData();
        indexSearchData.setId(indexRecord.getId());
        indexSearchData.setText(indexRecord.getDocument());
        indexSearchData.setCategory((String) indexRecord.getMetadata().get("category"));
        indexSearchData.setLevel((String) indexRecord.getMetadata().get("level"));
        indexSearchData.setFileId((String) indexRecord.getMetadata().get("file_id"));
        String filename = (String) indexRecord.getMetadata().get("filename");
        Long seq = indexRecord.getMetadata().get("seq") == null ? 0L : Long.parseLong((String) indexRecord.getMetadata().get("seq"));
        indexSearchData.setSeq(seq);
        if (filename != null) {
            indexSearchData.setFilename(Collections.singletonList(filename));
        }
        if (indexRecord.getMetadata().get("filepath") != null) {
            indexSearchData.setFilepath(Collections.singletonList((String) indexRecord.getMetadata().get("filepath")));
        }
        indexSearchData.setImage((String) indexRecord.getMetadata().get("image"));
        indexSearchData.setDistance(indexRecord.getDistance());
        indexSearchData.setParentId((String) indexRecord.getMetadata().get("parent_id"));
        indexSearchData.setSource((String) indexRecord.getMetadata().get("source"));
        return indexSearchData;
    }

    public IndexSearchData getParentIndex(String parentId) {
        return getParentIndex(parentId, vectorStore.getConfig().getDefaultCategory());
    }

    public IndexSearchData getParentIndex(String parentId, String category) {
        if (parentId == null) {
            return null;
        }
        IndexSearchData indexSearchData = vectorCache.getFromParentElementCache(parentId);
        if (indexSearchData == null) {
            indexSearchData = toIndexSearchData(this.fetch(parentId, category));
            vectorCache.putToParentElementCache(parentId, indexSearchData);
        }
        return indexSearchData;
    }

    public List<IndexSearchData> getChildIndex(String parentId, String category) {
        return getChildIndex(parentId, category, DEFAULT_SOURCES);
    }

    public List<IndexSearchData> getChildIndex(String parentId, String category, List<String> sourceList) {
        List<IndexSearchData> result = Collections.emptyList();
        if (parentId == null) {
            return result;
        }
        result = vectorCache.getFromChildElementCache(parentId);
        if (result != null) {
            return result;
        }
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("parent_id", parentId);
        Map<String, Object> inMap = new HashMap<>();
        inMap.put("$in", sourceList);
        conditions.put("source", inMap);
        Map<String, Object> where = buildAndQueryCondition(conditions);
        GetEmbedding getEmbedding = GetEmbedding.builder()
                .category(category)
                .where(where)
                .build();
        List<IndexRecord> indexRecords = this.get(getEmbedding);
        if (indexRecords != null && !indexRecords.isEmpty()) {
            result = indexRecords.stream()
                    .map(this::toIndexSearchData)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            vectorCache.putToChildElementCache(parentId, result);
        }
        return result;
    }

    public List<IndexSearchData> extendText(IndexSearchData data, String category) {
        int parentDepth = vectorStore.getConfig().getParentDepth();
        int childDepth = vectorStore.getConfig().getChildDepth();
        return extendText(parentDepth, childDepth, data, category);
    }

    public List<IndexSearchData> extendText(int parentDepth, int childDepth, IndexSearchData data, String category) {
        List<IndexSearchData> resultList = new ArrayList<>();
        String parentId = data.getParentId();
        IndexSearchData originalDocData = data;

        if (FILE_CHUNK_SOURCE_LLM.equals(data.getSource())) {
            originalDocData = getParentIndex(parentId, category);
            parentId = originalDocData.getParentId();
        }

        List<IndexSearchData> parentNodes = new ArrayList<>();
        int parentCount = 0;
        int i = 0;
        while (i < parentDepth) {
            IndexSearchData parentData = getParentIndex(parentId, category);
            if (parentData != null) {
                if (isRawData(parentData)) {
                    parentNodes.add(0, parentData);
                    parentCount++;
                    i++;
                }
                parentId = parentData.getParentId();
            } else {
                break;
            }
        }
        if (parentCount < parentDepth) {
            childDepth = childDepth + parentDepth - parentCount;
        }

        resultList.addAll(parentNodes);
        resultList.add(originalDocData);

        parentId = originalDocData.getId();
        int j = 0;
        while (j < childDepth) {
            List<IndexSearchData> childDataList = getChildIndex(parentId, category);
            if (childDataList != null && !childDataList.isEmpty()) {
                IndexSearchData childData = childDataList.get(0);
                if (isRawData(childData)) {
                    resultList.add(childData);
                    j++;
                }
                parentId = childData.getId();
            } else {
                break;
            }
        }
        for (IndexSearchData indexSearchData : resultList) {
            BeanUtil.copyProperties(data, indexSearchData, "text");
        }
        return resultList;
    }

    private boolean isRawData(IndexSearchData data) {
        return FILE_CHUNK_SOURCE_FILE.equals(data.getSource()) ||
                FILE_CHUNK_SOURCE_QA.equals(data.getSource()) || data.getSource() == null;
    }

    public List<String> getImageFiles(IndexSearchData indexData) {
        List<String> imageList = null;

        if (indexData.getImage() != null && !indexData.getImage().isEmpty()) {
            imageList = new ArrayList<>();
            List<JsonObject> imageObjectList = gson.fromJson(indexData.getImage(), new TypeToken<List<JsonObject>>() {
            }.getType());
            for (JsonObject image : imageObjectList) {
                String url = image.get("path").getAsString();
                imageList.add(url);
            }
        }
        return imageList;
    }

    public List<VectorCollection> listCollections() {
        return this.vectorStore.listCollections();
    }

    public List<IndexRecord> get(GetEmbedding getEmbedding) {
        return this.vectorStore.get(getEmbedding);
    }

    public void add(AddEmbedding addEmbedding) {
        this.vectorStore.add(addEmbedding);
    }

    public void update(UpdateEmbedding updateEmbedding) {
        this.vectorStore.update(updateEmbedding);
    }

    public void delete(DeleteEmbedding deleteEmbedding) {
        this.vectorStore.delete(deleteEmbedding);
    }

    public void chunkAdd(AddChunkEmbedding addChunkEmbedding) {
        if (addChunkEmbedding == null || addChunkEmbedding.getData() == null || addChunkEmbedding.getData().isEmpty()) {
            return;
        }
        String category = addChunkEmbedding.getCategory();
        if (category == null) {
            category = vectorStore.getConfig().getDefaultCategory();
        }
        List<AddEmbedding.AddEmbeddingData> addDataList = getAddEmbeddingData(addChunkEmbedding);
        for (AddEmbedding.AddEmbeddingData addData : addDataList) {
            updateChildNodesParentId(addData, category);
        }
        AddEmbedding addEmbedding = AddEmbedding.builder()
                .category(category)
                .data(addDataList)
                .build();
        this.add(addEmbedding);
    }

    private List<AddEmbedding.AddEmbeddingData> getAddEmbeddingData(AddChunkEmbedding addChunkEmbedding) {
        List<AddEmbedding.AddEmbeddingData> addDataList = new ArrayList<>();
        for (AddChunkEmbedding.AddChunkEmbeddingData chunkData : addChunkEmbedding.getData()) {
            Map<String, Object> metadata = new HashMap<>();
            if (chunkData.getExtraMetadatas() != null) {
                metadata.putAll(chunkData.getExtraMetadatas());
            }
            if (chunkData.getParentId() != null) {
                metadata.put("parent_id", chunkData.getParentId());
            } else {
                metadata.put("parent_id", "");
            }
            if (chunkData.getFileId() == null) {
                throw new RuntimeException("File id is required");
            }
            metadata.put("file_id", chunkData.getFileId());
            metadata.put("source", FILE_CHUNK_SOURCE_FILE);
            AddEmbedding.AddEmbeddingData addData = new AddEmbedding.AddEmbeddingData();
            if (chunkData.getId() != null) {
                addData.setId(chunkData.getId());
            } else {
                addData.setId(FileUtils.generateId());
            }
            addData.setDocument(chunkData.getDocument());
            addData.setMetadata(metadata);
            addDataList.add(addData);
        }
        return addDataList;
    }

    private Map<String, Object> buildAndQueryCondition(Map<String, Object> conditions) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> addList = new ArrayList<>();
        for (String key : conditions.keySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put(key, conditions.get(key));
            addList.add(item);
        }
        result.put("$and", addList);
        return result;
    }

    private void updateChildNodesParentId(AddEmbedding.AddEmbeddingData addData, String category) {
        String oldParentId = (String) addData.getMetadata().get("parent_id");
        String newParentId = addData.getId();
        String fileId = (String) addData.getMetadata().get("file_id");
        vectorCache.removeFromAllCache(oldParentId);
        try {
            Map<String, Object> conditions = new HashMap<>();
            conditions.put("parent_id", oldParentId);
            conditions.put("file_id", fileId);
            conditions.put("source", FILE_CHUNK_SOURCE_FILE);
            Map<String, Object> where = buildAndQueryCondition(conditions);
            List<String> includeList = new ArrayList<>();
            includeList.add(VectorStoreConstant.IncludeFields.DOCUMENTS);
            includeList.add(VectorStoreConstant.IncludeFields.EMBEDDINGS);
            GetEmbedding getEmbedding = GetEmbedding.builder()
                    .category(category)
                    .where(where)
                    .include(includeList)
                    .build();
            List<IndexRecord> childNodes = this.get(getEmbedding);
            if (childNodes != null && !childNodes.isEmpty()) {
                List<UpdateEmbedding.UpdateEmbeddingData> updateDataList = new ArrayList<>();
                for (IndexRecord childNode : childNodes) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("parent_id", newParentId);
                    UpdateEmbedding.UpdateEmbeddingData updateData = new UpdateEmbedding.UpdateEmbeddingData();
                    updateData.setId(childNode.getId());
                    updateData.setMetadata(metadata);
                    updateData.setDocument(childNode.getDocument());
                    updateData.setEmbedding(childNode.getEmbeddings());
                    updateDataList.add(updateData);
                    vectorCache.removeFromAllCache(childNode.getId());
                }
                UpdateEmbedding updateEmbedding = UpdateEmbedding.builder()
                        .category(category)
                        .data(updateDataList)
                        .build();
                this.update(updateEmbedding);
            }
        } catch (Exception e) {
            log.error("Failed to update child nodes parent_id from {} to {}", oldParentId, newParentId, e);
            throw new RuntimeException("Failed to update child nodes parent_id from " + oldParentId + " to " + newParentId, e);
        }
    }

    public void chunkUpdate(UpdateChunkEmbedding updateChunkEmbedding) {
        if (updateChunkEmbedding == null || updateChunkEmbedding.getData() == null || updateChunkEmbedding.getData().isEmpty()) {
            return;
        }
        String category = updateChunkEmbedding.getCategory();
        if (category == null) {
            category = vectorStore.getConfig().getDefaultCategory();
        }
        List<UpdateEmbedding.UpdateEmbeddingData> updateDataList = getUpdateEmbeddingData(updateChunkEmbedding);
        UpdateEmbedding updateEmbedding = UpdateEmbedding.builder()
                .category(category)
                .data(updateDataList)
                .build();
        this.update(updateEmbedding);
    }

    private List<UpdateEmbedding.UpdateEmbeddingData> getUpdateEmbeddingData(UpdateChunkEmbedding updateChunkEmbedding) {
        List<UpdateEmbedding.UpdateEmbeddingData> updateDataList = new ArrayList<>();
        for (UpdateChunkEmbedding.UpdateChunkEmbeddingData chunkData : updateChunkEmbedding.getData()) {
            UpdateEmbedding.UpdateEmbeddingData updateData = new UpdateEmbedding.UpdateEmbeddingData();
            if (chunkData.getId() == null) {
                throw new RuntimeException("Id is required for update operation");
            }
            updateData.setId(chunkData.getId());
            updateData.setDocument(chunkData.getDocument());
            updateDataList.add(updateData);
            IndexSearchData parentIndex = getParentIndex(chunkData.getId(), updateChunkEmbedding.getCategory());
            List<IndexSearchData> childIndexList = getChildIndex(chunkData.getId(), updateChunkEmbedding.getCategory());
            vectorCache.removeFromAllCache(chunkData.getId());
            vectorCache.removeFromAllCache(parentIndex.getId());
            for (IndexSearchData indexSearchData : childIndexList) {
                vectorCache.removeFromAllCache(indexSearchData.getId());
            }
        }
        return updateDataList;
    }

    public void chunkDelete(DeleteChunkEmbedding deleteChunkEmbedding) {
        if (deleteChunkEmbedding == null) {
            return;
        }
        String category = deleteChunkEmbedding.getCategory();
        if (category == null) {
            category = vectorStore.getConfig().getDefaultCategory();
        }

        if (deleteChunkEmbedding.getIds() != null && !deleteChunkEmbedding.getIds().isEmpty()) {
            updateChildNodesParentIdOnDelete(deleteChunkEmbedding.getIds(), category);
        }

        DeleteEmbedding deleteEmbedding = DeleteEmbedding.builder()
                .category(category)
                .ids(deleteChunkEmbedding.getIds())
                .build();
        this.delete(deleteEmbedding);
    }

    private void updateChildNodesParentIdOnDelete(List<String> deletedIds, String category) {
        try {
            for (String deletedId : deletedIds) {
                IndexSearchData parentIndex = getParentIndex(deletedId, category);
                String newParentId = parentIndex.getParentId();
                deleteChildNodesByLlm(deletedId, category);
                updateChildNodesParentId(deletedId, category, newParentId);
            }
        } catch (Exception e) {
            log.error("Failed to update child nodes parent_id on delete", e);
            throw new RuntimeException("Failed to update child nodes parent_id on delete", e);
        }
    }

    private void updateChildNodesParentId(String parentId, String category, String newParentId) {
        vectorCache.removeFromAllCache(parentId);
        vectorCache.removeFromAllCache(newParentId);
        try {
            Map<String, Object> conditions = new HashMap<>();
            conditions.put("parent_id", parentId);
            conditions.put("source", VectorStoreConstant.FileChunkSource.FILE_CHUNK_SOURCE_FILE);
            Map<String, Object> where = buildAndQueryCondition(conditions);
            List<String> includeList = new ArrayList<>();
            includeList.add(VectorStoreConstant.IncludeFields.DOCUMENTS);
            includeList.add(VectorStoreConstant.IncludeFields.EMBEDDINGS);
            GetEmbedding getEmbedding = GetEmbedding.builder()
                    .category(category)
                    .where(where)
                    .include(includeList)
                    .build();
            List<IndexRecord> childNodes = this.get(getEmbedding);
            if (childNodes != null && !childNodes.isEmpty()) {
                List<UpdateEmbedding.UpdateEmbeddingData> updateDataList = new ArrayList<>();
                for (IndexRecord childNode : childNodes) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("parent_id", newParentId);
                    UpdateEmbedding.UpdateEmbeddingData childUpdateData = new UpdateEmbedding.UpdateEmbeddingData();
                    childUpdateData.setId(childNode.getId());
                    childUpdateData.setMetadata(metadata);
                    childUpdateData.setDocument(childNode.getDocument());
                    childUpdateData.setEmbedding(childNode.getEmbeddings());
                    updateDataList.add(childUpdateData);
                    vectorCache.removeFromAllCache(childNode.getId());
                }
                UpdateEmbedding updateEmbedding = UpdateEmbedding.builder()
                        .category(category)
                        .data(updateDataList)
                        .build();
                this.update(updateEmbedding);
                log.info("Updated {} child nodes with source {} to set parent_id to {}",
                        childNodes.size(), VectorStoreConstant.FileChunkSource.FILE_CHUNK_SOURCE_FILE, newParentId);
            }
        } catch (Exception e) {
            log.error("Failed to update child nodes parent_id from {} to {} with source {}",
                    parentId, newParentId, VectorStoreConstant.FileChunkSource.FILE_CHUNK_SOURCE_FILE, e);
            throw new RuntimeException("Failed to update child nodes parent_id", e);
        }
    }

    private void deleteChildNodesByLlm(String parentId, String category) {
        try {
            Map<String, Object> conditions = new HashMap<>();
            conditions.put("parent_id", parentId);
            conditions.put("source", VectorStoreConstant.FileChunkSource.FILE_CHUNK_SOURCE_LLM);
            Map<String, Object> where = buildAndQueryCondition(conditions);
            List<String> includeList = new ArrayList<>();
            includeList.add(VectorStoreConstant.IncludeFields.DOCUMENTS);
            includeList.add(VectorStoreConstant.IncludeFields.EMBEDDINGS);
            GetEmbedding getEmbedding = GetEmbedding.builder()
                    .category(category)
                    .where(where)
                    .include(includeList)
                    .build();
            List<IndexRecord> childNodes = this.get(getEmbedding);
            if (childNodes != null && !childNodes.isEmpty()) {
                List<String> childIds = childNodes.stream()
                        .map(IndexRecord::getId)
                        .collect(Collectors.toList());
                DeleteEmbedding deleteChildren = DeleteEmbedding.builder()
                        .category(category)
                        .ids(childIds)
                        .build();
                this.delete(deleteChildren);
                log.info("Deleted {} child nodes with source {} for parent {}",
                        childNodes.size(), VectorStoreConstant.FileChunkSource.FILE_CHUNK_SOURCE_LLM, parentId);
            }
        } catch (Exception e) {
            log.error("Failed to delete child nodes with source {} for parent {}",
                    VectorStoreConstant.FileChunkSource.FILE_CHUNK_SOURCE_LLM, parentId, e);
            throw new RuntimeException("Failed to delete child nodes", e);
        }
    }
}
