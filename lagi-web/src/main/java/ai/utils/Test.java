package ai.utils;

import ai.keywords.SubstitutionUtil;

import java.util.List;

public class Test {
    public static void main(String[] args) {
        String text = "帮我查一下ip为127.0.0.1的归属地在哪？";
        List<String> vector = SubstitutionUtil.lookupNodeStr(text);
        System.out.println(vector);
    }
}
