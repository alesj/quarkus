package io.quarkus.observability.test.utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.jboss.logging.Logger;

import tools.jackson.databind.ObjectMapper;

public class ElasticClient {
    private static final Logger LOG = Logger.getLogger(ElasticClient.class.getName());

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String url;
    private final String username;
    private final String password;

    public ElasticClient(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    private <T> void handle(
            String path,
            Function<HttpRequest.Builder, HttpRequest.Builder> method,
            HttpResponse.BodyHandler<T> bodyHandler,
            BiConsumer<HttpResponse<T>, T> consumer) {
        try {
            HttpResponse<T> response;
            try (HttpClient httpClient = HttpClient.newHttpClient()) {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                                                         .uri(URI.create(url + path));
                if (username != null && password != null) {
                    String credentials = username + ":" + password;
                    String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
                    builder.header("Authorization", "Basic " + encodedCredentials);
                }
                HttpRequest request = method.apply(builder).build();

                response = httpClient.send(request, bodyHandler);
            }
            int code = response.statusCode();
            if (code < 200 || code > 299) {
                throw new IllegalStateException("Bad response: " + code + " >> " + response.body());
            }
            consumer.accept(response, response.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    public ElasticClusterHealth clusterHealth() {
        AtomicReference<ElasticClusterHealth> ref = new AtomicReference<>();
        handle(
                "/_cluster/health",
                HttpRequest.Builder::GET,
                HttpResponse.BodyHandlers.ofString(),
                (r, b) -> {
                    ElasticClusterHealth health = MAPPER.readValue(b, ElasticClusterHealth.class);
                    ref.set(health);
                });
        ElasticClusterHealth health = ref.get();
        LOG.info("Cluster health: " + health);
        return health;
    }

    public ElasticSearchResult traces(String serviceName) {
        String query = "{\"query\":{\"term\":{\"service.name\":\"" + serviceName + "\"}}}";
        AtomicReference<ElasticSearchResult> ref = new AtomicReference<>();
        handle(
                "/traces-apm*/_search",
                b -> b.POST(HttpRequest.BodyPublishers.ofString(query))
                        .header("Content-Type", "application/json"),
                HttpResponse.BodyHandlers.ofString(),
                (r, b) -> {
                    ElasticSearchResult result = MAPPER.readValue(b, ElasticSearchResult.class);
                    ref.set(result);
                });
        ElasticSearchResult result = ref.get();
        LOG.info("Traces: " + result);
        return result;
    }

    public ElasticSearchResult metrics(String serviceName) {
        String query = "{\"query\":{\"term\":{\"service.name\":\"" + serviceName + "\"}}}";
        AtomicReference<ElasticSearchResult> ref = new AtomicReference<>();
        handle(
                "/metrics-apm*/_search",
                b -> b.POST(HttpRequest.BodyPublishers.ofString(query))
                        .header("Content-Type", "application/json"),
                HttpResponse.BodyHandlers.ofString(),
                (r, b) -> {
                    ElasticSearchResult result = MAPPER.readValue(b, ElasticSearchResult.class);
                    ref.set(result);
                });
        ElasticSearchResult result = ref.get();
        LOG.info("Metrics: " + result);
        return result;
    }
}
