package ai.manager;

import ai.llm.adapter.ILlmAdapter;

public class Html2ContentManager extends AIManager<ILlmAdapter>{
    private static final Html2ContentManager INSTANCE = new Html2ContentManager();

    private Html2ContentManager() {

    }

    public static Html2ContentManager getInstance(){
        return INSTANCE;
    }
}
