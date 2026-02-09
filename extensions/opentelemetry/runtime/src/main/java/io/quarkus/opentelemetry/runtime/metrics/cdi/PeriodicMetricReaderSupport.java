package io.quarkus.opentelemetry.runtime.metrics.cdi;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.internal.DaemonThreadFactory;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;

/**
 * Fix for OpenTelemetry ContextClassLoader issue in Quarkus
 * <p>
 * Problem: OpenTelemetry's DaemonThreadFactory creates scheduler threads with null ContextClassLoader.
 * This breaks CDI injection and application class loading in gauge callbacks.
 * <p>
 * This fix patches the PeriodicMetricReader to use threads with proper ContextClassLoader.
 */
public class PeriodicMetricReaderSupport implements AutoConfigurationCustomizerProvider {
    private static final Logger log = LoggerFactory.getLogger(PeriodicMetricReaderSupport.class);
    private static final Duration DEFAULT_EXPORT_INTERVAL = Duration.ofMinutes(1);

    @Override
    public void customize(AutoConfigurationCustomizer autoConfiguration) {
        // depending on the order ... ?!
        AtomicReference<MetricExporter> exporterRef = new AtomicReference<>();

        autoConfiguration.addMetricExporterCustomizer(((metricExporter, configProperties) -> {
            exporterRef.set(metricExporter);
            return metricExporter;
        }));

        autoConfiguration.addMetricReaderCustomizer((metricReader, config) -> {
            if (metricReader instanceof PeriodicMetricReader) {
                log.info("Successfully patched PeriodicMetricReader scheduler");

                Instance<ScheduledExecutorService> ses = CDI
                        .current()
                        .select(ScheduledExecutorService.class, Any.Literal.INSTANCE);

                ScheduledExecutorService executor;
                if (ses.isUnsatisfied() || ses.isAmbiguous()) {
                    ClassLoader cl = Thread.currentThread().getContextClassLoader();
                    executor = Executors.newScheduledThreadPool(1, new ContextClassLoaderThreadFactory(cl));
                } else {
                    executor = ses.get();
                }

                return PeriodicMetricReader.builder(exporterRef.get())
                        .setExecutor(executor)
                        .setInterval(config.getDuration("otel.metric.export.interval", DEFAULT_EXPORT_INTERVAL))
                        .build(); // return ours
            }
            return metricReader;
        });
    }

    private static class ContextClassLoaderThreadFactory implements ThreadFactory {
        private final ThreadFactory delegate = new DaemonThreadFactory("PeriodicMetricReaderSupport");
        private final ClassLoader contextClassLoader;

        public ContextClassLoaderThreadFactory(ClassLoader contextClassLoader) {
            this.contextClassLoader = contextClassLoader;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = delegate.newThread(r);
            // Fix: Set the application class loader as context class loader
            thread.setContextClassLoader(contextClassLoader);
            return thread;
        }
    }

}
