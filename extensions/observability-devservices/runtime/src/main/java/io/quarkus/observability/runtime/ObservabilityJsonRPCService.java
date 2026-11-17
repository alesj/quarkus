package io.quarkus.observability.runtime;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.eclipse.microprofile.config.inject.ConfigProperty;

public class ObservabilityJsonRPCService {

    @ConfigProperty(name = "otel-collector.url")
    Optional<String> otelEndpoint;

    @ConfigProperty(name = "grafana.endpoint")
    Optional<String> grafanaEndpoint;

    @ConfigProperty(name = "elasticsearch.endpoint")
    Optional<String> elasticsearchEndpoint;

    @ConfigProperty(name = "kibana.endpoint")
    Optional<String> kibanaEndpoint;

    @ConfigProperty(name = "apm-server.url")
    Optional<String> apmServerEndpoint;

    @ConfigProperty(name = "elastic.mcp.api-key")
    Optional<String> elasticMcpApiKey;

    public String getGrafanaEndpoint() {
        return grafanaEndpoint
                .map(e -> Strings.CS.prependIfMissing(e, "http://"))
                .orElse("");
    }

    public String getOtelEndpoint() {
        return otelEndpoint
                .map(e -> StringUtils.substringAfterLast(e, ":"))
                .orElse("");
    }

    public String getElasticsearchEndpoint() {
        return elasticsearchEndpoint
                .map(e -> Strings.CS.prependIfMissing(e, "http://"))
                .orElse("");
    }

    public String getKibanaEndpoint() {
        return kibanaEndpoint
                .map(e -> Strings.CS.prependIfMissing(e, "http://"))
                .orElse("");
    }

    public String getApmServerEndpoint() {
        return apmServerEndpoint
                .map(e -> Strings.CS.prependIfMissing(e, "http://"))
                .orElse("");
    }

    public String getElasticMcpApiKey() {
        return elasticMcpApiKey.orElse("");
    }
}
