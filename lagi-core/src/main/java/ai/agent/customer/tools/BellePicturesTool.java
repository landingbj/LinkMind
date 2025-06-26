package ai.agent.customer.tools;

import ai.agent.customer.pojo.ToolInfo;
import ai.utils.ApiInvokeUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class BellePicturesTool extends AbstractTool {

    private static final String API_ADDRESS = "https://api.52vmy.cn/api/img/tu/girl?type=json";

    private Gson gson = new Gson();

    public BellePicturesTool() {
        init();
    }

    private void init() {
        name = "belle_pictures";
        toolInfo = ToolInfo.builder().name("belle_pictures")
//                .description("这是一个头像随机生成工具,调用这个工具可以随机生成一张头像, 因为并不需要任何参数所以生成的头像是不可控的")
                .description("这是一个头像生成的工具,调用这个工具可以随机生成一张头像")
                .args(Lists.newArrayList()).build();
        register(this);
    }

    private String search() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        String post = ApiInvokeUtil.post(API_ADDRESS, headers, "", 30, TimeUnit.SECONDS);
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
         Map<String, Object> map = gson.fromJson(post, type);
        if (map == null) {
            return "图片生成失败！";
        }
//        Map<String, Object> data = gson.fromJson(dataStr, type);
        return StrUtil.format("{\"图片生成成功,地址为\": \"{}\"}",map.get("url"));
    }
    @Override
    public String apply(Map<String, Object> stringObjectMap) {
        return search();
    }

    public static void main(String[] args) {
        BellePicturesTool tool = new BellePicturesTool();
        String result = tool.search();
        System.out.println(result);
    }
}
