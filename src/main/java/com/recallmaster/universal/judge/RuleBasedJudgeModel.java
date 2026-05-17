package com.recallmaster.universal.judge;

import com.recallmaster.universal.model.EvaluationCase;
import com.recallmaster.universal.model.JudgeVerdict;
import com.recallmaster.universal.model.SearchResult;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedJudgeModel implements JudgeModel {

    @Override
    public String name() {
        return "rule-based";
    }

    @Override
    public JudgeVerdict judge(EvaluationCase evaluationCase, List<SearchResult> retrieved) {
        String merged = normalize(retrieved.stream()
                .map(SearchResult::text)
                .reduce("", (left, right) -> left + "\n" + right));
        List<String> covered = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String intent : evaluationCase.intents()) {
            if (covers(merged, intent)) {
                covered.add(intent);
            } else {
                missing.add(intent);
            }
        }
        double coverage = evaluationCase.intents().isEmpty()
                ? 1.0
                : (double) covered.size() / evaluationCase.intents().size();
        double noiseRatio = estimateNoise(evaluationCase, retrieved);
        int score = (int) Math.round((coverage * 0.7 + (1.0 - noiseRatio) * 0.3) * 100);
        String summary = missing.isEmpty()
                ? "检索片段覆盖了全部子意图。"
                : "缺失子意图：" + String.join("、", missing) + "。";
        String suggestion = missing.isEmpty()
                ? "可继续扩大 Case 集合，验证不同问法下的稳定性。"
                : "建议检查缺失意图相关文档的分块、向量化和过滤条件。";
        return new JudgeVerdict(name(), score, covered, missing, noiseRatio, summary, suggestion);
    }

    private boolean covers(String merged, String intent) {
        String normalizedIntent = normalize(intent);
        if (normalizedIntent.isBlank()) {
            return true;
        }
        if (merged.contains(normalizedIntent)) {
            return true;
        }
        Set<String> terms = terms(normalizedIntent);
        if (terms.isEmpty()) {
            return false;
        }
        long matched = terms.stream().filter(merged::contains).count();
        return matched >= Math.max(1, Math.ceil(terms.size() * 0.5));
    }

    private double estimateNoise(EvaluationCase evaluationCase, List<SearchResult> retrieved) {
        if (retrieved.isEmpty()) {
            return 1.0;
        }
        Set<String> queryTerms = terms(normalize(evaluationCase.question() + " " + String.join(" ", evaluationCase.intents())));
        if (queryTerms.isEmpty()) {
            return 0;
        }
        long irrelevant = retrieved.stream()
                .filter(result -> terms(normalize(result.text())).stream().noneMatch(queryTerms::contains))
                .count();
        return (double) irrelevant / retrieved.size();
    }

    private Set<String> terms(String value) {
        Set<String> terms = new LinkedHashSet<>();
        StringBuilder ascii = new StringBuilder();
        for (int offset = 0; offset < value.length(); ) {
            int cp = value.codePointAt(offset);
            if (Character.isLetterOrDigit(cp) && cp < 128) {
                ascii.appendCodePoint(cp);
            } else {
                flushAscii(ascii, terms);
                if (!Character.isWhitespace(cp) && !Character.isISOControl(cp)) {
                    terms.add(new String(Character.toChars(cp)));
                }
            }
            offset += Character.charCount(cp);
        }
        flushAscii(ascii, terms);
        return terms;
    }

    private void flushAscii(StringBuilder ascii, Set<String> terms) {
        if (ascii.length() > 1) {
            terms.add(ascii.toString());
        }
        ascii.setLength(0);
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\p{Punct}+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
