package ai.utils;

public final class ModelNameUtil {

    private static final String ALIBABA_PREFIX = "Alibaba/";

    private ModelNameUtil() {
    }

    public static String normalizeOpenAiCompatibleModelName(String model) {
        if (model == null) {
            return null;
        }
        String normalized = model.trim();
        if (normalized.regionMatches(true, 0, ALIBABA_PREFIX, 0, ALIBABA_PREFIX.length())) {
            String modelName = normalized.substring(ALIBABA_PREFIX.length()).trim();
            if (!modelName.isEmpty()) {
                return ALIBABA_PREFIX + modelName;
            }
        }
        return normalized;
    }

    public static String resolveBillingModelName(String requestModel, String responseModel) {
        String request = trimToNull(requestModel);
        String response = trimToNull(responseModel);
        if (request != null && request.indexOf('/') >= 0 && response != null) {
            return response;
        }
        if (request != null && request.regionMatches(true, 0, ALIBABA_PREFIX, 0, ALIBABA_PREFIX.length())) {
            String modelName = request.substring(ALIBABA_PREFIX.length()).trim();
            if (!modelName.isEmpty()) {
                return modelName;
            }
        }
        return request != null ? request : response;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
