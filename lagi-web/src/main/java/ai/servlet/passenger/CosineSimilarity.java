package ai.servlet.passenger;

public final class CosineSimilarity {

	/**
	 * 计算两个向量的余弦相似度
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
			na  += ai * ai;
			nb  += bi * bi;
		}
		double denom = Math.sqrt(na) * Math.sqrt(nb);
		if (denom == 0.0) return 0.0;
		return dot / denom;
	}

	/**
	 * 判断两个特征向量是否匹配（相似度大于阈值）
	 * @param feature1 特征向量1
	 * @param feature2 特征向量2
	 * @param threshold 相似度阈值，默认0.5
	 * @return true表示匹配，false表示不匹配
	 */
	public static boolean isFeatureMatch(double[] feature1, double[] feature2, double threshold) {
		if (feature1 == null || feature2 == null) return false;
		if (feature1.length != feature2.length) return false;
		
		double similarity = cosine(feature1, feature2);
		return similarity > threshold;
	}

	/**
	 * 判断两个特征向量是否匹配（使用默认阈值0.5）
	 * @param feature1 特征向量1
	 * @param feature2 特征向量2
	 * @return true表示匹配，false表示不匹配
	 */
	public static boolean isFeatureMatch(double[] feature1, double[] feature2) {
		return isFeatureMatch(feature1, feature2, 0.5);
	}

	/**
	 * 从字符串特征向量解析为double数组
	 * @param featureStr 特征向量字符串
	 * @return double数组
	 */
	public static double[] parseFeatureVector(String featureStr) {
		if (featureStr == null || featureStr.trim().isEmpty()) {
			return new double[0];
		}
		
		try {
			// 假设特征向量是逗号分隔的数值字符串
			String[] parts = featureStr.split(",");
			double[] result = new double[parts.length];
			
			for (int i = 0; i < parts.length; i++) {
				result[i] = Double.parseDouble(parts[i].trim());
			}
			
			return result;
		} catch (NumberFormatException e) {
			// 如果解析失败，返回空数组
			return new double[0];
		}
	}
}
