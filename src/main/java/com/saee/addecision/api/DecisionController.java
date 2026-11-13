package com.saee.addecision.api;

import com.saee.addecision.service.DecisionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class DecisionController {

    private final DecisionService decisionService;

    public DecisionController(DecisionService decisionService) {
        this.decisionService = decisionService;
    }

    /** Returns the best ad for the request, or 204 No Content if no campaign is eligible. */
    @PostMapping("/decide")
    public ResponseEntity<DecisionResponse> decide(@Valid @RequestBody DecisionRequest request) {
        return decisionService.decide(request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Records a click. Spend is charged asynchronously by the Kafka consumer. */
    @PostMapping("/events/click")
    public ResponseEntity<Void> click(@Valid @RequestBody ClickRequest request) {
        return decisionService.recordClick(request)
                ? ResponseEntity.accepted().build()
                : ResponseEntity.notFound().build();
    }
}
