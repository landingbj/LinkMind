package ai.servlet.passenger;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class CosineSimilarity {

    /**
     * 计算两个向量的余弦相似度
     *
     * @param a 向量a
     * @param b 向量b
     * @return 余弦相似度值，范围[-1,1]，值越大表示越相似
     */
    public static double cosine(double[] a, double[] b) {
        if (a == null || b == null) throw new IllegalArgumentException("Vectors must not be null.");
        if (a.length != b.length) throw new IllegalArgumentException("Vectors must have same length.");

        double dot = 0.0, na = 0.0, nb = 0.0;
        for (int i = 0; i < a.length; i++) {
            double ai = a[i], bi = b[i];
            dot += ai * bi;
            na += ai * ai;
            nb += bi * bi;
        }
        double denom = Math.sqrt(na) * Math.sqrt(nb);
        double result = denom == 0.0 ? 0.0 : dot / denom;
        if (Config.LOG_DEBUG || Config.PILOT_ROUTE_LOG_ENABLED) {
            System.out.println("[CosineSimilarity] 余弦相似度计算: length=" + a.length + ", result=" + result);
        }
        return result;
    }

    /**
     * 从字符串特征向量解析为double数组
     *
     * @param featureStr 特征向量字符串
     * @return double数组
     */
    public static double[] parseFeatureVector(String featureStr) {
        try {
            byte[] bytes = Base64.getDecoder().decode(featureStr.trim());
            String s = new String(bytes, StandardCharsets.UTF_8);
            s = s.replaceAll("[\\[\\]]", "");
            String[] parts = s.split(",");
            double[] vec = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                vec[i] = Double.parseDouble(parts[i]);
            }
            if (Config.LOG_DEBUG || Config.PILOT_ROUTE_LOG_ENABLED) {
                System.out.println("[CosineSimilarity] 特征向量解码: length=" + vec.length + ", values=" + formatVector(vec));
            }
            return vec;
        } catch (Exception ignore) {
        }
        // 解析失败返回空数组
        if (Config.LOG_DEBUG || Config.PILOT_ROUTE_LOG_ENABLED) {
            System.out.println("[CosineSimilarity] 特征向量解码失败，返回空数组");
        }
        return new double[0];
    }

    private static String formatVector(double[] vec) {
        if (vec == null) return "null";
        int n = vec.length;
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int limit = Math.min(n, 64);
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        if (n > limit) sb.append("... total=").append(n);
        sb.append("]");
        return sb.toString();
    }
}
