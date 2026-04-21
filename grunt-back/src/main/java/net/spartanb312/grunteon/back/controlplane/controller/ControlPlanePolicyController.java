package net.spartanb312.grunteon.back.controlplane.controller;

import java.util.Map;
import net.spartanb312.grunteon.back.policy.ControlPlanePolicyService;
import net.spartanb312.grunteon.back.support.ApiSupport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ControlPlanePolicyController {

    private final ControlPlanePolicyService policyService;

    public ControlPlanePolicyController(ControlPlanePolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping("/api/control/policy/profile")
    public Map<String, Object> profile() {
        Map<String, Object> result = ApiSupport.ok();
        result.putAll(policyService.describePlatformProfiles());
        return result;
    }
}
