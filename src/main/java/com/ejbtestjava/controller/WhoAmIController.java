package com.ejbtestjava.controller;

import com.endpointblank.Configuration;
import com.endpointblank.spring.Authenticated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The one controller behind {@code @Authenticated} rather than {@code @Authorized}.
 *
 * <p>These are two different code paths through the SDK. {@code @Authorized} asks
 * intake whether a grant covers this endpoint; {@code @Authenticated} asks only
 * whether the credential itself is good. Until now every controller here — and
 * every route in every one of the five epb_test_* applications — used
 * {@code @Authorized}, so nothing anywhere called the second interceptor. That
 * is why sc-307 survived two releases: {@code AuthorizedInterceptor} carried
 * intake's refusal status through while {@code AuthenticatedInterceptor}
 * collapsed every refusal to 401, and the only programs that would have shown
 * the difference never called it. A pass/fail count over these applications
 * cannot see a code path nothing calls.
 *
 * <p>The path carries no variables, deliberately. {@code AuthorizedInterceptor}
 * resolves the endpoint through {@code RoutePatternFinder.find(request)} — the
 * route <em>pattern</em> — while {@code AuthenticatedInterceptor} uses
 * {@code request.getRequestURI()}, the concrete URI. For {@code /whoami} the two
 * agree; for a {@code /whoami/{id}} they would not, and intake matches what it
 * was told at registration, so the mismatch would refuse every request. That
 * divergence is real and still unfixed on the SDK's master — sc-307 repaired
 * the status, not the path — so this route stays variable-free until it is.
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
}
