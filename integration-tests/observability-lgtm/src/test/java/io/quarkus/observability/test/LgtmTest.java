package io.quarkus.observability.test;

import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkus.observability.test.support.GrafanaClient;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
@DisabledOnOs(OS.WINDOWS)
public class LgtmTest {

    @ConfigProperty(name = "quarkus.grafana.url")
    String url;

    @Test
    public void testTracing() throws Exception {
        String response = RestAssured.get("/api/poke?f=100").body().asString();
        System.out.println(response);
        GrafanaClient client = new GrafanaClient("http://" + url, "admin", "admin");
        Awaitility.await().atMost(61, TimeUnit.SECONDS).until(
                client::user,
                u -> "admin".equals(u.login));
        Awaitility.await().atMost(61, TimeUnit.SECONDS).until(
                () -> client.query("xvalue_X"),
                result -> !result.data.result.isEmpty());
    }

}
