package com.recallmaster.universal.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.connector.ConnectorRegistry;
import com.recallmaster.universal.embedding.HashEmbeddingModel;
import com.recallmaster.universal.judge.JudgeRegistry;
import com.recallmaster.universal.judge.RuleBasedJudgeModel;
import com.recallmaster.universal.model.CaseResult;
import com.recallmaster.universal.model.EvaluationCase;
import com.recallmaster.universal.model.EvaluationStatus;
import com.recallmaster.universal.model.LabelStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class EvaluationServiceTest {

    @Test
    void evaluatesHardRecallAndMarksModelLabelsForReview() {
        RecallMasterProperties properties = properties();
        HashEmbeddingModel embedding = new HashEmbeddingModel(256);
        ConnectorRegistry connectorRegistry = new ConnectorRegistry(properties, embedding, new ObjectMapper());
        JudgeRegistry judgeRegistry = new JudgeRegistry(new RuleBasedJudgeModel(), properties, new ObjectMapper());
        EvaluationService service = new EvaluationService(connectorRegistry, embedding, judgeRegistry, properties);

        EvaluationCase evaluationCase = new EvaluationCase(
                "case-1",
                "如何配置负载均衡？高可用模式下心跳间隔是多少？",
                List.of("负载均衡配置", "高可用参数"),
                List.of("tech_lb_config", "tech_ha_heartbeat"),
                Map.of(),
                LabelStatus.MODEL_PROPOSED);

        CaseResult result = service.evaluate("demo-memory", evaluationCase, 5);

        assertThat(result.status()).isEqualTo(EvaluationStatus.FULL_RECALL);
        assertThat(result.retrievalMetrics().hitIds()).containsExactlyInAnyOrder("tech_lb_config", "tech_ha_heartbeat");
        assertThat(result.aiAnalysis().needsHumanReview()).isTrue();
        assertThat(result.aiAnalysis().summary()).contains("召回内容覆盖全部子意图");
    }

    @Test
    void reportsPartialRecallWhenExpectedIdIsMissing() {
        RecallMasterProperties properties = properties();
        HashEmbeddingModel embedding = new HashEmbeddingModel(256);
        ConnectorRegistry connectorRegistry = new ConnectorRegistry(properties, embedding, new ObjectMapper());
        JudgeRegistry judgeRegistry = new JudgeRegistry(new RuleBasedJudgeModel(), properties, new ObjectMapper());
        EvaluationService service = new EvaluationService(connectorRegistry, embedding, judgeRegistry, properties);

        EvaluationCase evaluationCase = new EvaluationCase(
                "case-2",
                "牙科报销上限是多少？",
                List.of("报销额度"),
                List.of("med_dental_limit", "missing_doc"),
                Map.of(),
                LabelStatus.HUMAN_VERIFIED);

        CaseResult result = service.evaluate("demo-memory", evaluationCase, 5);

        assertThat(result.status()).isEqualTo(EvaluationStatus.PARTIAL_RECALL);
        assertThat(result.retrievalMetrics().hitIds()).contains("med_dental_limit");
        assertThat(result.retrievalMetrics().missIds()).contains("missing_doc");
    }

    private RecallMasterProperties properties() {
        RecallMasterProperties properties = new RecallMasterProperties();
        RecallMasterProperties.Database database = new RecallMasterProperties.Database();
        database.setName("demo-memory");
        database.setType("memory");
        database.setDocuments(List.of(
                seed("tech_lb_config", "负载均衡需要配置 upstream 节点、健康检查路径和转发策略。", "tech"),
                seed("tech_ha_heartbeat", "高可用模式默认心跳间隔为 3 秒，连续 3 次失败后触发主备切换。", "tech"),
                seed("med_dental_limit", "医疗保险牙科报销年度上限为 2000 元，包含洗牙、补牙和根管治疗。", "medical")
        ));
        properties.setDatabases(List.of(database));
        properties.getEvaluator().setPrimaryJudge("rule-based");
        return properties;
    }

    private RecallMasterProperties.SeedDocument seed(String id, String text, String source) {
        RecallMasterProperties.SeedDocument seed = new RecallMasterProperties.SeedDocument();
        seed.setId(id);
        seed.setText(text);
        seed.setMetadata(Map.of("source", source));
        return seed;
    }
}
