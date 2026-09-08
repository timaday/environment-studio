package studio.environment.server;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpBoundaryTest {
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> request(String path, boolean post) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (post) builder.POST(HttpRequest.BodyPublishers.ofString("{}"));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void capabilitiesCannotAdvertiseUnimplementedExport() throws Exception {
        var response = request("/api/v1/capabilities", false);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"exportEnabled\":false"));
        assertTrue(response.body().contains("\"inspectionEnabled\":false"));
        assertTrue(response.headers().firstValue("Content-Security-Policy").orElseThrow().contains("frame-ancestors 'none'"));
    }

    @Test void directExportOrInspectionRequestsAreDenied() throws Exception {
        assertEquals(403, request("/api/v1/plans/demo/artifacts", true).statusCode());
        assertEquals(403, request("/api/v1/plans/demo/inspections", true).statusCode());
    }

    @Test void readinessReportsOnlyProcessHealth() throws Exception {
        assertEquals(200, request("/actuator/health/readiness", false).statusCode());
    }
}
