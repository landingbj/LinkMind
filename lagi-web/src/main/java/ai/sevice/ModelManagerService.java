package ai.sevice;

import ai.agent.dto.ManagerModel;
import ai.dao.ManagerDao;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.utils.LlmAdapterFactory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ModelManagerService {

    public List<ILlmAdapter> getUserLlmAdapters(String userId) {
        // TODO 2025/3/4  support invoke remote service
        ManagerDao managerDao = new ManagerDao();
        List<ManagerModel> managerModels = managerDao.getManagerModels(userId, 1);
        return managerModels.stream().map(m -> {
            return LlmAdapterFactory.getLlmAdapter(m.getModelType(), m.getModelName(), 999, m.getApiKey(), m.getEndpoint());
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }
}
