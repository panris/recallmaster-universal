package com.recallmaster.universal.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.recallmaster.universal.model.SearchResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReciprocalRankFusionTest {

    @Test
    void rewardsDocumentsThatAppearInMultipleRankedLists() {
        SearchResult a = hit("a");
        SearchResult b = hit("b");
        SearchResult c = hit("c");

        List<SearchResult> fused = ReciprocalRankFusion.fuse(List.of(
                List.of(a, b),
                List.of(c, b)
        ), 2);

        assertThat(fused).extracting(SearchResult::id).contains("b");
        assertThat(fused).hasSize(2);
    }

    private SearchResult hit(String id) {
        return new SearchResult(id, id + " text", 1.0, Map.of());
    }
}
