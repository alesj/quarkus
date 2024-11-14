package io.quarkus.grpc.server.dups;

import io.grpc.examples.dups.Poke;
import io.grpc.examples.dups.PokeReply;
import io.grpc.examples.dups.PokeRequest;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;

@GrpcService
public class MutinyPokeService implements Poke {
    @Override
    public Uni<PokeReply> poke(PokeRequest request) {
        return null;
    }
}
