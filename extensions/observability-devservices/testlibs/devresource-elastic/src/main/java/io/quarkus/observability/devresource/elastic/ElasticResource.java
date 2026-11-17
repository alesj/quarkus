package io.quarkus.observability.devresource.elastic;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jboss.logging.Logger;

import io.quarkus.observability.common.ContainerConstants;
import io.quarkus.observability.common.config.ElasticConfig;
import io.quarkus.observability.common.config.ModulesConfiguration;
import io.quarkus.observability.devresource.Container;
import io.quarkus.observability.devresource.DevResourceLifecycleManager;
import io.quarkus.observability.devresource.ExtensionsCatalog;
import io.quarkus.observability.devresource.testcontainers.ContainerResource;
import io.quarkus.observability.testcontainers.ElasticContainer;
import io.smallrye.config.Config;

public class ElasticResource extends ContainerResource<ElasticContainer, ElasticConfig> {

    private static final Logger log = Logger.getLogger(ElasticResource.class.getName());

    private ExtensionsCatalog catalog;
    private ElasticConfig config;

    @Override
    public ElasticConfig config(ModulesConfiguration configuration) {
        ElasticConfig config = configuration.elastic();
        this.config = config;
        return config;
    }

    @Override
    public ElasticConfig config(ModulesConfiguration configuration, ExtensionsCatalog catalog) {
        this.catalog = catalog;
        return config(configuration);
    }

    @Override
    public Container<ElasticConfig> container(ElasticConfig config, ModulesConfiguration root) {
        return set(new ElasticContainer(config));
    }

    @Override
    public boolean enable() {
        String explicitEndpoint = findExplicitlyConfiguredOtlpEndpoint();
        if (explicitEndpoint == null) {
            return true;
        }

        List<String> configuredPorts = configuredPorts();
        if (!configuredPorts.isEmpty()) {
            log.warnf(
                    "Not starting Elastic Dev Services because '%s' is explicitly configured, but %s (is/are) also set and will have no effect.",
                    explicitEndpoint, configuredPorts);
        } else {
            log.infof("Not starting Elastic Dev Services: '%s' is explicitly configured.", explicitEndpoint);
        }
        return false;
    }

    private String findExplicitlyConfiguredOtlpEndpoint() {
        Config cfg = Config.get();
        if (catalog != null && catalog.hasOpenTelemetry()) {
            for (String key : List.of(
                    OTEL_OTLP_ENDPOINT,
                    OTEL_OTLP_TRACES_ENDPOINT,
                    OTEL_OTLP_METRICS_ENDPOINT,
                    OTEL_OTLP_LOGS_ENDPOINT)) {
                if (cfg.getOptionalValue(key, String.class).isPresent()) {
                    return key;
                }
            }
        }
        if (catalog != null && catalog.hasMicrometerOtlp()) {
            if (cfg.getOptionalValue(MICROMETER_OTLP_URL, String.class).isPresent()) {
                return MICROMETER_OTLP_URL;
            }
        }
        return null;
    }

    private List<String> configuredPorts() {
        List<String> configured = new ArrayList<>();
        if (config != null) {
            if (config.elasticsearchPort().isPresent()) {
                configured.add("quarkus.observability.elastic.elasticsearch-port");
            }
            if (config.kibanaPort().isPresent()) {
                configured.add("quarkus.observability.elastic.kibana-port");
            }
            if (config.apmPort().isPresent()) {
                configured.add("quarkus.observability.elastic.apm-port");
            }
        }
        return configured;
    }

