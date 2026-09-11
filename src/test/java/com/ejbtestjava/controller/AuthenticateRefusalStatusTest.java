package com.ejbtestjava.controller;

import com.ejbtestjava.support.IntakeGrant;
import com.endpointblank.Configuration;
import com.endpointblank.spring.Authenticated;
import com.endpointblank.spring.AuthenticatedInterceptor;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * What the caller is told when intake refuses an {@code @Authenticated} handler.
 *
 * <p>This class exists because of a hole rather than a feature. No controller in
 * this application — and no route in any of the five epb_test_* applications —
 * sat behind {@code @Authenticated}; every protected controller used
 * {@code @Authorized}. That is why sc-307 survived two releases:
 * {@code AuthorizedInterceptor} passed intake's refusal status through while
 * {@code AuthenticatedInterceptor} dropped it and every refusal reached the
 * caller as a 401 — including the 403 that means the credential was fine and no
 * grant covers the endpoint. Nothing went red because nothing called it. A
 * pass/fail count over these applications cannot see a code path nothing calls.
 *
 * <p>Modelled on {@link com.ejbtestjava.mesh.MeshAuthorizationTest}: the SDK's
 * real interceptors run against an in-process stub standing in for intake, so
 * the decision is genuinely made by the SDK and nothing needs to be running.
 * Both interceptors are registered, as the SDK's auto-configuration registers
 * them in the real application, which is what makes the parity test below an
 * honest comparison rather than two separately-wired setups.
 *
 * <p>The contract, from the SDK's own {@code UnauthorizedException.refusalFrom}:
 *
 * <pre>
 *   intake answered 401         -&gt; caller sees 401  (re-check the credential)
 *   intake answered 403         -&gt; caller sees 403  (ask for a grant)
 *   intake answered any non-201 -&gt; caller sees that status, verbatim
 *   intake did not answer       -&gt; caller sees 503  (nothing judged this caller)
 * </pre>
 */
class AuthenticateRefusalStatusTest {

    private static HttpServer intake;
    private static final AtomicInteger authorizeStatus = new AtomicInteger(201);
    private static final AtomicReference<String> authorizeBody = new AtomicReference<>("{}");
    private static final AtomicReference<String> lastRequestBody = new AtomicReference<>("");
    private static final AtomicInteger authorizeCalls = new AtomicInteger();

    private static String stubBaseUrl;
    private static String originalBaseUrl;
    private static String originalAppName;

    private MockMvc mockMvc;

    /**
     * A controller behind the other guard, for the parity test. Deliberately
     * declared here rather than borrowed from the application: the point is to
     * compare the two interceptors against one identical answer from intake,
     * not to re-test any particular controller.
     */
    @RestController
    @RequestMapping("/parity-probe")
    @Authorized
    static class AuthorizedProbe {
        @GetMapping
        ResponseEntity<Map<String, Object>> index() {
            return ResponseEntity.ok(Map.of("ok", true));
        }

        /** Mirrors {@code WhoAmIController#show}, so the two guards are asked about one shape. */
        @GetMapping("/{id}")
        ResponseEntity<Map<String, Object>> show(@PathVariable String id) {
            return ResponseEntity.ok(Map.of("ok", true, "id", id));
        }
    }

