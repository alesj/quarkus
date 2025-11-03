package org.acme;

import jakarta.enterprise.context.ApplicationScoped;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.quarkus.grpc.GlobalInterceptor;

@ApplicationScoped
@GlobalInterceptor
public class GrpcLogInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata,
            ServerCallHandler<ReqT, RespT> serverCallHandler) {
        System.out.println("Intercepting " + serverCall.getMethodDescriptor().getFullMethodName());
        System.out.println("Metadata " + metadata.toString());

        return serverCallHandler.startCall(serverCall, metadata);
    }
}
