package ai.agent.customer.tools;

import ai.agent.customer.pojo.ToolArg;
import ai.agent.customer.pojo.ToolInfo;
import com.google.common.collect.Lists;

import java.util.Map;


public class FinishTool extends AbstractTool {


    public FinishTool() {
        init();
    }


    private void init() {
        name = "finish";
        toolInfo = ToolInfo.builder().name("finish")
                .description("这是一个任务完成工具当任务完成时接受最终结果")
                .args(Lists.newArrayList(
                        ToolArg.builder().name("answer").type("string").description("输出最终的任务结果").build(),
                        ToolArg.builder().name("imageUrl").type("string").description("任务结果中的图片地址").build(),
                        ToolArg.builder().name("fileUrl").type("string").description("任务结果中文件地址").build()
                )).build();
        register(this);
    }

    @Override
    public String apply(Map<String, Object> args) {
        return (String) args.get("answer") + (String) args.get("imageUrl") + (String) args.get("fileUrl");
    }

}
