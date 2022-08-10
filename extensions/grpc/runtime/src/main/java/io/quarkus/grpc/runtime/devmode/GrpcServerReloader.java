package io.quarkus.grpc.runtime.devmode;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.quarkus.dev.testing.GrpcWebSocketProxy;
import io.quarkus.grpc.stubs.ServerCalls;
import io.quarkus.grpc.stubs.StreamCollector;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.configuration.ProfileManager;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServiceBridge;

public class GrpcServerReloader {
    private static volatile GrpcServer server = null;
    private static volatile List<GrpcServiceBridge> bridges;

    public static GrpcServer getServer() {
        return server;
    }

    public static void init(GrpcServer grpcServer, List<GrpcServiceBridge> grpcBridges) {
        server = grpcServer;
        bridges = grpcBridges;
        ServerCalls.setStreamCollector(devModeCollector());
    }

    public static StreamCollector devModeCollector() {
        if (ProfileManager.getLaunchMode() != LaunchMode.DEVELOPMENT) {
            throw new IllegalStateException("Attempted to initialize development mode StreamCollector in non-development mode");
        }
        return new DevModeStreamsCollector();
    }

    public static void reset() {
        if (server == null) {
            return;
        }

        ListIterator<GrpcServiceBridge> list = bridges.listIterator(bridges.size());
        while (list.hasPrevious()) {
            GrpcServiceBridge bridge = list.previous();
            bridge.unbind(server);
        }

        StreamCollector streamCollector = ServerCalls.getStreamCollector();
        if (!(streamCollector instanceof DevModeStreamsCollector)) {
            throw new IllegalStateException("Non-dev mode streams collector used in development mode");
        }
        ((DevModeStreamsCollector) streamCollector).shutdown();
        GrpcWebSocketProxy.closeAll();
    }

    public static void reinitialize(List<ServerServiceDefinition> serviceDefinitions,
            List<ServerInterceptor> sortedInterceptors) {
        if (server == null) {
            return;
        }

        List<GrpcServiceBridge> newBridges = new ArrayList<>();
        for (ServerServiceDefinition definition : serviceDefinitions) {
            ServerServiceDefinition intercept = ServerInterceptors.intercept(definition, sortedInterceptors);
            GrpcServiceBridge bridge = GrpcServiceBridge.bridge(intercept);
            bridge.bind(server);
            newBridges.add(bridge);
        }

        bridges = newBridges;
    }

    public static void shutdown() {
        if (server != null) {
            // TODO server.shutdown(); // unbind server from Vertx http server?!
            server = null;
        }
    }
}
