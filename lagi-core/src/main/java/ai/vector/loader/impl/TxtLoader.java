package ai.vector.loader.impl;

import ai.common.pojo.FileChunkResponse;
import ai.utils.ChapterExtractorUtil;
import ai.utils.OrdinanceExtractorUtil;
import ai.utils.QaExtractorUtil;
import ai.utils.SectionExtractorUtil;
import ai.vector.FileService;
import ai.vector.loader.DocumentLoader;
import ai.vector.loader.pojo.SplitConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.Collections;
import java.util.List;

@Slf4j
public class TxtLoader implements DocumentLoader {

    @Override
    public List<FileChunkResponse.Document> load(String path, SplitConfig splitConfig) {
        File file = new File(path);
        String content = FileService.getString(file.getPath());
        try {
            if (QaExtractorUtil.extractQA(content, splitConfig.getCategory(), splitConfig.getExtra(), splitConfig.getChunkSizeForText())) {
                return Collections.emptyList();
            }
            if (ChapterExtractorUtil.isChapterDocument(content)) {
                return ChapterExtractorUtil.getChunkDocument(content, splitConfig.getChunkSizeForText());
            } else if (SectionExtractorUtil.isChapterDocument(content, splitConfig.getChunkSizeForText())) {
                return SectionExtractorUtil.getChunkDocument(content, splitConfig.getChunkSizeForText());
            } else if (OrdinanceExtractorUtil.isOrdinanceDocument(content)) {
                return OrdinanceExtractorUtil.getChunkDocument(content, splitConfig.getChunkSizeForText());
            }
            return FileService.splitContentChunks(splitConfig.getChunkSizeForText(), content);
        } catch (Exception e) {
            log.error("load txt file error", e);
        }
        return Collections.emptyList();
    }

}
