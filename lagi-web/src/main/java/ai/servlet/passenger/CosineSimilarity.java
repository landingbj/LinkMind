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

		// 1) 逗号/JSON数组形式
		if (featureStr.contains(",") || featureStr.startsWith("[") ) {
			try {
				String cleaned = featureStr.replace("[", "").replace("]", "").trim();
				if (cleaned.isEmpty()) return new double[0];
				String[] parts = cleaned.split(",");
				double[] result = new double[parts.length];
				for (int i = 0; i < parts.length; i++) {
					result[i] = Double.parseDouble(parts[i].trim());
				}
				return result;
			} catch (Exception ignore) { /* fallback to base64 */ }
		}

		// 2) base64 的 float32 向量（小端序）
		try {
			byte[] bytes = java.util.Base64.getDecoder().decode(featureStr.trim());
			if (bytes.length % 4 != 0 || bytes.length == 0) {
				return new double[0];
			}
			int n = bytes.length / 4;
			double[] vec = new double[n];
			// 默认按小端序解释 float32
			for (int i = 0; i < n; i++) {
				int base = i * 4;
				int asInt = (bytes[base] & 0xFF) |
							   ((bytes[base + 1] & 0xFF) << 8) |
							   ((bytes[base + 2] & 0xFF) << 16) |
							   ((bytes[base + 3] & 0xFF) << 24);
				float f = java.lang.Float.intBitsToFloat(asInt);
				vec[i] = (double) f;
			}
			return vec;
		} catch (Exception ignore) { }

		// 3) 解析失败返回空数组
		return new double[0];
	}
}
