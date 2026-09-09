package com.ejbtestjava.controller;

import com.ejbtestjava.mesh.HopBudget;
import com.ejbtestjava.mesh.MeshRelayService;
import com.ejbtestjava.mesh.MeshRequestBody;
import com.endpointblank.spring.Authorized;
import com.endpointblank.spring.Versioned;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * The sc-263 mesh endpoints, behind the same {@link Authorized} check as the
 * demo CRUD controllers. Exercising that authorization is the point: an
 * unprotected relay endpoint would prove nothing.
 *
 * <p>{@code /mesh/relay} is the mesh call, granted to the client organization.
 * {@code /mesh/reports} is the negative control: same shape, same behaviour,
 * deliberately not granted, so a refusal can be observed.
 */
@RestController
@RequestMapping("/mesh")
@Authorized
public class MeshController {

    private final MeshRelayService relayService;

    public MeshController(MeshRelayService relayService) {
        this.relayService = relayService;
    }

    @PostMapping("/relay")
    @Versioned(versions = {"1"})
    public ResponseEntity<Map<String, Object>> relay(HttpServletRequest request) {
        return handle(request, "/mesh/relay");
    }

    @PostMapping("/reports")
    @Versioned(versions = {"1"})
    public ResponseEntity<Map<String, Object>> reports(HttpServletRequest request) {
        return handle(request, "/mesh/reports");
    }

    private ResponseEntity<Map<String, Object>> handle(HttpServletRequest request, String path) {
        Object payload;
        try {
            payload = MeshRequestBody.payloadOf(request);
        } catch (IOException e) {
            return relayService.invalidBody(e.getMessage());
        }

        // getHeader returns element 0 when the header repeats, which is the
        // value the contract starts from; HopBudget.parse then takes what comes
        // before the first comma of it, so a folded "4, 8" is still 4. The run
        // id gets element 0 and nothing else: it may legitimately contain a
        // comma, and "forwarded verbatim" outranks the split.
        return relayService.relay(
                path,
                request.getHeader(HopBudget.HOPS_HEADER),
                request.getHeader(HopBudget.RUN_HEADER),
                payload);
    }
}
