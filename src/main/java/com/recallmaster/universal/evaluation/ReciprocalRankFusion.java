package com.recallmaster.universal.evaluation;

import com.recallmaster.universal.model.SearchResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReciprocalRankFusion {

    private static final int DEFAULT_K = 60;

    private ReciprocalRankFusion() {
    }

    public static List<SearchResult> fuse(List<List<SearchResult>> rankedLists, int topK) {
        return fuse(rankedLists, topK, DEFAULT_K);
    }

    public static List<SearchResult> fuse(List<List<SearchResult>> rankedLists, int topK, int rankConstant) {
        Map<String, Accumulator> scores = new LinkedHashMap<>();
        for (List<SearchResult> list : rankedLists) {
            for (int index = 0; index < list.size(); index++) {
                SearchResult result = list.get(index);
                double contribution = 1.0 / (rankConstant + index + 1);
                scores.compute(result.id(), (id, existing) -> {
                    if (existing == null) {
                        return new Accumulator(result, contribution);
                    }
                    existing.score += contribution;
                    return existing;
                });
            }
        }
        return scores.values().stream()
                .sorted(Comparator.comparingDouble(Accumulator::score).reversed())
                .limit(topK)
                .map(item -> new SearchResult(
                        item.result.id(),
                        item.result.text(),
                        item.score,
                        item.result.metadata()))
                .toList();
    }

    private static final class Accumulator {
        private final SearchResult result;
        private double score;

        private Accumulator(SearchResult result, double score) {
            this.result = result;
            this.score = score;
        }

        private double score() {
            return score;
        }
    }
}
