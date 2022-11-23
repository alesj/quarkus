package io.quarkus.grpc.examples.interceptors;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import examples.GreeterGrpc;
import examples.MutinyGreeterGrpc;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;

@Path("/hello")
public class HelloWorldEndpoint {

    @GrpcClient("hello")
    GreeterGrpc.GreeterBlockingStub blockingHelloService;

    @GrpcClient("hello")
    MutinyGreeterGrpc.MutinyGreeterStub mutinyHelloService;

    @GET
    @Path("/blocking/{name}")
    public Response helloBlocking(@PathParam("name") String name) {
        HeaderClientInterceptor.invoked = false;
        return Response.ok().header("intercepted", HeaderClientInterceptor.invoked).build();
    }

    @GET
    @Path("/mutiny/{name}")
    public Uni<String> helloMutiny(@PathParam("name") String name) {
        return Uni.createFrom().nullItem();
    }
}
