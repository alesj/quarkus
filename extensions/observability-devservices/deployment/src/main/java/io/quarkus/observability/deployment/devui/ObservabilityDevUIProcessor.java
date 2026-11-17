package io.quarkus.observability.deployment.devui;

import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.deployment.IsDevServicesSupportedByLaunchMode;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.IsLocalDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.devjsonrpc.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.FooterPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.devui.spi.page.WebComponentPageBuilder;
import io.quarkus.observability.deployment.DevResourceEnabledBuildItem;
import io.quarkus.observability.runtime.ObservabilityJsonRPCService;
import io.quarkus.observability.runtime.config.ObservabilityConfiguration;

/**
 * Dev UI card for displaying observability backend details (LGTM or Elastic).
 */
@BuildSteps(onlyIf = { IsDevServicesSupportedByLaunchMode.class, DevServicesConfig.Enabled.class,
        ObservabilityDevUIProcessor.IsObservabilityEnabled.class })
public class ObservabilityDevUIProcessor {

    private static final List<String> EXPLICIT_ENDPOINT_PROPERTIES = List.of(
            "quarkus.otel.exporter.otlp.endpoint",
            "quarkus.otel.exporter.otlp.traces.endpoint",
            "quarkus.otel.exporter.otlp.metrics.endpoint",
            "quarkus.otel.exporter.otlp.logs.endpoint",
            "quarkus.micrometer.export.otlp.url");

    public static class IsObservabilityEnabled implements BooleanSupplier {
        ObservabilityConfiguration config;

        @Override
        public boolean getAsBoolean() {
            if (!config.enabled() || config.devResources()) {
                return false;
            }
            if (!config.lgtm().enabled() && !config.elastic().enabled()) {
                return false;
            }
            var cfg = ConfigProvider.getConfig();
            return EXPLICIT_ENDPOINT_PROPERTIES.stream()
                    .noneMatch(key -> cfg.getOptionalValue(key, String.class).isPresent());
        }
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    void createVersion(List<DevResourceEnabledBuildItem> enabledResources,
            BuildProducer<CardPageBuildItem> cardPageBuildItemBuildProducer,
            BuildProducer<FooterPageBuildItem> footerProducer) {

        Set<String> enabled = enabledResources.stream()
                .map(DevResourceEnabledBuildItem::getDevId)
                .collect(Collectors.toSet());

        if (enabled.contains("Lgtm")) {
            produceLgtmDevUI(cardPageBuildItemBuildProducer, footerProducer);
        }

        if (enabled.contains("Elastic")) {
            produceElasticDevUI(cardPageBuildItemBuildProducer, footerProducer);
        }
    }

    private void produceLgtmDevUI(BuildProducer<CardPageBuildItem> cardProducer,
            BuildProducer<FooterPageBuildItem> footerProducer) {

        final CardPageBuildItem card = new CardPageBuildItem();

        card.addPage(Page.externalPageBuilder("Grafana UI")
                .icon("font-awesome-solid:chart-line")
                .doNotEmbed()
                .dynamicUrlJsonRPCMethodName("getGrafanaEndpoint"));

        card.addPage(Page.externalPageBuilder("OpenTelemetry Port")
                .icon("font-awesome-solid:binoculars")
                .doNotEmbed()
                .url("https://opentelemetry.io/")
                .dynamicLabelJsonRPCMethodName("getOtelEndpoint"));

        cardProducer.produce(card);

        WebComponentPageBuilder logPageBuilder = Page.webComponentPageBuilder()
                .icon("font-awesome-solid:chart-line")
                .title("LGTM")
                .componentLink("qwc-lgtm-log.js");

        footerProducer.produce(new FooterPageBuildItem(logPageBuilder));
    }

    private void produceElasticDevUI(BuildProducer<CardPageBuildItem> cardProducer,
            BuildProducer<FooterPageBuildItem> footerProducer) {

        final CardPageBuildItem card = new CardPageBuildItem();

        card.addPage(Page.externalPageBuilder("Kibana UI")
                .icon("font-awesome-solid:magnifying-glass-chart")
                .doNotEmbed()
                .dynamicUrlJsonRPCMethodName("getKibanaEndpoint"));

        card.addPage(Page.externalPageBuilder("Elasticsearch")
                .icon("font-awesome-solid:database")
                .doNotEmbed()
                .dynamicUrlJsonRPCMethodName("getElasticsearchEndpoint"));

        card.addPage(Page.externalPageBuilder("APM Server")
                .icon("font-awesome-solid:tower-broadcast")
                .doNotEmbed()
                .dynamicUrlJsonRPCMethodName("getApmServerEndpoint"));

        cardProducer.produce(card);

        WebComponentPageBuilder logPageBuilder = Page.webComponentPageBuilder()
                .icon("font-awesome-solid:magnifying-glass-chart")
                .title("Elastic")
                .componentLink("qwc-elastic-log.js");

        footerProducer.produce(new FooterPageBuildItem(logPageBuilder));
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    public JsonRPCProvidersBuildItem createJsonRPCService() {
        return new JsonRPCProvidersBuildItem(ObservabilityJsonRPCService.class, BuiltinScope.SINGLETON.getName());
    }
}
