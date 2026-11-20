package ai.manager;

import ai.common.ModelService;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.adapter.impl.ProxyLlmAdapter;
import ai.llm.routing.BringYourOwnKeyRouter;
import java.util.ArrayList;
import java.util.List;

public class LlmManager extends AIManager<ILlmAdapter>{

    private static final LlmManager  INSTANCE = new LlmManager();

    private final List<ILlmAdapter> ordered = new ArrayList<>();

    private LlmManager() {

    }

    public static LlmManager getInstance(){
        return INSTANCE;
    }

    @Override
    public synchronized void register(String key, ILlmAdapter adapter) {
        if (adapter instanceof ModelService && ((ModelService) adapter).getModel() == null) {
            ((ModelService) adapter).setModel(key);
        }
        addProxy(key, new ProxyLlmAdapter(adapter));
    }

    private void addProxy(String key, ProxyLlmAdapter proxy) {
        ordered.add(proxy);
        aiMap.putIfAbsent(key, proxy);
        aiMap.putIfAbsent(BringYourOwnKeyRouter.publicId(proxy, key), proxy);
    }

    /** Publish only after discovery has finished for every platform. */
    public synchronized void replaceModels(List<ModelService> models) {
        List<ProxyLlmAdapter> next = new ArrayList<>(models.size());
        for (ModelService model : models) next.add(new ProxyLlmAdapter((ILlmAdapter) model));
        clear();
        for (ProxyLlmAdapter proxy : next) addProxy(proxy.getModel(), proxy);
    }

    @Override
    public synchronized ILlmAdapter getAdapter(String key) {
        return super.getAdapter(key);
    }

    @Override
    public synchronized List<ILlmAdapter> getAllAdapters() {
        return new ArrayList<>(ordered);
    }

    @Override
    public synchronized void clear() {
        ordered.clear();
        super.clear();
    }
}
