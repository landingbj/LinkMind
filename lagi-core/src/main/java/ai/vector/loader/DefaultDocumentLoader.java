package ai.vector.loader;

import ai.common.pojo.FileChunkResponse;
import ai.vector.loader.pojo.SplitConfig;

import java.util.ArrayList;
import java.util.List;

public class DefaultDocumentLoader implements DocumentLoader, ContentLoader {

    @Override
    public List<List<FileChunkResponse.Document>> load(String path, SplitConfig splitConfig) {
        // TODO: 实现具体的文档解析逻辑
        // 这里只是一个简单的伪实现，你可以根据实际需要替换为真正的解析逻辑
        List<List<FileChunkResponse.Document>> result = new ArrayList<>();
        List<FileChunkResponse.Document> chunk = new ArrayList<>();

        FileChunkResponse.Document doc = new FileChunkResponse.Document();
        doc.setText("模拟内容：" + path);
        chunk.add(doc);

        result.add(chunk);
        return result;
    }

    @Override
    public String load(String path) {
        return "";
    }
}
