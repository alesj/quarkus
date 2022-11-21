package io.quarkus.grpc.runtime.supports.exc;

import javax.enterprise.context.ApplicationScoped;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.StatusRuntimeException;
import io.quarkus.arc.DefaultBean;
import io.quarkus.grpc.ExceptionHandler;
import io.quarkus.grpc.ExceptionHandlerProvider;

@ApplicationScoped
@DefaultBean
public class DefaultExceptionHandlerProvider implements ExceptionHandlerProvider {
    @Override
    public <ReqT, RespT> ExceptionHandler<ReqT, RespT> createHandler(ServerCall.Listener<ReqT> listener,
            ServerCall<ReqT, RespT> call, Metadata metadata) {
        return new DefaultExceptionHandler<>(listener, call, metadata);
    }

    private class DefaultExceptionHandler<ReqT, RespT> extends ExceptionHandler<ReqT, RespT> {
        public DefaultExceptionHandler(ServerCall.Listener<ReqT> listener, ServerCall<ReqT, RespT> call,
                Metadata metadata) {
            super(listener, call, metadata);
        }

        @Override
        protected void handleException(Throwable exception, ServerCall<ReqT, RespT> call, Metadata metadata) {
            StatusRuntimeException sre = transformToStatusRuntimeException(exception);
            Metadata trailers = sre.getTrailers() != null ? sre.getTrailers() : metadata;
            call.close(sre.getStatus(), trailers);
        }
    }
}
