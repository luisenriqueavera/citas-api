package co.fcv.citas.insurance;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping({"/api/insurance-plans", "/api/v1/catalogs/insurance-plans"})
public class InsurancePlanController {
    private final InsurancePlanRepository plans;
    public InsurancePlanController(InsurancePlanRepository plans) { this.plans = plans; }

    @GetMapping
    public List<PlanOption> activePlans() {
        return plans.findAllByActiveTrueOrderByEpsNameAscNameAsc().stream()
                .map(plan -> new PlanOption(plan.getId(), plan.getEpsName(), plan.getName()))
                .toList();
    }

    public record PlanOption(Long id, String epsName, String name) { }
}
