package com.recallmaster.universal.web;

import com.recallmaster.universal.connector.ConnectorRegistry;
import com.recallmaster.universal.connector.ConnectorDescriptor;
import com.recallmaster.universal.task.EvaluationRunService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final ConnectorRegistry connectorRegistry;
    private final EvaluationRunService evaluationRunService;

    public DashboardController(ConnectorRegistry connectorRegistry, EvaluationRunService evaluationRunService) {
        this.connectorRegistry = connectorRegistry;
        this.evaluationRunService = evaluationRunService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("connectors", connectorRegistry.describeAll());
        model.addAttribute("runs", evaluationRunService.list());
        return "dashboard";
    }
}
