package com.example.ragdemo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RagQueryPreprocessServiceTest {

    private final RagQueryPreprocessService service = new RagQueryPreprocessService();

    @Test
    void classifiesPromotionPriceProtectionQuestions() {
        RagQueryPreprocessService.RagQueryPlan plan = service.plan("这个活动价保到什么时候？");

        assertEquals("promotion_rule", plan.primaryDocType());
        assertEquals("promotion_price_protection", plan.intent());
        assertTrue(plan.rewrittenQuery().contains("价保说明"));
        assertTrue(plan.filterExpressions().getFirst().contains("doc_type == 'promotion_rule'"));
    }

    @Test
    void classifiesManualUsageQuestions() {
        RagQueryPreprocessService.RagQueryPlan plan = service.plan("空气炸锅第一次怎么用？");

        assertEquals("manual", plan.primaryDocType());
        assertEquals("manual_usage", plan.intent());
        assertTrue(plan.rewrittenQuery().contains("澄风AF-65智能空气炸锅"));
        assertTrue(plan.filterExpressions().getFirst().contains("doc_type == 'manual'"));
    }

    @Test
    void leavesUnknownQuestionsUnfiltered() {
        RagQueryPreprocessService.RagQueryPlan plan = service.plan("推荐一款蓝色手机");

        assertEquals("unknown", plan.primaryDocType());
        assertTrue(plan.filterExpressions().isEmpty());
    }
}
