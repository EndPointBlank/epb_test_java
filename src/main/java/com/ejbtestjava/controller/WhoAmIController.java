package com.ejbtestjava.controller;

import com.endpointblank.Configuration;
import com.endpointblank.spring.Authenticated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The one controller behind {@code @Authenticated} rather than {@code @Authorized}.
 *
 * <p>These are two different code paths through the SDK. {@code @Authorized} asks
 * intake whether a grant covers this endpoint; {@code @Authenticated} asks only
 * whether the credential itself is good. Until sc-307 every controller here — and
 * every route in every one of the five epb_test_* applications — used
 * {@code @Authorized}, so nothing anywhere called the second interceptor. That
 * is why sc-307 survived two releases: {@code AuthorizedInterceptor} carried
 * intake's refusal status through while {@code AuthenticatedInterceptor}
 * collapsed every refusal to 401, and the only programs that would have shown
 * the difference never called it. A pass/fail count over these applications
 * cannot see a code path nothing calls.
 *
 * <p>{@link #show} carries a path variable, and that is the point of it. The two
 * interceptors used to resolve the endpoint differently:
 * {@code AuthorizedInterceptor} through {@code RoutePatternFinder.find(request)} —
 * the route <em>pattern</em>, {@code /whoami/:id} — and
 * {@code AuthenticatedInterceptor} through {@code request.getRequestURI()}, the
 * concrete URI, {@code /whoami/7}. Intake matches what registration told it, so
 * the authenticate call asked about an endpoint that does not exist and every
 * request through it would have been refused. This controller was variable-free
 * for exactly as long as that was true; both interceptors now share
 * {@code RoutePatternFinder}, so {@link #index} and {@link #show} between them
 * cover the shape that agreed by accident and the shape that did not.
 *
 * <p>No {@code @Versioned}: endpoint versions belong to the authorize path,
 * which resolves a specific endpoint. Authentication judges the credential.
 */
@RestController
@RequestMapping("/whoami")
@Authenticated
public class WhoAmIController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> index() {
        return ResponseEntity.ok(Map.of(
                "application", String.valueOf(Configuration.getInstance().getAppName()),
                "authenticated", true));
    }

    /**
     * The same answer, behind a path variable.
     *
     * <p>The {@code id} is echoed rather than looked up: this route exists to put
     * a {@code {id}} in front of the interceptor, not to model anything. What
     * matters is the path the guard reports to intake, which
     * {@code AuthenticateRefusalStatusTest} asserts is {@code /whoami/:id} and
     * never the concrete {@code /whoami/7}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> show(@PathVariable String id) {
        return ResponseEntity.ok(Map.of(
                "application", String.valueOf(Configuration.getInstance().getAppName()),
                "authenticated", true,
                "id", id));
    }
}
