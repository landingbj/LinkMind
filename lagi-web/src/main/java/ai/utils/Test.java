package ai.utils;

import ai.keywords.SubstitutionUtil;

import java.util.List;

public class Test {
    public static void main(String[] args) {
        String text = "人工智能，指由人制造出来的机器所表现出来的智能。";
        SubstitutionUtil.lookupNodeStr(text);

        long startTime = System.currentTimeMillis();
        List<String> keywords = SubstitutionUtil.lookupNodeStr(text);
        System.out.println(keywords);
        System.out.println("That took " + (System.currentTimeMillis() - startTime) + " milliseconds");
    }
}
