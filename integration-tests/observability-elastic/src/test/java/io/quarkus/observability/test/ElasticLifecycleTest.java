package io.quarkus.observability.test;

import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkus.observability.devresource.elastic.ElasticResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@QuarkusTestResource(value = ElasticResource.class, restrictToAnnotatedClass = true)
@TestProfile(ElasticLifecycleTest.TestResourceTestProfileOff.class)
@DisabledOnOs(OS.WINDOWS)
public class ElasticLifecycleTest extends ElasticTestBase {

    @ConfigProperty(name = "elasticsearch.endpoint")
    String elasticsearchEndpoint;

    @Override
    protected String elasticsearchEndpoint() {
        return elasticsearchEndpoint;
    }

    @Test
    public void testTracing() {
        poke();
    }

    public static class TestResourceTestProfileOff implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.otel.exporter.otlp.endpoint", "http://${apm-server.url}",
                    "quarkus.observability.dev-resources", "false",
                    "quarkus.observability.enabled", "false");
        }
    }
}
