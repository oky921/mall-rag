package com.example.ragdemo.store;

import java.math.BigDecimal;
import java.util.List;

public record StoreCouponContext(List<StoreCouponLine> lines, BigDecimal originalAmount) { }
