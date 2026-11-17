package io.quarkus.observability.testcontainers;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

import io.quarkus.observability.common.ContainerConstants;
import io.quarkus.observability.common.config.AbstractContainerConfig;
import io.quarkus.observability.common.config.ElasticConfig;

@SuppressWarnings("resource")
public class ElasticContainer extends ObservabilityContainer<ElasticContainer, ElasticConfig> {
    protected static final String ELASTIC_NETWORK_ALIAS = "elastic.testcontainer.docker";

    private final ElasticConfig config;

    public ElasticContainer() {
        this(new ElasticConfigImpl());
    }

    public ElasticContainer(ElasticConfig config) {
        super(config);
        this.config = config;
        withEnv("ELASTIC_PASSWORD", config.password());
        addExposedPorts(
                ContainerConstants.ELASTICSEARCH_PORT,
                ContainerConstants.KIBANA_PORT,
                ContainerConstants.APM_SERVER_PORT);
        config.elasticsearchPort()
                .ifPresent(port -> addFixedExposedPort(port, ContainerConstants.ELASTICSEARCH_PORT));
        config.kibanaPort()
                .ifPresent(port -> addFixedExposedPort(port, ContainerConstants.KIBANA_PORT));
        config.apmPort()
                .ifPresent(port -> addFixedExposedPort(port, ContainerConstants.APM_SERVER_PORT));
        config.shutdownTimeout()
                .ifPresent(timeout -> withEnv("ELASTIC_SHUTDOWN_TIMEOUT_SECONDS", Integer.toString(timeout)));
        waitingFor(waitStrategy());
    }

    protected WaitStrategy waitStrategy() {
        return new WaitAllStrategy()
                .withStartupTimeout(config.timeout())
                .withStrategy(
                        Wait.forLogMessage(".*All components are up and running.*", 1)
                                .withStartupTimeout(config.timeout()));
    }

    @Override
    protected String prefix() {
        return "Elastic";
    }

    public String getOtlpProtocol() {
        return config.otlpProtocol();
    }

    public int getElasticsearchPort() {
        return getMappedPort(ContainerConstants.ELASTICSEARCH_PORT);
    }

    public int getKibanaPort() {
        return getMappedPort(ContainerConstants.KIBANA_PORT);
    }

    public int getApmPort() {
        return getMappedPort(ContainerConstants.APM_SERVER_PORT);
    }

    protected static class ElasticConfigImpl extends AbstractContainerConfig implements ElasticConfig {
        public ElasticConfigImpl() {
            this(ContainerConstants.ELASTIC);
        }

        public ElasticConfigImpl(String imageName) {
            super(imageName);
        }

        @Override
        public Optional<Set<String>> networkAliases() {
            return Optional.of(Set.of("elastic", ELASTIC_NETWORK_ALIAS));
        }

        @Override
        public String username() {
            return "elastic";
        }

        @Override
        public String password() {
            return "changeme";
        }

        @Override
        public Duration timeout() {
            return Duration.ofMinutes(3);
        }

        @Override
        public String otlpProtocol() {
            return ContainerConstants.OTEL_HTTP_PROTOCOL;
        }

        @Override
        public OptionalInt elasticsearchPort() {
            return OptionalInt.empty();
        }

        @Override
        public OptionalInt kibanaPort() {
            return OptionalInt.empty();
        }

        @Override
        public OptionalInt apmPort() {
            return OptionalInt.empty();
        }

        @Override
        public OptionalInt shutdownTimeout() {
            return OptionalInt.empty();
        }

        @Override
        public String otelMetricExportInterval() {
            return ContainerConstants.OTEL_METRIC_EXPORT_INTERVAL;
        }

        @Override
        public String otelBspScheduleDelay() {
            return ContainerConstants.OTEL_BSP_SCHEDULE_DELAY;
        }

        @Override
        public String otelBlrpScheduleDelay() {
            return ContainerConstants.OTEL_BLRP_SCHEDULE_DELAY;
        }
    }
}