    @Override
    public Map<String, String> config(int privatePort, String host, int publicPort) {
        Map<String, String> containerConfigs = new HashMap<>();

        switch (privatePort) {
            case ContainerConstants.ELASTICSEARCH_PORT:
                containerConfigs.put("elasticsearch.endpoint", String.format("http://%s:%s", host, publicPort));
                break;
            case ContainerConstants.KIBANA_PORT:
                containerConfigs.put("kibana.endpoint", String.format("http://%s:%s", host, publicPort));
                break;
            case ContainerConstants.APM_SERVER_PORT:
                containerConfigs.put("apm-server.url", String.format("%s:%s", host, publicPort));
                if (catalog != null && catalog.hasOpenTelemetry()) {
                    containerConfigs.put("quarkus.otel.exporter.otlp.endpoint",
                            String.format("http://%s:%s", host, publicPort));
                    containerConfigs.put("quarkus.otel.exporter.otlp.protocol",
                            config != null ? config.otlpProtocol() : ContainerConstants.OTEL_HTTP_PROTOCOL);
                }
                if (catalog != null && catalog.hasMicrometerOtlp()) {
                    containerConfigs.put("quarkus.micrometer.export.otlp.url",
                            String.format("http://%s:%s/v1/metrics", host, publicPort));
                }
                break;
        }

        return containerConfigs;
    }

    @Override
    public <S> Map<String, Function<S, String>> configProvider(Function<S, Map<String, String>> fn) {
        return createConfigProvider(
                fn,
                Prop.of("elasticsearch.endpoint"),
                Prop.of("kibana.endpoint"),
                Prop.of("elastic.mcp.api-key"),
                Prop.of("apm-server.url"),
                new Prop("quarkus.micrometer.export.otlp.url", () -> catalog != null && catalog.hasMicrometerOtlp()),
                new Prop("quarkus.otel.exporter.otlp.endpoint", () -> catalog != null && catalog.hasOpenTelemetry()),
                new Prop("quarkus.otel.exporter.otlp.protocol", () -> catalog != null && catalog.hasOpenTelemetry()));
    }

    @Override
    protected ElasticContainer defaultContainer() {
        return new ElasticContainer();
    }

    @Override
    public Map<String, String> doStart() {
        String host = container.getHost();
        Map<String, String> containerConfigs = new HashMap<>();

        containerConfigs.putAll(
                config(ContainerConstants.ELASTICSEARCH_PORT, host, container.getElasticsearchPort()));
        containerConfigs.putAll(
                config(ContainerConstants.KIBANA_PORT, host, container.getKibanaPort()));
        containerConfigs.putAll(
                config(ContainerConstants.APM_SERVER_PORT, host, container.getApmPort()));

        String apiKey = createMcpApiKey(host, container.getElasticsearchPort());
        if (apiKey != null) {
            containerConfigs.put("elastic.mcp.api-key", apiKey);
        }

        return containerConfigs;
    }

    private static final String API_KEY_REQUEST_BODY = """
            {
              "name": "mcp-devservice",
              "role_descriptors": {
                "mcp": {
                  "cluster": ["monitor_inference"],
                  "indices": [{"names": ["*"],
                               "privileges": ["read", "view_index_metadata"]}],
                  "applications": [{
                    "application": "kibana-.kibana",
                    "privileges": ["feature_agentBuilder.read",
                                    "feature_actions.read"],
                    "resources": ["*"]
                  }]
                }
              }
            }""";

    private String createMcpApiKey(String host, int esPort) {
        String username = config != null ? config.username() : "elastic";
        String password = config != null ? config.password() : "changeme";
        String credentials = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format("http://%s:%s/_security/api_key", host, esPort)))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Basic " + credentials)
                    .POST(HttpRequest.BodyPublishers.ofString(API_KEY_REQUEST_BODY))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body();
                // Extract the "encoded" field — the base64-encoded id:key ready for Bearer auth
                int idx = body.indexOf("\"encoded\"");
                if (idx >= 0) {
                    int start = body.indexOf('"', idx + 10) + 1;
                    int end = body.indexOf('"', start);
                    String encoded = body.substring(start, end);
                    log.infof("Created Elasticsearch API key for Kibana MCP access");
                    return encoded;
                }
            }
            log.warnf("Failed to create Elasticsearch API key (status %d): %s", response.statusCode(), response.body());
        } catch (IOException | InterruptedException e) {
            log.warnf(e, "Failed to create Elasticsearch API key for MCP access");
        }
        return null;
    }

    @Override
    public int order() {
        return DevResourceLifecycleManager.OTEL;
    }
}
