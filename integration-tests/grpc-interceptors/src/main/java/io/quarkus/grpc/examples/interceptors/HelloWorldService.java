package io.quarkus.grpc.examples.interceptors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import examples.GreeterGrpc;
import examples.HelloReply;
import examples.HelloRequest;
import io.grpc.stub.StreamObserver;
import io.quarkus.grpc.GrpcService;
import io.smallrye.common.annotation.Blocking;

@GrpcService
public class HelloWorldService extends GreeterGrpc.GreeterImplBase {
    private static final Logger log = LoggerFactory.getLogger(HelloWorldService.class);
    
    private HelloReply getReply(HelloRequest request) {
        String name = request.getName();
        if (name.equals("Fail")) {
            throw new HelloException(name);
        }
        return HelloReply.newBuilder().setMessage("Hello " + name).build();
    }

    private void sleep(long d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Blocking
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        request.getDurationList().parallelStream().forEach(duration -> {
            log.info("server: starting " + duration);
            sleep(duration);
            responseObserver.onNext(HelloReply.newBuilder().setMessage("Slept for " + duration + " ms").build());
            log.info("server: finished " + duration);
        });

        log.info("server: reached #onCompleted");
        sleep(2000);
        responseObserver.onCompleted();
        log.info("server: finished #onCompleted");    }

    @Override
    public StreamObserver<HelloRequest> multiHello(StreamObserver<HelloReply> responseObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(HelloRequest helloRequest) {
                responseObserver.onNext(getReply(helloRequest));
            }

            @Override
            public void onError(Throwable throwable) {
                responseObserver.onError(throwable);
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
}
