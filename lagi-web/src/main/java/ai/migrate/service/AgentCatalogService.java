package ai.migrate.service;

import ai.agent.Agent;
import ai.manager.AgentManager;
import ai.migrate.pojo.AgentListItem;
import ai.migrate.pojo.AgentListResponse;
import ai.migrate.pojo.ImportedAgentProfile;
import ai.migrate.service.ImportedAgentStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class AgentCatalogService {
    public AgentListResponse listAgents() {
        ImportedAgentStore.ensureExternalAgentsRegistered();
        Map<String, ImportedAgentProfile> importedProfiles = ImportedAgentStore.loadImportedProfilesByAgentId();
        List<AgentListItem> items = new ArrayList<>();
        for (Agent<?, ?> agent : AgentManager.getInstance().agents()) {
            if (agent == null || agent.getAgentConfig() == null || agent.getAgentConfig().getName() == null) {
                continue;
            }
            String agentId = agent.getAgentConfig().getName();
            ImportedAgentProfile profile = importedProfiles.get(agentId);
            AgentListItem item = new AgentListItem();
            item.setAgentId(agentId);
            item.setTitle(profile != null && !isBlank(profile.getDisplayName()) ? profile.getDisplayName() : agentId);
            item.setDescription(profile == null ? "" : safe(profile.getDescription()));
            item.setImported(profile != null);
            item.setSource(profile == null ? "yaml" : "import");
            if (profile != null) {
                item.setTemplateIssues("继续和 " + item.getTitle() + " 对话");
            }
            items.add(item);
        }
        items.sort(Comparator.comparing(AgentListItem::getImported).reversed()
                .thenComparing(AgentListItem::getTitle, String.CASE_INSENSITIVE_ORDER));
        AgentListResponse response = new AgentListResponse();
        response.setAgents(items);
        return response;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
