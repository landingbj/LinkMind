package ai.migrate.service;

import ai.config.pojo.AgentConfig;
import ai.migrate.pojo.HistoryRecord;
import ai.migrate.pojo.ImportedAgentProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImportedAgentStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void readLastHistoryReturnsAllMessagesWithoutApplyingProfileLimit() throws Exception {
        Path profilePath = tempDir.resolve("agent.yaml");
        Path historyPath = tempDir.resolve("history.jsonl");
        Files.write(profilePath, new byte[0]);
        Files.write(historyPath, (
                "{\"role\":\"user\",\"content\":\"第一条\"}\n"
                        + "{\"role\":\"assistant\",\"content\":\"第二条\"}\n"
                        + "{\"role\":\"user\",\"content\":\"第三条\"}\n"
        ).getBytes(StandardCharsets.UTF_8));

        AgentConfig config = new AgentConfig();
        config.setEndpoint(profilePath.toString());
        ImportedAgentProfile profile = new ImportedAgentProfile();
        profile.setHistoryPath("history.jsonl");
        profile.setMaxHistoryMessages(1);

        List<HistoryRecord> records = ImportedAgentStore.readLastHistory(config, profile);

        assertEquals(3, records.size());
        assertEquals("第一条", records.get(0).getContent());
        assertEquals("第三条", records.get(2).getContent());
    }
}
