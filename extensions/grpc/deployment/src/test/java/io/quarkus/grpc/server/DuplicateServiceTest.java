package io.quarkus.grpc.server;

import static org.junit.jupiter.api.Assertions.fail;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.grpc.examples.dups.Poke;
import io.quarkus.grpc.server.dups.MutinyPokeService;
import io.quarkus.grpc.server.dups.PokeService;
import io.quarkus.test.QuarkusUnitTest;

// 1 implBase + 1 bean
public class DuplicateServiceTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MutinyPokeService.class, PokeService.class)
                    .addPackage(Poke.class.getPackage()))
            .assertException(t -> {
                Assertions.assertTrue(t.getMessage().contains("Duplicated gRPC service"));
            });

    @Test
    void testDuplicateService() {
        fail("Should not be called");
    }

}
