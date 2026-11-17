package io.quarkus.observability.test;

import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.config.Config;

/**
 * Verifies that Elastic Dev Services do not start in test mode
 * when {@code quarkus.observability.enabled-in-tests} is {@code false} (the default).
 */
@QuarkusTest
@TestProfile(ElasticDisabledInTestsTest.DisabledInTestsProfile.class)
@DisabledOnOs(OS.WINDOWS)
public class ElasticDisabledInTestsTest {

    @Inject
    Config config;

    @Test
    public void testDevServicesNotStarted() {
        Optional<String> esEndpoint = config.getOptionalValue("elasticsearch.endpoint", String.class);
        Assertions.assertTrue(esEndpoint.isEmpty(),
                "elasticsearch.endpoint should not be set when enabled-in-tests is false, but was: "
                        + esEndpoint.orElse(""));

        Optional<String> apmServerUrl = config.getOptionalValue("apm-server.url", String.class);
        Assertions.assertTrue(apmServerUrl.isEmpty(),
                "apm-server.url should not be set when enabled-in-tests is false, but was: "
                        + apmServerUrl.orElse(""));
    }

    public static class DisabledInTestsProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.observability.enabled-in-tests", "false");
        }
    }
}
