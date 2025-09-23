package io.quarkus.it.pulsar;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import examples.GpReply;
import examples.GpRequest;
import examples.GreeterGrpc;
import io.grpc.Channel;
import io.quarkus.grpc.test.utils.GRPCTestUtils;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class GreeterTest {

    @Test
    public void testProto4() {
        Channel channel = GRPCTestUtils.channel(null);
        try {
            GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
            GpRequest request = GpRequest.newBuilder().setName("Pulsar").build();
            GpReply reply = stub.sayHello(request);
            Assertions.assertEquals("Hello Pulsar", reply.getMessage());
        } finally {
            GRPCTestUtils.close(channel);
        }
    }
}
