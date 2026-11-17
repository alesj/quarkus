package io.quarkus.observability.test;

import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.jboss.logging.Logger;

import io.quarkus.observability.test.utils.ElasticClient;
import io.restassured.RestAssured;

public abstract class ElasticTestBase {
    protected final Logger log = Logger.getLogger(getClass());

    private static final String SERVICE_NAME = "quarkus-integration-test-observability-elastic";

    protected abstract String elasticsearchEndpoint();

    protected void poke() {
        log.info("Testing Elastic ...");
        String response = RestAssured.get("/api/poke?f=100").body().asString();
        log.info("Response: " + response);

        ElasticClient client = new ElasticClient(elasticsearchEndpoint(), "elastic", "changeme");

        Awaitility.setDefaultPollInterval(5, TimeUnit.SECONDS);

        Awaitility.given().ignoreException(UncheckedIOException.class).await().atMost(60, TimeUnit.SECONDS).until(
                client::clusterHealth,
                h -> "green".equals(h.status) || "yellow".equals(h.status));
        Awaitility.given().ignoreException(UncheckedIOException.class).await().atMost(90, TimeUnit.SECONDS).until(
                () -> client.traces(SERVICE_NAME),
                result -> result.hits != null && result.hits.total != null && result.hits.total.value > 0);
        Awaitility.given().ignoreException(UncheckedIOException.class).await().atMost(90, TimeUnit.SECONDS).until(
                () -> client.metrics(SERVICE_NAME),
                result -> result.hits != null && result.hits.total != null && result.hits.total.value > 0);
    }
}
