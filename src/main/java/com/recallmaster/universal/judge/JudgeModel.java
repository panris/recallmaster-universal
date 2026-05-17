package com.recallmaster.universal.judge;

import com.recallmaster.universal.model.EvaluationCase;
import com.recallmaster.universal.model.JudgeVerdict;
import com.recallmaster.universal.model.SearchResult;
import java.util.List;

public interface JudgeModel {

    String name();

    JudgeVerdict judge(EvaluationCase evaluationCase, List<SearchResult> retrieved);
}
