package io.quarkus.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;

/**
 * Provider for ExceptionHandler.
 *
 * To use a custom ExceptionHandler, extend {@link ExceptionHandler} and implement
 * an {@link ExceptionHandlerProvider}, and expose it as a CDI bean.
 */
public interface ExceptionHandlerProvider {
    <ReqT, RespT> ExceptionHandler<ReqT, RespT> createHandler(Listener<ReqT> listener,
            ServerCall<ReqT, RespT> serverCall, Metadata metadata);

    default StatusRuntimeException transformToStatusRuntimeException(Throwable t) {
        return toStatusRuntimeException(t);
    }

    static StatusRuntimeException toStatusRuntimeException(Throwable t) {
        if (t instanceof StatusRuntimeException) {
            return (StatusRuntimeException) t;
        } else if (t instanceof StatusException) {
            StatusException se = (StatusException) t;
            return new StatusRuntimeException(se.getStatus(), se.getTrailers());
        } else {
            String desc = t.getClass().getName();
            if (t.getMessage() != null) {
                desc += " - " + t.getMessage();
            }
            if (t instanceof IllegalArgumentException) {
                return Status.INVALID_ARGUMENT.withDescription(desc).asRuntimeException();
            }
            return Status.fromThrowable(t).withDescription(desc).asRuntimeException();
        }
    }
}
