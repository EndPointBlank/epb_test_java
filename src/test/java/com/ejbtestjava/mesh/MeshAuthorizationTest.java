package com.ejbtestjava.mesh;

import com.ejbtestjava.controller.MeshController;
import com.ejbtestjava.controller.GlobalExceptionHandler;
import com.ejbtestjava.support.IntakeGrant;
import com.endpointblank.Configuration;
import com.endpointblank.spring.Authorized;
import com.endpointblank.spring.AuthorizedInterceptor;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The mesh endpoints must sit behind the same EndPointBlank authorization as the
 * demo CRUD routes — an unprotected relay would prove nothing about
 * cross-organization authorization, which is the entire reason the mesh exists.
 *
 * <p>The SDK's real {@link AuthorizedInterceptor} runs here against an in-process
 * stub standing in for intake, so the decision is genuinely made by the SDK and
 * the test still needs nothing running.
 */
class MeshAuthorizationTest {

    private static HttpServer intake;
    private static final AtomicInteger authorizeStatus = new AtomicInteger(201);
    private static final AtomicReference<String> authorizeBody = new AtomicReference<>("{}");
    private static final AtomicInteger authorizeCalls = new AtomicInteger();

    private static String stubBaseUrl;
    private static String originalBaseUrl;
    private static String originalAppName;

    private RecordingDownstream downstream;
    private MockMvc mockMvc;

    @BeforeAll
    static void startStubIntake() throws IOException {
        intake = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        intake.createContext("/api/authorize", exchange -> {
            authorizeCalls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] body = authorizeBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(authorizeStatus.get(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        intake.start();

        Configuration config = Configuration.getInstance();
        originalBaseUrl = config.getBaseUrl();
        originalAppName = config.getAppName();
        stubBaseUrl = "http://127.0.0.1:" + intake.getAddress().getPort();
        config.setBaseUrl(stubBaseUrl);
        config.setClientId("test-client");
        config.setClientSecret("test-secret");
        config.setAppName("epb-test-java");
    }

    @AfterAll
    static void stopStubIntake() {
        intake.stop(0);
        Configuration config = Configuration.getInstance();
        config.setBaseUrl(originalBaseUrl);
        config.setAppName(originalAppName);
    }

    @BeforeEach
    void setUp() {
        downstream = new RecordingDownstream();
        MeshRelayService service =
                new MeshRelayService(new MeshConfig("https://downstream.test", "epb_test_java"), downstream);
        mockMvc = MockMvcBuilders.standaloneSetup(new MeshController(service))
                .addInterceptors(new AuthorizedInterceptor())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        authorizeCalls.set(0);
    }

    @AfterEach
    void resetStub() {
        authorizeStatus.set(201);
        authorizeBody.set("{}");
    }

    /**
     * A fresh credential per request: the SDK caches successful authorizations
     * keyed on the caller's Authorization header, so reusing one would let an
     * earlier test's decision answer a later test's request.
     */
    private MvcResult call(String path, String hops) throws Exception {
        return mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Basic " + UUID.randomUUID())
                        .header(HopBudget.HOPS_HEADER, hops))
                .andReturn();
    }

    @Test
    @DisplayName("the stub's granted answer is intake's, with the caller's environment under data")
    void stubGrantsInIntakesShape() throws Exception {
        HttpResponse<String> granted = IntakeGrant.askToAuthorize(stubBaseUrl);

        assertEquals(201, granted.statusCode());
        IntakeGrant.assertIsIntakesGrant(granted.body());
    }

    @ParameterizedTest(name = "[{index}] {0} is refused when authorization refuses")
    @ValueSource(strings = {"/mesh/relay", "/mesh/reports"})
    void refusalIsHonoured(String path) throws Exception {
        authorizeStatus.set(403);
        authorizeBody.set("{\"error\":\"not granted\"}");

        MvcResult result = call(path, "3");

        assertEquals(403, result.getResponse().getStatus(),
                path + " must be refused when the authorization service refuses");
        assertEquals(1, authorizeCalls.get(), "the authorization service must actually be consulted");
        assertEquals(0, downstream.callCount(), "a refused request must not relay downstream");
    }

    @ParameterizedTest(name = "[{index}] {0} runs when authorization allows")
    @ValueSource(strings = {"/mesh/relay", "/mesh/reports"})
    void allowedRequestsRelay(String path) throws Exception {
        MvcResult result = call(path, "3");

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(1, authorizeCalls.get());
        assertEquals(1, downstream.callCount());
    }

    @Test
    @DisplayName("an authorization service that cannot answer does not let the request through")
    void unavailableAuthorizationDoesNotFailOpen() throws Exception {
        authorizeStatus.set(500);

        MvcResult result = call("/mesh/relay", "3");

        assertNotEquals(200, result.getResponse().getStatus());
        assertEquals(0, downstream.callCount());
    }

    @Test
    @DisplayName("the controller carries @Authorized, like the demo CRUD controllers")
    void controllerIsAnnotated() {
        assertTrue(MeshController.class.isAnnotationPresent(Authorized.class),
                "MeshController must be annotated @Authorized");
    }

    @Test
    @DisplayName("both mesh handlers are recognised as protected by the SDK's own rule")
    void bothHandlersAreProtected() {
        List<String> handlers = List.of("relay", "reports");
        for (String handler : handlers) {
            boolean protectedByAnnotation = java.util.Arrays.stream(MeshController.class.getDeclaredMethods())
                    .filter(method -> method.getName().equals(handler))
                    .anyMatch(method -> method.isAnnotationPresent(Authorized.class)
                            || method.getDeclaringClass().isAnnotationPresent(Authorized.class));
            assertTrue(protectedByAnnotation, handler + " must be behind @Authorized");
        }
    }
}
