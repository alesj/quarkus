package io.quarkus.observability.test;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@DisabledOnOs(OS.WINDOWS)
public class ElasticServicesTest extends ElasticTestBase {

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
}
