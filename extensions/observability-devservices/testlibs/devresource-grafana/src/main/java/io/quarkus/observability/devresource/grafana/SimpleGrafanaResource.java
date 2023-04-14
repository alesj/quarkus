package io.quarkus.observability.devresource.grafana;

import java.util.Map;

import org.testcontainers.containers.GenericContainer;

import io.quarkus.observability.common.config.ModulesConfiguration;
import io.quarkus.observability.common.config.SimpleGrafanaConfig;
import io.quarkus.observability.devresource.ContainerResource;
import io.quarkus.observability.devresource.DevResourceLifecycleManager;
import io.quarkus.observability.testcontainers.SimpleGrafanaContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class SimpleGrafanaResource extends ContainerResource<SimpleGrafanaContainer, SimpleGrafanaConfig>
        implements QuarkusTestResourceLifecycleManager {
    @Override
    public SimpleGrafanaConfig config(ModulesConfiguration configuration) {
        return configuration.grafana();
    }

    @Override
    public GenericContainer<?> container(SimpleGrafanaConfig config, ModulesConfiguration root) {
        return set(new SimpleGrafanaContainer(config, root));
    }

    @Override
    public Map<String, String> config(int privatePort, String host, int publicPort) {
        return Map.of("quarkus.grafana.url", String.format("%s:%s", host, publicPort));
    }

    @Override
    protected SimpleGrafanaContainer defaultContainer() {
        return new SimpleGrafanaContainer();
    }

    @Override
    public Map<String, String> doStart() {
        String host = container.getHost();
        Integer mappedPort = container.getGrafanaPort();
        return Map.of("quarkus.grafana.url", String.format("%s:%s", host, mappedPort));
    }

    @Override
    public int order() {
        return DevResourceLifecycleManager.GRAFANA;
    }
}
