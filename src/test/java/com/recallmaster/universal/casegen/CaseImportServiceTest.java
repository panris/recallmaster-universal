package com.recallmaster.universal.casegen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

class CaseImportServiceTest {

    @Test
    void importsAndWarnsOnModelProposedCases() {
        CaseImportService service = new CaseImportService(new ObjectMapper());
        ImportedCaseSet imported = service.importJson("""
                [
                  {
                    "question": "如何配置负载均衡？",
                    "intents": ["负载均衡配置"],
                    "expectedIds": [],
                    "filters": {},
                    "labelStatus": "MODEL_PROPOSED"
                  }
                ]
                """);

        assertThat(imported.count()).isEqualTo(1);
        assertThat(imported.labelStatusCounts()).containsEntry("MODEL_PROPOSED", 1);
        assertThat(imported.warnings()).isNotEmpty();
        assertThat(service.exportJson(imported.cases())).contains("负载均衡配置");
    }
}
