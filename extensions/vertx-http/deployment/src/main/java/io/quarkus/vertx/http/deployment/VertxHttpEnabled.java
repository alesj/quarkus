package io.quarkus.vertx.http.deployment;

import java.util.function.BooleanSupplier;

import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;

public class VertxHttpEnabled implements BooleanSupplier {
    VertxHttpBuildTimeConfig config;

    public boolean getAsBoolean() {
        return config.enabled();
    }
}
