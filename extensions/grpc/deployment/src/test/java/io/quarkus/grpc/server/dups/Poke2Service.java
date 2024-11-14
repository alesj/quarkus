package io.quarkus.grpc.server.dups;

import io.grpc.examples.dups.PokeGrpc;
import io.grpc.examples.dups.PokeReply;
import io.grpc.examples.dups.PokeRequest;
import io.grpc.stub.StreamObserver;
import io.quarkus.grpc.GrpcService;

@GrpcService
public class Poke2Service extends PokeGrpc.PokeImplBase {
    @Override
    public void poke(PokeRequest request, StreamObserver<PokeReply> responseObserver) {
    }
}
