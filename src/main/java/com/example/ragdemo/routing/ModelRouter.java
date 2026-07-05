package com.example.ragdemo.routing;

import com.example.ragdemo.exception.AiServiceException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private final ModelFaultInjector faultInjector;

    public ModelRouter() {
        this(new ModelFaultInjector());
    }

    public ModelRouter(ModelFaultInjector faultInjector) {
        this.faultInjector = faultInjector;
    }

    public <T, R> R execute(ModelCapability capability, List<ModelEndpoint<T>> endpoints,
            Function<ModelEndpoint<T>, R> invoker) {
        List<ModelEndpoint<T>> activeEndpoints = endpoints.stream()
                .filter(ModelEndpoint::isEnabled)
                .sorted(Comparator.comparingInt(ModelEndpoint::getPriority))
                .toList();
        if (activeEndpoints.isEmpty()) {
            throw new AiServiceException("No enabled " + capability.name().toLowerCase() + " model candidates configured.", null);
        }

        List<String> skipped = new ArrayList<>();
        RuntimeException lastFailure = null;
        for (ModelEndpoint<T> endpoint : activeEndpoints) {
            ModelCircuitBreaker.Permit permit;
            try {
                permit = endpoint.getCircuitBreaker().acquirePermit();
            } catch (ModelCircuitOpenException ex) {
                skipped.add(endpoint.getId() + "[" + ex.getState() + "]");
                ModelRouteTraceContext.record(new ModelRouteEvent(capability, endpoint.getId(), endpoint.getProvider(),
                        "SKIPPED", ex.getState(), "Circuit is " + ex.getState()));
                continue;
            }

            try {
                ModelRouteTraceContext.record(new ModelRouteEvent(capability, endpoint.getId(), endpoint.getProvider(),
                        "ATTEMPT", endpoint.getCircuitBreaker().snapshot().state(), "Trying candidate"));
                if (faultInjector.shouldFail(endpoint.getId())) {
                    throw new IllegalStateException("Injected test failure for " + endpoint.getId());
                }
                R result = invoker.apply(endpoint);
                endpoint.getCircuitBreaker().onSuccess(permit);
                ModelRouteTraceContext.record(new ModelRouteEvent(capability, endpoint.getId(), endpoint.getProvider(),
                        "SUCCESS", endpoint.getCircuitBreaker().snapshot().state(), "Candidate returned successfully"));
                return result;
            } catch (RuntimeException ex) {
                endpoint.getCircuitBreaker().onFailure(permit);
                lastFailure = ex;
                ModelRouteTraceContext.record(new ModelRouteEvent(capability, endpoint.getId(), endpoint.getProvider(),
                        "FAILURE", endpoint.getCircuitBreaker().snapshot().state(), ex.getMessage()));
                log.warn("Model {} for capability {} failed, trying next candidate.", endpoint.getId(), capability, ex);
            }
        }

        String message = "All " + capability.name().toLowerCase()
                + " model candidates are unavailable. Skipped=" + skipped;
        throw new AiServiceException(message, lastFailure);
    }
}
