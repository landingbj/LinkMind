package ai.utils;

import ai.common.pojo.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyValidationResponseTest {

    @Test
    void acceptsOnlyAnExplicitSaasSuccessValidationResponse() {
        assertTrue(ApiKeyValidationResponse.isSuccessfulLandingValidation(Response.builder().status("success").build()));
        assertTrue(ApiKeyValidationResponse.isSuccessfulLandingValidation(Response.builder().status("SUCCESS").build()));
        assertFalse(ApiKeyValidationResponse.isSuccessfulLandingValidation(Response.builder().status("not_found").build()));
        assertFalse(ApiKeyValidationResponse.isSuccessfulLandingValidation(Response.builder().status("failed").build()));
        assertFalse(ApiKeyValidationResponse.isSuccessfulLandingValidation(Response.builder().status("unexpected").build()));
        assertFalse(ApiKeyValidationResponse.isSuccessfulLandingValidation(null));
    }
}
