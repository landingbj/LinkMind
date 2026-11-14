package ai.utils;

import ai.common.pojo.Response;

final class ApiKeyValidationResponse {
    private ApiKeyValidationResponse() {
    }

    static boolean isSuccessfulLandingValidation(Response response) {
        return response != null && "success".equalsIgnoreCase(response.getStatus());
    }
}
