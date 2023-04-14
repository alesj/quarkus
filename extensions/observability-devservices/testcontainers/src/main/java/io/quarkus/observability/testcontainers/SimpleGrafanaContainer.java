package io.quarkus.observability.testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

import io.quarkus.observability.common.ContainerConstants;
import io.quarkus.observability.common.config.AbstractGrafanaConfig;
import io.quarkus.observability.common.config.ConfigUtils;
import io.quarkus.observability.common.config.ModulesConfiguration;
import io.quarkus.observability.common.config.SimpleGrafanaConfig;
import io.quarkus.observability.common.config.VictoriaMetricsConfig;

public class SimpleGrafanaContainer extends GrafanaContainer<SimpleGrafanaContainer, SimpleGrafanaConfig> {
    protected static final String GRAFANA_NETWORK_ALIAS = "grafana.testcontainer.docker";

    private final ModulesConfiguration root;

    public SimpleGrafanaContainer() {
        this(new GrafanaConfigImpl(), null);
    }

    public SimpleGrafanaContainer(SimpleGrafanaConfig config, ModulesConfiguration root) {
        super(config);
        this.config = config;
        this.root = root;
    }

    @Override
    protected void containerIsCreated(String containerId) {
        super.containerIsCreated(containerId);
        byte[] datasources = getResourceAsBytes(config.datasourcesFile());
        String content = new String(datasources, StandardCharsets.UTF_8);
        String vmEndpoint = "victoria-metrics:8428";
        if (root != null) {
            VictoriaMetricsConfig vmc = root.victoriaMetrics();
            vmEndpoint = ConfigUtils.vmEndpoint(vmc);
        }
        content = content.replace("xTARGETx", vmEndpoint);
        addFileToContainer(content.getBytes(StandardCharsets.UTF_8), DATASOURCES_PATH);
    }

    private static class GrafanaConfigImpl extends AbstractGrafanaConfig implements SimpleGrafanaConfig {
        public GrafanaConfigImpl() {
            super(ContainerConstants.GRAFANA);
        }

        @Override
        public Optional<Set<String>> networkAliases() {
            return Optional.of(Set.of("lgtm", GRAFANA_NETWORK_ALIAS));
        }

        @Override
        public String datasourcesFile() {
            return "datasources.yaml";
        }
    }
}
