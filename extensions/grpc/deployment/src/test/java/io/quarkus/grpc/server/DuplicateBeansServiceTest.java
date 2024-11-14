package io.quarkus.grpc.server;

import static org.junit.jupiter.api.Assertions.fail;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.grpc.examples.dups.Poke;
import io.quarkus.grpc.server.dups.MutinyPoke2Service;
import io.quarkus.grpc.server.dups.MutinyPokeService;
import io.quarkus.test.QuarkusUnitTest;

// 2 same beans services
public class DuplicateBeansServiceTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MutinyPokeService.class, MutinyPoke2Service.class)
                    .addPackage(Poke.class.getPackage()))
            .assertException(t -> {
                // fails with CDI / Arc
                Assertions.assertTrue(t.getMessage().contains("Ambiguous dependencies"));
            });

    @Test
    void testDuplicateService() {
        fail("Should not be called");
    }

}
