package io.quarkus.grpc.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import javax.enterprise.inject.Instance;

import io.vertx.ext.web.Router;
import org.jboss.logging.Logger;

import grpc.health.v1.HealthOuterClass;
import io.grpc.BindableService;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.quarkus.arc.Arc;
import io.quarkus.arc.Subclass;
import io.quarkus.grpc.runtime.config.GrpcConfiguration;
import io.quarkus.grpc.runtime.config.GrpcServerConfiguration;
import io.quarkus.grpc.runtime.devmode.GrpcHotReplacementInterceptor;
import io.quarkus.grpc.runtime.devmode.GrpcServerReloader;
import io.quarkus.grpc.runtime.health.GrpcHealthStorage;
import io.quarkus.grpc.runtime.reflection.ReflectionService;
import io.quarkus.grpc.runtime.supports.CompressionInterceptor;
import io.quarkus.grpc.runtime.supports.blocking.BlockingServerInterceptor;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.ProfileManager;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServiceBridge;

@Recorder
public class GrpcServerRecorder {
    private static final Logger LOGGER = Logger.getLogger(GrpcServerRecorder.class.getName());

    private static final AtomicInteger grpcVerticleCount = new AtomicInteger(0);
    private Map<String, List<String>> blockingMethodsPerService = Collections.emptyMap();

    private static volatile DevModeInterceptor devModeInterceptor;
    private static volatile List<GrpcServiceDefinition> services = Collections.emptyList();

    public static List<GrpcServiceDefinition> getServices() {
        return services;
    }

    public void initializeGrpcServer(RuntimeValue<Vertx> vertxSupplier,
            GrpcConfiguration cfg,
            ShutdownContext shutdown,
            Map<String, List<String>> blockingMethodsPerServiceImplementationClass, LaunchMode launchMode) {
        GrpcContainer grpcContainer = Arc.container().instance(GrpcContainer.class).get();
        if (grpcContainer == null) {
            throw new IllegalStateException("gRPC not initialized, GrpcContainer not found");
        }
        Vertx vertx = vertxSupplier.getValue();
        if (hasNoServices(grpcContainer.getServices()) && LaunchMode.current() != LaunchMode.DEVELOPMENT) {
            LOGGER.error("Unable to find beans exposing the `BindableService` interface - not starting the gRPC server");
        }

        this.blockingMethodsPerService = blockingMethodsPerServiceImplementationClass;

        GrpcServerConfiguration configuration = cfg.server;

        if (launchMode == LaunchMode.DEVELOPMENT) {
            // start single server, not in a verticle, regardless of the configuration.instances
            // for reason unknown to me, verticles occasionally get undeployed on dev mode reload
            if (GrpcServerReloader.getServer() == null) {
                devModeStart(grpcContainer, vertx, configuration, shutdown, launchMode);
            } else {
                devModeReload(grpcContainer, vertx, configuration, shutdown);
            }
        } else {
            prodStart(grpcContainer, vertx, configuration, launchMode);
        }
    }

