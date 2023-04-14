package io.quarkus.observability.devresource.lgtm;

import java.util.Map;

import org.testcontainers.containers.GenericContainer;

import io.quarkus.observability.common.ContainerConstants;
import io.quarkus.observability.common.config.LgtmConfig;
import io.quarkus.observability.common.config.ModulesConfiguration;
import io.quarkus.observability.devresource.ContainerResource;
import io.quarkus.observability.devresource.DevResourceLifecycleManager;
import io.quarkus.observability.testcontainers.LgtmContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class LgtmResource extends ContainerResource<LgtmContainer, LgtmConfig>
        implements QuarkusTestResourceLifecycleManager {

    @Override
    public LgtmConfig config(ModulesConfiguration configuration) {
        return configuration.lgtm();
    }

    @Override
    public GenericContainer<?> container(LgtmConfig config, ModulesConfiguration root) {
        return set(new LgtmContainer(config));
    }

    @Override
    public Map<String, String> config(int privatePort, String host, int publicPort) {
        switch (privatePort) {
            case ContainerConstants.GRAFANA_PORT:
                return Map.of("quarkus.grafana.url", String.format("%s:%s", host, publicPort));
            case ContainerConstants.OTEL_GRPC_EXPORTER_PORT:
            case ContainerConstants.OTEL_HTTP_EXPORTER_PORT:
                return Map.of("quarkus.otel-collector.url", String.format("%s:%s", host, publicPort));
        }
        return Map.of();
    }

    @Override
    protected LgtmContainer defaultContainer() {
        return new LgtmContainer();
    }

    @Override
    public Map<String, String> doStart() {
        String host = container.getHost();
        return Map.of(
                "quarkus.grafana.url", String.format("%s:%s", host, container.getGrafanaPort()),
                "quarkus.otel-collector.url", String.format("%s:%s", host, container.getOtlpPort()));
    }

    @Override
    public int order() {
        return DevResourceLifecycleManager.GRAFANA;
    }
}
