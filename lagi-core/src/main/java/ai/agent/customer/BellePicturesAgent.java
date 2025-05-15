package ai.agent.customer;

import ai.agent.customer.tools.BellePicturesTool;
import ai.agent.customer.tools.FinishTool;
import ai.agent.customer.tools.ImageGenTool;
import ai.config.pojo.AgentConfig;
import com.google.common.collect.Lists;

public class BellePicturesAgent  extends CustomerAgent {
    public BellePicturesAgent(AgentConfig agentConfig) {
        super(agentConfig);
        BellePicturesTool weatherSearchTool1 = new BellePicturesTool();
        ImageGenTool imageGenTool = new ImageGenTool(agentConfig.getEndpoint());
        FinishTool finishTool = new FinishTool();
        this.toolInfoList = Lists.newArrayList(
                weatherSearchTool1.getToolInfo(),
                imageGenTool.getToolInfo(),
                finishTool.getToolInfo());
    }
}