    @BeforeAll
    static void startStubIntake() throws IOException {
        intake = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        intake.createContext("/api/authorize", exchange -> {
            authorizeCalls.incrementAndGet();
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = authorizeBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(authorizeStatus.get(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        intake.start();

        stubBaseUrl = "http://127.0.0.1:" + intake.getAddress().getPort();

        Configuration config = Configuration.getInstance();
        originalBaseUrl = config.getBaseUrl();
        originalAppName = config.getAppName();
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
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WhoAmIController(), new AuthorizedProbe())
                .addInterceptors(new AuthenticatedInterceptor(), new AuthorizedInterceptor())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        authorizeCalls.set(0);
        Configuration.getInstance().setBaseUrl(stubBaseUrl);
    }

    @AfterEach
    void resetStub() {
        authorizeStatus.set(201);
        authorizeBody.set("{}");
        Configuration.getInstance().setBaseUrl(stubBaseUrl);
    }

    /**
     * A fresh credential per request: the SDK caches a successful authorization
     * keyed on the caller's Authorization header, so reusing one would let an
     * earlier test's decision answer a later test's request.
     */
    private MvcResult call(String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Basic " + UUID.randomUUID()))
                .andReturn();
    }

    /** Point the SDK at a port with nothing behind it. */
    private static void pointAtUnreachableIntake() throws IOException {
        int port;
        try (ServerSocket probe = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            port = probe.getLocalPort();
        }
        Configuration.getInstance().setBaseUrl("http://127.0.0.1:" + port);
    }

    // ----------------------------------------------------------------------
    // The stub grants the way intake does
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("the stub's granted answer is intake's, with the caller's environment under data")
    void stubGrantsInIntakesShape() throws Exception {
        HttpResponse<String> granted = IntakeGrant.askToAuthorize(stubBaseUrl);

        assertEquals(201, granted.statusCode());
        IntakeGrant.assertIsIntakesGrant(granted.body());
    }

    // ----------------------------------------------------------------------
    // The route exists and goes through the authenticate path
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("GET /whoami is served when intake accepts the credential")
    void servedWhenAccepted() throws Exception {
        MvcResult result = call("/whoami");

        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("\"authenticated\":true"));
        assertEquals(1, authorizeCalls.get(), "intake must actually be consulted");
    }

    @Test
    @DisplayName("WhoAmIController carries @Authenticated, and nothing else here does")
    void controllerIsAnnotated() {
        assertTrue(WhoAmIController.class.isAnnotationPresent(Authenticated.class),
                "WhoAmIController must be annotated @Authenticated — it is the only route in "
                        + "this application that reaches the SDK's authenticate path");
    }

    @Test
    @DisplayName("the path intake is told about for /whoami is /whoami")
    void reportsItsOwnPath() throws Exception {
        call("/whoami");

        assertTrue(lastRequestBody.get().contains("\"path\":\"/whoami\""),
                "intake was told: " + lastRequestBody.get());
    }

    @Test
    @DisplayName("the path intake is told about for /whoami/7 is the pattern, not the URI")
    void reportsThePatternNotTheUri() throws Exception {
        call("/whoami/7");

        // The test the variable-free route could not carry. AuthenticatedInterceptor
        // built the path from request.getRequestURI() — the concrete URI —
        // while AuthorizedInterceptor used the route pattern via
        // RoutePatternFinder. On a path with no variables the two agree, which
        // is why this controller had none and why the assertion above passed
        // against an interceptor that was wrong. Intake matches what
        // registration told it, and registration reports the pattern, so
        // /whoami/7 names an endpoint intake has never heard of and every
        // request through this route would have been refused.
        assertTrue(lastRequestBody.get().contains("\"path\":\"/whoami/:id\""),
                "intake must be told the registered pattern, not the concrete URI; "
                        + "intake was told: " + lastRequestBody.get());
    }

    @Test
    @DisplayName("both guards name a parameterised route the way it was registered")
    void guardsAgreeOnPath() throws Exception {
        // The path half of the refusal parity below. The status divergence was
        // caught by asking both guards about one refusal; this asks both about
        // one route shape. Neither call fails — intake answers 201 for both —
        // which is exactly what made the path bug survivable: nothing in the
        // application could see it, only intake could.
        call("/whoami/7");
        String viaAuthenticate = lastRequestBody.get();

        call("/parity-probe/7");
        String viaAuthorize = lastRequestBody.get();

        assertTrue(viaAuthenticate.contains("\"path\":\"/whoami/:id\""),
                "authenticate named it: " + viaAuthenticate);
        assertTrue(viaAuthorize.contains("\"path\":\"/parity-probe/:id\""),
                "authorize named it: " + viaAuthorize);
    }

    // ----------------------------------------------------------------------
    // The contract: intake's own status, not a blanket 401
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("a 403 from intake reaches the caller as 403, not 401")
    void forbiddenIsNotCollapsedTo401() throws Exception {
        authorizeStatus.set(403);
        authorizeBody.set("{\"error\":\"access_denied\"}");

        MvcResult result = call("/whoami");

        assertEquals(403, result.getResponse().getStatus(),
                "the whole point of sc-307: 403 means ask for a grant, 401 means re-check the "
                        + "credential, and collapsing them sends integrators to debug the wrong thing");
        assertTrue(result.getResponse().getContentAsString().contains("access_denied"),
                "intake's reason must survive too, not just its status");
    }

    @Test
    @DisplayName("a 401 from intake still reaches the caller as 401")
    void unauthorizedStaysUnauthorized() throws Exception {
        authorizeStatus.set(401);
        authorizeBody.set("{\"error\":\"invalid_credentials\"}");

        MvcResult result = call("/whoami");

        assertEquals(401, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("invalid_credentials"));
    }

    @Test
    @DisplayName("an unreachable intake reaches the caller as 503, not 401")
    void unreachableIntakeIs503() throws Exception {
        pointAtUnreachableIntake();

        MvcResult result = call("/whoami");

        assertEquals(503, result.getResponse().getStatus(),
                "nothing judged this caller, so 401 would blame a credential no one looked at");
        assertEquals(0, authorizeCalls.get(), "the stub must not have been reached at all");
    }

    @ParameterizedTest(name = "[{index}] intake answering {0} reaches the caller as {0}")
    @ValueSource(ints = {400, 429, 500, 502})
    @DisplayName("any other status intake invents is passed through verbatim")
    void otherStatusesArePassedThrough(int status) throws Exception {
        authorizeStatus.set(status);
        authorizeBody.set("{\"error\":\"whatever intake said\"}");

        MvcResult result = call("/whoami");

        assertEquals(status, result.getResponse().getStatus(),
                "the interceptor forwards intake's verdict rather than classifying it");
    }

    // ----------------------------------------------------------------------
    // Parity: the two interceptors must answer one refusal the same way
    // ----------------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] both guards answer {0} for a {0} from intake")
    @ValueSource(ints = {401, 403, 429})
    @DisplayName("both guards give the caller the same status for the same refusal")
    void guardsAgreeOnRefusalStatus(int status) throws Exception {
        // The regression test for sc-307 proper. One intake, one answer, two
        // routes: /whoami through @Authenticated, /parity-probe through
        // @Authorized. Before the fix these returned 401 and 403 for one
        // identical refusal, and no application called the first one.
        authorizeStatus.set(status);
        authorizeBody.set("{\"error\":\"access_denied\"}");

        MvcResult viaAuthenticate = call("/whoami");
        MvcResult viaAuthorize = call("/parity-probe");

        assertEquals(status, viaAuthenticate.getResponse().getStatus(),
                "authenticate lost intake's " + status);
        assertEquals(status, viaAuthorize.getResponse().getStatus(),
                "authorize lost intake's " + status);
        assertEquals(viaAuthenticate.getResponse().getStatus(), viaAuthorize.getResponse().getStatus(),
                "the two guards disagree about a " + status + " from intake");
    }

    @Test
    @DisplayName("both guards answer 503 when intake cannot be reached")
    void guardsAgreeOnUnreachable() throws Exception {
        pointAtUnreachableIntake();

        MvcResult viaAuthenticate = call("/whoami");
        MvcResult viaAuthorize = call("/parity-probe");

        assertEquals(503, viaAuthenticate.getResponse().getStatus());
        assertEquals(503, viaAuthorize.getResponse().getStatus());
        assertNotNull(viaAuthenticate.getResponse().getContentAsString());
    }
}
