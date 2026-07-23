package com.example.ragdemo.store;

import java.math.BigDecimal;

public record StoreCouponLine(Long productId, String category, BigDecimal amount) { }
