package io.quarkus.grpc.server.dups;

import io.grpc.examples.dups.MutinyPokeGrpc;
import io.grpc.examples.dups.PokeReply;
import io.grpc.examples.dups.PokeRequest;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;

@GrpcService
public class MPoke2Service extends MutinyPokeGrpc.PokeImplBase {
    @Override
    public Uni<PokeReply> poke(PokeRequest request) {
        return super.poke(request);
    }
}
