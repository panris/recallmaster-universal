package com.recallmaster.universal.web;

import com.recallmaster.universal.casegen.CaseGenerationRequest;
import com.recallmaster.universal.casegen.CaseGeneratorService;
import com.recallmaster.universal.casegen.CaseImportService;
import com.recallmaster.universal.casegen.GeneratedCaseSet;
import com.recallmaster.universal.casegen.ImportedCaseSet;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cases")
public class CaseController {

    private final CaseGeneratorService caseGeneratorService;
    private final CaseImportService caseImportService;

    public CaseController(CaseGeneratorService caseGeneratorService, CaseImportService caseImportService) {
        this.caseGeneratorService = caseGeneratorService;
        this.caseImportService = caseImportService;
    }

    @PostMapping("/generate")
    public GeneratedCaseSet generateCases(@jakarta.validation.Valid @RequestBody CaseGenerationRequest request) {
        return caseGeneratorService.generate(request);
    }

    @PostMapping("/import")
    public ImportedCaseSet importCases(@RequestBody String content,
                                       @RequestHeader(value = "X-Filename", defaultValue = "data.json") String filename) {
        return caseImportService.importCases(content, filename);
    }
}