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
 * Verifies that Elastic Dev Services do not start when an OTLP exporter endpoint
 * is explicitly configured.
 */
@QuarkusTest
@TestProfile(ElasticExplicitEndpointTest.ExplicitEndpointProfile.class)
@DisabledOnOs(OS.WINDOWS)
public class ElasticExplicitEndpointTest {

    @Inject
    Config config;

    @Test
    public void testDevServicesNotStartedWhenEndpointExplicitlyConfigured() {
        Optional<String> esEndpoint = config.getOptionalValue("elasticsearch.endpoint", String.class);
        Assertions.assertTrue(esEndpoint.isEmpty(),
                "elasticsearch.endpoint should not be set when an OTLP endpoint is explicitly configured, but was: "
                        + esEndpoint.orElse(""));

        Optional<String> apmServerUrl = config.getOptionalValue("apm-server.url", String.class);
        Assertions.assertTrue(apmServerUrl.isEmpty(),
                "apm-server.url should not be set when an OTLP endpoint is explicitly configured, but was: "
                        + apmServerUrl.orElse(""));
    }

    public static class ExplicitEndpointProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.otel.exporter.otlp.traces.endpoint", "http://localhost:14250");
        }
    }
}
