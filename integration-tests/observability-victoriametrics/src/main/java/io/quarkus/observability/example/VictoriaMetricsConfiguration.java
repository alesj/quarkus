package io.quarkus.observability.example;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.observability.promql.client.util.ObservabilityObjectMapperFactory;

@ApplicationScoped
public class VictoriaMetricsConfiguration {
    @Singleton
    public ObjectMapper objectMapper() {
        return ObservabilityObjectMapperFactory.createObjectMapper();
    }
}
