package com.example.ragdemo.store;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class StoreCouponRecommendationService {
    private final List<StoreCouponStrategy> strategies;
    private final Executor executor;

    public StoreCouponRecommendationService(List<StoreCouponStrategy> strategies,
            @Qualifier("couponRecommendationExecutor") Executor executor) {
        this.strategies = strategies;
        this.executor = executor;
    }

    public StoreCouponPlan recommend(StoreCouponContext context, List<StoreUserCoupon> coupons) {
        List<CompletableFuture<List<StoreCouponCandidate>>> futures = strategies.stream()
                .map(strategy -> CompletableFuture.supplyAsync(() -> strategy.calculate(context, coupons), executor)
                        .orTimeout(200, TimeUnit.MILLISECONDS).exceptionally(error -> List.of()))
                .toList();
        List<StoreCouponCandidate> candidates = futures.stream().map(CompletableFuture::join)
                .flatMap(List::stream).sorted(Comparator.comparing(StoreCouponCandidate::discountAmount).reversed())
                .toList();
        List<StoreCouponPlan> plans = new ArrayList<>();
        search(candidates, 0, new ArrayList<>(), new HashSet<>(), context.originalAmount(), plans);
        return plans.stream().min(Comparator.comparing(StoreCouponPlan::payableAmount)
                .thenComparing(StoreCouponPlan::discountAmount, Comparator.reverseOrder())
                .thenComparing(plan -> plan.candidates().size()))
                .orElse(new StoreCouponPlan(List.of(), BigDecimal.ZERO, context.originalAmount()));
    }

    private void search(List<StoreCouponCandidate> candidates, int index,
            List<StoreCouponCandidate> selected, Set<Long> selectedIds,
            BigDecimal amount, List<StoreCouponPlan> plans) {
        if (!selected.isEmpty()) {
            BigDecimal discount = selected.stream().map(StoreCouponCandidate::discountAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).min(amount);
            plans.add(new StoreCouponPlan(List.copyOf(selected), discount, amount.subtract(discount)));
        }
        if (index >= candidates.size() || selected.size() >= 6) return;
        for (int i = index; i < candidates.size(); i++) {
            StoreCouponCandidate candidate = candidates.get(i);
            StoreCoupon coupon = candidate.userCoupon().getCoupon();
            boolean canStack = selected.isEmpty()
                    || (Boolean.TRUE.equals(coupon.getStackable())
                    && selected.stream().allMatch(existing -> Boolean.TRUE.equals(
                            existing.userCoupon().getCoupon().getStackable())));
            if (!selectedIds.contains(candidate.userCoupon().getId()) && canStack) {
                selected.add(candidate);
                selectedIds.add(candidate.userCoupon().getId());
                search(candidates, i + 1, selected, selectedIds, amount, plans);
                selectedIds.remove(candidate.userCoupon().getId());
                selected.remove(selected.size() - 1);
            }
        }
    }
}
