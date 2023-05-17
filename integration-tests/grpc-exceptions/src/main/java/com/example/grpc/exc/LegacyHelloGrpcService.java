package com.example.grpc.exc;

import com.google.protobuf.Any;
import com.google.rpc.ErrorInfo;
import com.google.rpc.Status;

import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import io.quarkus.grpc.GrpcService;

@GrpcService
public class LegacyHelloGrpcService extends LegacyHelloGrpcGrpc.LegacyHelloGrpcImplBase {
    @Override
    public void legacySayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        // it NEEDS to be plain StatusException, and NOT StatusRuntimeException ?!
        final StatusException t = new StatusException(io.grpc.Status.INVALID_ARGUMENT);
        responseObserver.onError(t);
    }

    @Override
    public void legacySayHelloRuntime(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        ErrorInfo errorInfo = ErrorInfo.newBuilder()
                .setDomain("org.acme.test")
                .setReason("stub-error")
                .build();

        final StatusRuntimeException t = StatusProto.toStatusRuntimeException(
                Status.newBuilder()
                        .setCode(io.grpc.Status.INVALID_ARGUMENT.getCode().value())
                        .setMessage("this is a test error")
                        .addDetails(Any.pack(errorInfo))
                        .build());
        responseObserver.onError(t);
    }
}
