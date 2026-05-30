package com.recallmaster.universal.casegen;

import static org.assertj.core.api.Assertions.assertThat;

import com.recallmaster.universal.document.DocumentLoader;
import com.recallmaster.universal.embedding.HashEmbeddingModel;
import com.recallmaster.universal.llm.LlmClient;
import com.recallmaster.universal.model.LabelStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CaseGeneratorServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesModelProposedCasesFromTxtDocuments() throws Exception {
        Path source = tempDir.resolve("medical.txt");
        Files.writeString(source, "医疗保险牙科报销年度上限为 2000 元，包含根管治疗。美容贴面不在保障范围内。");
        LlmClient mockLlm = new LlmClient("mock", "", "", null) {
            @Override
            public String chatJson(String systemPrompt, String userPrompt) {
                return "{\"intents\": [\"牙科保障范围\", \"报销额度\"]}";
            }
        };
        CaseGeneratorService service = new CaseGeneratorService(new DocumentLoader(), new HashEmbeddingModel(128), mockLlm);

        GeneratedCaseSet generated = service.generate(new CaseGenerationRequest(List.of(source.toString()), 3, false));

        assertThat(generated.cases()).isNotEmpty();
        assertThat(generated.cases().getFirst().labelStatus()).isEqualTo(LabelStatus.MODEL_PROPOSED);
        assertThat(generated.cases().getFirst().intents()).contains("牙科保障范围", "报销额度");
    }
}
