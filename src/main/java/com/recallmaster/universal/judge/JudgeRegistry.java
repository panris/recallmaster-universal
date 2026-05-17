package com.recallmaster.universal.judge;

import com.recallmaster.universal.config.RecallMasterProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class JudgeRegistry {

    private final Map<String, JudgeModel> judges = new LinkedHashMap<>();
    private final RecallMasterProperties properties;
    private final ObjectMapper objectMapper;

    public JudgeRegistry(
            RuleBasedJudgeModel ruleBasedJudgeModel,
            RecallMasterProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        judges.put(ruleBasedJudgeModel.name(), ruleBasedJudgeModel);
    }

    public JudgeModel get(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim();
        JudgeModel judge = judges.get(normalized);
        if (judge != null) {
            return judge;
        }
        RecallMasterProperties.Evaluator evaluator = properties.getEvaluator();
        String configuredBaseUrl = System.getenv("RECALLMASTER_LLM_BASE_URL");
        String apiKey = System.getenv("RECALLMASTER_LLM_API_KEY");
        if (configuredBaseUrl == null || configuredBaseUrl.isBlank()) {
            configuredBaseUrl = evaluator.getBaseUrl();
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = evaluator.getApiKey();
        }
        String model = normalized;
        JudgeModel created = new OpenAiCompatibleJudgeModel(normalized, model, configuredBaseUrl, apiKey, objectMapper);
        judges.put(normalized, created);
        return created;
    }
}
