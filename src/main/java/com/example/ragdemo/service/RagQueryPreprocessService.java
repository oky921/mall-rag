package com.example.ragdemo.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RagQueryPreprocessService {

    public RagQueryPlan plan(String query) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("manual", score(query, "空气炸锅", "炸锅", "首次", "空烧", "清洁", "保养", "温度", "时间",
                "预热", "翻面", "故障", "无法启动", "白烟", "异响", "安全", "用电", "售后", "薯条", "鸡翅", "烘焙"));
        scores.put("brand_story", score(query, "素时器物", "品牌", "创始", "创始人", "林知衡", "理念", "耐用",
                "克制", "极简", "可清洁", "发展历程", "愿景", "环保", "社区", "口碑", "供应链"));
        scores.put("promotion_rule", score(query, "云集", "盛夏焕新节", "活动", "促销", "优惠", "满减", "直降",
                "优惠券", "大额券", "会员", "PLUS", "预售", "定金", "膨胀", "物流", "退货", "退款", "价保",
                "价格保护", "发票", "秒杀", "赠品"));

        int maxScore = scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (maxScore == 0) {
            return new RagQueryPlan(query, query, "general", "unknown", false, List.of());
        }

        List<String> docTypes = scores.entrySet().stream()
                .filter(entry -> entry.getValue() > 0 && entry.getValue() >= maxScore - 1)
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .toList();
        String primaryDocType = docTypes.getFirst();
        String intent = detectIntent(query, primaryDocType);
        String rewrittenQuery = rewriteQuery(query, primaryDocType, intent);
        List<String> filters = docTypes.stream()
                .map(docType -> "source == 'local-md' && doc_type == '" + docType + "'")
                .toList();
        return new RagQueryPlan(query, rewrittenQuery, intent, primaryDocType, true, filters);
    }

    private int score(String query, String... keywords) {
        int score = 0;
        for (String keyword : keywords) {
            if (query.contains(keyword)) {
                score++;
            }
        }
        return score;
    }

    private String detectIntent(String query, String docType) {
        if ("manual".equals(docType)) {
            if (containsAny(query, "故障", "报错", "无法启动", "白烟", "异响", "不熟")) {
                return "manual_troubleshooting";
            }
            if (containsAny(query, "安全", "用电", "烫伤", "插座", "注意")) {
                return "manual_safety";
            }
            return "manual_usage";
        }
        if ("promotion_rule".equals(docType)) {
            if (containsAny(query, "价保", "价格保护")) {
                return "promotion_price_protection";
            }
            if (containsAny(query, "退货", "退款", "售后", "换货")) {
                return "promotion_after_sales";
            }
            if (containsAny(query, "预售", "定金", "尾款", "膨胀")) {
                return "promotion_presale";
            }
            if (containsAny(query, "物流", "发货", "配送", "送装")) {
                return "promotion_logistics";
            }
            return "promotion_rule";
        }
        if ("brand_story".equals(docType)) {
            if (containsAny(query, "理念", "耐用", "克制", "极简", "可清洁")) {
                return "brand_philosophy";
            }
            if (containsAny(query, "创始", "林知衡", "创业")) {
                return "brand_origin";
            }
            return "brand_story";
        }
        return "general";
    }

    private String rewriteQuery(String query, String docType, String intent) {
        List<String> terms = new ArrayList<>();
        terms.add(query);
        switch (docType) {
            case "manual" -> terms.add("澄风AF-65智能空气炸锅 使用说明书 操作步骤 故障排查 安全注意事项");
            case "brand_story" -> terms.add("素时器物 品牌故事 创立背景 产品理念 发展历程 用户口碑 品牌愿景");
            case "promotion_rule" -> terms.add("云集商城盛夏焕新节 促销活动规则 活动时间 优惠券 满减 预售 物流 退换货 价保");
            default -> {
            }
        }
        switch (intent) {
            case "manual_troubleshooting" -> terms.add("故障排查 原因 处理方法 售后");
            case "manual_safety" -> terms.add("安全注意事项 用电安全 禁止事项");
            case "manual_usage" -> terms.add("首次使用 日常操作 推荐参数 清洁保养");
            case "promotion_price_protection" -> terms.add("价保说明 价格保护 申请截止时间 适用条件");
            case "promotion_after_sales" -> terms.add("退换货政策 退款规则 质量问题处理 售后");
            case "promotion_presale" -> terms.add("预售规则 定金 尾款 定金膨胀 发货");
            case "promotion_logistics" -> terms.add("物流时效 现货 大件 预约配送");
            case "brand_philosophy" -> terms.add("克制设计 耐用主义 可清洁 长期使用价值");
            case "brand_origin" -> terms.add("创始人 林知衡 创立背景 创业起点 第一款产品");
            default -> {
            }
        }
        return terms.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .reduce((left, right) -> left + "\n" + right)
                .orElse(query);
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    public record RagQueryPlan(String originalQuery, String rewrittenQuery, String intent, String primaryDocType,
            boolean strictFilter, List<String> filterExpressions) {

        boolean targetsLocalMarkdown() {
            return strictFilter && !"unknown".equals(primaryDocType);
        }
    }
}