    private void prodStart(GrpcContainer grpcContainer, Vertx vertx, GrpcServerConfiguration configuration,
            LaunchMode launchMode) {
        CompletableFuture<Void> startResult = new CompletableFuture<>();

        vertx.deployVerticle(
                new Supplier<Verticle>() {
                    @Override
                    public Verticle get() {
                        return new GrpcServerVerticle(configuration, grpcContainer, launchMode);
                    }
                },
                new DeploymentOptions().setInstances(configuration.instances),
                new Handler<AsyncResult<String>>() {
                    @Override
                    public void handle(AsyncResult<String> result) {
                        if (result.failed()) {
                            startResult.completeExceptionally(result.cause());
                        } else {
                            GrpcServerRecorder.this.postStartup(configuration, launchMode == LaunchMode.TEST);

                            startResult.complete(null);
                        }
                    }
                });

        try {
            startResult.get(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Unable to start the gRPC server, waiting for server start interrupted");
        } catch (TimeoutException e) {
            LOGGER.error("Unable to start the gRPC server, still not listening after 1 minute");
        } catch (ExecutionException e) {
            LOGGER.error("Unable to start the gRPC server", e.getCause());
        }
    }

    private void postStartup(GrpcServerConfiguration configuration, boolean test) {
        initHealthStorage();
        LOGGER.infof("gRPC Server started on %s:%d [SSL enabled: %s]",
                configuration.host, test ? configuration.testPort : configuration.port, !configuration.plainText);
    }

    private void initHealthStorage() {
        GrpcHealthStorage storage = Arc.container().instance(GrpcHealthStorage.class).get();
        storage.setStatus(GrpcHealthStorage.DEFAULT_SERVICE_NAME,
                HealthOuterClass.HealthCheckResponse.ServingStatus.SERVING);
        for (GrpcServiceDefinition service : services) {
            storage.setStatus(service.definition.getServiceDescriptor().getName(),
                    HealthOuterClass.HealthCheckResponse.ServingStatus.SERVING);
        }
    }

    private void devModeStart(GrpcContainer grpcContainer, Vertx vertx, GrpcServerConfiguration configuration,
            ShutdownContext shutdown, LaunchMode launchMode) {
        devModeInterceptor = new DevModeInterceptor(Thread.currentThread().getContextClassLoader());

        buildServer(vertx, configuration, grpcContainer, launchMode);
        grpcVerticleCount.incrementAndGet();

        shutdown.addShutdownTask(
                new Runnable() { // NOSONAR
                    @Override
                    public void run() {
                        GrpcServerReloader.reset();
                    }
                });
    }

    private static boolean hasNoServices(Instance<BindableService> services) {
        return services.isUnsatisfied()
                || services.stream().count() == 1
                        && services.get().bindService().getServiceDescriptor().getName().equals("grpc.health.v1.Health");
    }

    private static List<GrpcServiceDefinition> collectServiceDefinitions(Instance<BindableService> services) {
        List<GrpcServiceDefinition> definitions = new ArrayList<>();
        for (BindableService service : services) {
            ServerServiceDefinition definition = service.bindService();
            definitions.add(new GrpcServiceDefinition(service, definition));
        }

        // Set the last service definitions in use, referenced in the Dev UI
        GrpcServerRecorder.services = definitions;

        return definitions;
    }

    public static final class GrpcServiceDefinition {

        public final BindableService service;
        public final ServerServiceDefinition definition;

        GrpcServiceDefinition(BindableService service, ServerServiceDefinition definition) {
            this.service = service;
            this.definition = definition;
        }

        public String getImplementationClassName() {
            if (service instanceof Subclass) {
                // All intercepted services are represented by a generated subclass
                return service.getClass().getSuperclass().getName();
            }
            return service.getClass().getName();
        }
    }

    private void devModeReload(GrpcContainer grpcContainer, Vertx vertx, GrpcServerConfiguration configuration,
            ShutdownContext shutdown) {
        List<ServerServiceDefinition> servicesWithInterceptors = new ArrayList<>();
        CompressionInterceptor compressionInterceptor = prepareCompressionInterceptor(configuration);
        List<ServerServiceDefinition> definitions = new ArrayList<>();
        List<GrpcServiceDefinition> services = collectServiceDefinitions(grpcContainer.getServices());
        for (GrpcServiceDefinition service : services) {
            servicesWithInterceptors.add(serviceWithInterceptors(vertx, grpcContainer, compressionInterceptor, service, true));
            definitions.add(service.definition);
        }
        ServerServiceDefinition reflectionService = new ReflectionService(definitions).bindService();
        servicesWithInterceptors.add(reflectionService);

        devModeInterceptor = new DevModeInterceptor(Thread.currentThread().getContextClassLoader());

        initHealthStorage();

        List<ServerInterceptor> globalInterceptors = grpcContainer.getSortedGlobalInterceptors();
        globalInterceptors.add(0, devModeInterceptor);
        GrpcServerReloader.reinitialize(servicesWithInterceptors, globalInterceptors);

        shutdown.addShutdownTask(
                new Runnable() { // NOSONAR
                    @Override
                    public void run() {
                        GrpcServerReloader.reset();
                    }
                });
    }

    public static int getVerticleCount() {
        return grpcVerticleCount.get();
    }

    public RuntimeValue<ServerInterceptorStorage> initServerInterceptorStorage(
            Map<String, Set<Class<?>>> perServiceInterceptors,
            Set<Class<?>> globalInterceptors) {
        return new RuntimeValue<>(new ServerInterceptorStorage(perServiceInterceptors, globalInterceptors));
    }

    private void buildServer(Vertx vertx, GrpcServerConfiguration configuration,
            GrpcContainer grpcContainer, LaunchMode launchMode) {

        GrpcServer server = GrpcServer.server(vertx);
        List<ServerInterceptor> globalInterceptors = grpcContainer.getSortedGlobalInterceptors();

        if (launchMode == LaunchMode.DEVELOPMENT) {
            globalInterceptors.add(0, devModeInterceptor); // add as first
        }

        List<GrpcServiceDefinition> toBeRegistered = collectServiceDefinitions(grpcContainer.getServices());
        List<ServerServiceDefinition> definitions = new ArrayList<>();
        List<GrpcServiceBridge> bridges = new ArrayList<>();

        CompressionInterceptor compressionInterceptor = prepareCompressionInterceptor(configuration);

        for (GrpcServiceDefinition service : toBeRegistered) {
            ServerServiceDefinition defWithInterceptors = serviceWithInterceptors(
                    vertx, grpcContainer, compressionInterceptor, service, launchMode == LaunchMode.DEVELOPMENT);
            LOGGER.debugf("Registered gRPC service '%s'", service.definition.getServiceDescriptor().getName());
            ServerServiceDefinition serviceDefinition = ServerInterceptors.intercept(defWithInterceptors, globalInterceptors);
            GrpcServiceBridge bridge = GrpcServiceBridge.bridge(serviceDefinition);
            bridge.bind(server);
            bridges.add(bridge);
            definitions.add(service.definition);
        }

        boolean reflectionServiceEnabled = configuration.enableReflectionService
                || ProfileManager.getLaunchMode() == LaunchMode.DEVELOPMENT;

        if (reflectionServiceEnabled) {
            LOGGER.info("Registering gRPC reflection reflectionService");
            ReflectionService reflectionService = new ReflectionService(definitions);
            ServerServiceDefinition serviceDefinition = ServerInterceptors.intercept(reflectionService, globalInterceptors);
            GrpcServiceBridge bridge = GrpcServiceBridge.bridge(serviceDefinition);
            bridge.bind(server);
            bridges.add(bridge);
        }

        LOGGER.debugf("Starting gRPC Server ...");
        GrpcServerReloader.init(server, bridges);
    }

    /**
     * Compression interceptor if needed, null otherwise
     *
     * @param configuration gRPC server configuration
     * @return interceptor or null
     */
    private CompressionInterceptor prepareCompressionInterceptor(GrpcServerConfiguration configuration) {
        CompressionInterceptor compressionInterceptor = null;
        if (configuration.compression.isPresent()) {
            compressionInterceptor = new CompressionInterceptor(configuration.compression.get());
        }
        return compressionInterceptor;
    }

    private ServerServiceDefinition serviceWithInterceptors(Vertx vertx, GrpcContainer grpcContainer,
            CompressionInterceptor compressionInterceptor, GrpcServiceDefinition service, boolean devMode) {
        List<ServerInterceptor> interceptors = new ArrayList<>();
        if (compressionInterceptor != null) {
            interceptors.add(compressionInterceptor);
        }

        interceptors.addAll(grpcContainer.getSortedPerServiceInterceptors(service.getImplementationClassName()));

        // We only register the blocking interceptor if needed by at least one method of the service.
        if (!blockingMethodsPerService.isEmpty()) {
            List<String> list = blockingMethodsPerService.get(service.getImplementationClassName());
            if (list != null) {
                interceptors.add(new BlockingServerInterceptor(vertx, list, devMode));
            }
        }
        return ServerInterceptors.intercept(service.definition, interceptors);
    }

    private class GrpcServerVerticle extends AbstractVerticle {
        private final GrpcServerConfiguration configuration;
        private final GrpcContainer grpcContainer;
        private final LaunchMode launchMode;

        GrpcServerVerticle(GrpcServerConfiguration configuration, GrpcContainer grpcContainer, LaunchMode launchMode) {
            this.configuration = configuration;
            this.grpcContainer = grpcContainer;
            this.launchMode = launchMode;
        }

        @Override
        public void start(Promise<Void> startPromise) {
            if (grpcContainer.getServices().isUnsatisfied()) {
                LOGGER.warn(
                        "Unable to find bean exposing the `BindableService` interface - not starting the gRPC server");
                return;
            }
            buildServer(getVertx(), configuration, grpcContainer, launchMode);
            startPromise.complete();
            grpcVerticleCount.incrementAndGet();
        }

        @Override
        public void stop(Promise<Void> stopPromise) {
            stopPromise.complete();
            grpcVerticleCount.decrementAndGet();
        }
    }

    private static class DevModeInterceptor implements ServerInterceptor {
        private final ClassLoader classLoader;

        public DevModeInterceptor(ClassLoader contextClassLoader) {
            classLoader = contextClassLoader;
        }

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
                ServerCallHandler<ReqT, RespT> next) {
            GrpcHotReplacementInterceptor.fire();
            ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(classLoader);
            try {
                return next.startCall(call, headers);
            } finally {
                Thread.currentThread().setContextClassLoader(originalTccl);
            }
        }
    }
}
