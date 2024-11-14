package io.quarkus.grpc.server;

import static org.junit.jupiter.api.Assertions.fail;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.protobuf.EmptyProtos;

import io.grpc.examples.helloworld.MutinyGreeterGrpc;
import io.grpc.testing.integration.Messages;
import io.grpc.testing.integration.MutinyTestServiceGrpc;
import io.grpc.testing.integration.TestServiceGrpc;
import io.quarkus.grpc.server.services.MutinyTestService;
import io.quarkus.grpc.server.services.TestService;
import io.quarkus.test.QuarkusUnitTest;

public class DuplicateServiceTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestService.class, MutinyTestService.class, // teste 2 are dups!
                            MutinyGreeterGrpc.class,
                            EmptyProtos.class, Messages.class,
                            MutinyTestServiceGrpc.class, TestServiceGrpc.class))
            .assertException(t -> {
                Assertions.assertTrue(t.getMessage().contains("Duplicate service impl"));
            });

    @Test
    void testDuplicateService() {
        fail("Should not be called");
    }

}
