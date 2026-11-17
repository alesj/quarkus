package io.quarkus.observability.common.config;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import io.quarkus.observability.common.ContainerConstants;
import io.quarkus.runtime.annotations.ConfigDocIgnore;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ElasticConfig extends ContainerConfig {

    /**
     * The name of the Elastic all-in-one Docker image.
     */
    @WithDefault(ContainerConstants.ELASTIC)
    String imageName();

    /**
     * The Docker network aliases.
     */
    @WithDefault("elastic,elastic.testcontainer.docker")
    Optional<Set<String>> networkAliases();

    /**
     * The label of the container.
     */
    @WithDefault("quarkus-dev-service-elastic")
    String label();

    /**
     * The value of the {@code quarkus-dev-service} label attached to the started container.
     * This property is used when {@code shared} is set to {@code true}.
     */
    @WithDefault("elastic")
    String serviceName();

    /**
     * The Elasticsearch username.
     */
    @WithDefault("elastic")
    String username();

    /**
     * The Elasticsearch password.
     */
    @WithDefault("changeme")
    String password();

    /**
     * The timeout.
     */
    @WithDefault("3M")
    Duration timeout();

    /**
     * The Elastic's OTLP protocol.
     * APM Server accepts both gRPC and HTTP on the same port (8200).
     */
    @WithDefault(ContainerConstants.OTEL_HTTP_PROTOCOL)
    String otlpProtocol();

    /**
     * The Elasticsearch port, if set it will be a fixed value.
     */
    OptionalInt elasticsearchPort();

    /**
     * The Kibana port, if set it will be a fixed value.
     */
    OptionalInt kibanaPort();

    /**
     * The APM Server / OTLP port, if set it will be a fixed value.
     * APM Server natively accepts OTLP on this port (both gRPC and HTTP).
     */
    OptionalInt apmPort();

    /**
     * The grace period in seconds for graceful shutdown after the container receives SIGTERM or SIGINT.
     */
    OptionalInt shutdownTimeout();

    /**
     * A way to override `quarkus.otel.metric.export.interval` property's default value.
     */
    @OverrideProperty("quarkus.otel.metric.export.interval")
    @WithDefault(ContainerConstants.OTEL_METRIC_EXPORT_INTERVAL)
    @ConfigDocIgnore
    String otelMetricExportInterval();

    /**
     * A way to override `quarkus.otel.bsp.schedule.delay` property's default value.
     */
    @OverrideProperty("quarkus.otel.bsp.schedule.delay")
    @WithDefault(ContainerConstants.OTEL_BSP_SCHEDULE_DELAY)
    @ConfigDocIgnore
    String otelBspScheduleDelay();

    /**
     * A way to override `quarkus.otel.metric.export.interval` property's default value.
     */
    @OverrideProperty("quarkus.otel.blrp.schedule.delay")
    @WithDefault(ContainerConstants.OTEL_BLRP_SCHEDULE_DELAY)
    @ConfigDocIgnore
    String otelBlrpScheduleDelay();
}
