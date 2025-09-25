package ai.servlet;

import java.util.*;

import ai.common.pojo.Backend;
import ai.common.pojo.Configuration;
import ai.common.pojo.VectorStoreConfig;
import ai.servlet.annotation.Get;
import ai.servlet.annotation.Param;
import ai.servlet.dto.Prompt;
import ai.utils.MigrateGlobal;
import ai.vector.VectorStoreService;

public class PageInfoServlet extends RestfulServlet{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public static List<Prompt> prompts = new ArrayList<>(10);
	public static List<Prompt> agentPrompts = new ArrayList<>(10);
	private static final Configuration config = MigrateGlobal.config;
	private static final Map<String, Integer> navMap = new HashMap<>(20);

	static{
		prompts.add(new Prompt("内容撰写","请为一位大学生写一篇产品助理岗位的实习报告", null, null));
		prompts.add(new Prompt("内容改写","请帮我把这段内容改写：“天青色等烟雨，而我在等你”", null, null));
		prompts.add(new Prompt("旅游规划","请帮我制定一个新疆7天游，预算8888元的旅游攻略", null, null));
		prompts.add(new Prompt("产品推荐","请给我推荐几辆预算15万以内的新能源小汽车，并说明它们的优缺点", null, null));
		prompts.add(new Prompt("智能百科","兔子的耳朵为什么那么长？", null, null));
		prompts.add(new Prompt("家常菜谱","请告诉我红烧猪蹄的做法和注意事项", null, null));
		prompts.add(new Prompt("社恐交际","应酬时想以茶代酒该怎么高情商发言？", null, null));
		prompts.add(new Prompt("“社恐”交际","如何不带脏字的骂人？", null, null));
		agentPrompts.add(new Prompt("天气助手","今天北京天气如何", "weather", 1010108));
		agentPrompts.add(new Prompt("油价助手","湖北省的最近的油价如何", "oil", 1010109));
//		agentPrompts.add(new Prompt("历史今日","历史上的今天有什么事件发生", "history", -14));
//		agentPrompts.add(new Prompt("有道翻译", "将我要吃面条翻译为泰文", "youdao", -15));
		VectorStoreConfig vectorStoreConfig = new VectorStoreService().getVectorStoreConfig();
		// 智能问答 配置检测

		long llmCount = config.getLLM().getBackends().stream().filter(Backend::getEnable).count();
		navMap.put("znwd", llmCount > 0 ? 1 :0);
		navMap.put("wbsc", llmCount > 0 ? 1 :0);
		long count = config.getASR().getBackends().stream().filter(Backend::getEnable).count();
		navMap.put("yysb", count > 0 ? 1 :0);
		count = config.getTTS().getBackends().stream().filter(Backend::getEnable).count();
		navMap.put("qrqs", count > 0 ? 1 :0);
		count = config.getImageCaptioning().getBackends().stream().filter(Backend::getEnable).count();
		navMap.put("ktsh", count > 0 ? 1 :0);
		count = config.getImageEnhance().getBackends().stream().filter(Backend::getEnable).count();
		navMap.put("hzzq", count > 0 ? 1 :0);
		count = config.getImageGeneration().getBackends().stream().filter(Backend::getEnable).count();
		navMap.put("tpsc", count > 0 ? 1 :0);
		count = config.getVideoTrack().getBackends().stream().filter(Backend::getEnable).count();
		navMap.put("spzz", count > 0 ? 1 :0);
		count = config.getVideoEnhance().getBackends().stream().filter(Backend::getEnable).count();
		navMap.put("spzq", count > 0 ? 1 :0);
		count = config.getVideoGeneration().getBackends().stream().filter(Backend::getEnable).count();
		navMap.put("spsc", count > 0 ? 1 :0);
		navMap.put("kjsx", vectorStoreConfig != null  ? 1 :0);
		navMap.put("zlsc", 1);

		navMap.put("twhp", llmCount > 0 ? 1 :0);
	}
	
	public static List<Prompt> randomPrompts(int size) {
		List<Prompt> aRes = new ArrayList<>(agentPrompts);
		Collections.shuffle(aRes);
		size = Math.min(size, prompts.size());
		List<Prompt> prompts1 = aRes.subList(0, 2);
		List<Prompt> res = new ArrayList<>(prompts);
		Collections.shuffle(res);
		List<Prompt> prompts2 = res.subList(0, size - 2);
		List<Prompt> finalRes = new ArrayList<>(size);
		finalRes.addAll(prompts1);
		finalRes.addAll(prompts2);
		return finalRes;
	}
	
	@Get("getPrompts")
	public List<Prompt> getPromptList(@Param("size") Integer size) {
		return randomPrompts(size);
	}

	@Get("getNavStatus")
	public Map<String, Integer> getNavStatus() {
		return navMap;
	}
	
}
