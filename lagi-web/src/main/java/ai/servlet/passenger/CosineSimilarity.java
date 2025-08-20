package ai.servlet.passenger;

public final class CosineSimilarity {

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
}
