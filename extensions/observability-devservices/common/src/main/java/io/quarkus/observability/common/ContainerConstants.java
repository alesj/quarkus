package io.quarkus.observability.common;

public final class ContainerConstants {

    // Images

    public static final String LGTM = "grafana/otel-lgtm:0.2.0";
    public static final String GRAFANA = "grafana/grafana:10.1.0";
    public static final String JAEGER = "quay.io/jaegertracing/all-in-one:1.48.0";
    public static final String OTEL = "otel/opentelemetry-collector-contrib:0.83.0";
    public static final String VICTORIA_METRICS = "victoriametrics/victoria-metrics:v1.93.0";
    public static final String VM_AGENT = "victoriametrics/vmagent:v1.93.0";

    // Ports

    public static final int GRAFANA_PORT = 3000;

    public static final int OTEL_GRPC_EXPORTER_PORT = 4317;
    public static final int OTEL_HTTP_EXPORTER_PORT = 4318;
}
