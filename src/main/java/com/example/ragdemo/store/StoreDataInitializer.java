package com.example.ragdemo.store;

import java.math.BigDecimal;
import java.util.List;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StoreDataInitializer implements ApplicationRunner {

    private final StoreProductRepository repository;
    private final StoreProductCatalogService catalogService;
    private final StoreSkuRepository skuRepository;
    private final StoreCouponRepository couponRepository;

    public StoreDataInitializer(StoreProductRepository repository, StoreSkuRepository skuRepository,
            StoreProductCatalogService catalogService, StoreCouponRepository couponRepository) {
        this.repository = repository;
        this.skuRepository = skuRepository;
        this.catalogService = catalogService;
        this.couponRepository = couponRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() == 0) {
            catalogService.saveAll(List.of(
                product("DIG-1001", "星云 Pro 智能手机", "旗舰影像 · 轻薄长续航", "高亮直屏、全天候续航与专业影像系统。", "数码家电", "4299", "4699", 88, 1260, "4.9", "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=900&q=85", true),
                product("DIG-1002", "静界降噪耳机", "沉浸声场 · 40 小时续航", "舒适包耳设计，适合通勤、工作和长时间聆听。", "数码家电", "899", "1099", 156, 2384, "4.8", "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=85", true),
                product("DIG-1003", "曜石机械键盘", "热插拔轴体 · 无线三模", "紧凑配列与稳定手感，适合办公和游戏桌面。", "数码家电", "499", "599", 73, 986, "4.7", "https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=900&q=85", false),
                product("CLO-2001", "云感轻暖羽绒服", "防风面料 · 轻盈保暖", "利落廓形与轻量填充，满足冬季通勤需求。", "服饰鞋包", "699", "899", 64, 1542, "4.8", "https://images.unsplash.com/photo-1551028719-00167b16eac5?auto=format&fit=crop&w=900&q=85", true),
                product("CLO-2002", "城市缓震跑鞋", "透气鞋面 · 稳定支撑", "日常慢跑与城市步行兼顾，脚感轻快。", "服饰鞋包", "459", "559", 120, 2130, "4.9", "https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=900&q=85", true),
                product("BEA-3001", "焕亮护肤礼盒", "温和保湿 · 日夜护理", "基础护肤组合，覆盖清洁、补水和锁水步骤。", "美妆护理", "329", "399", 92, 1740, "4.8", "https://images.unsplash.com/photo-1596462502278-27bfdc403348?auto=format&fit=crop&w=900&q=85", true),
                product("HOM-4001", "手冲咖啡套装", "简洁器具 · 入门友好", "包含分享壶、滤杯与手冲壶，轻松搭建家庭咖啡角。", "家居生活", "269", "329", 45, 620, "4.7", "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=900&q=85", false),
                product("DIG-1004", "极简智能腕表", "健康监测 · 多场景运动", "清晰信息提醒与全天健康数据记录。", "数码家电", "1299", "1499", 38, 810, "4.6", "https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=900&q=85", false)
            ));
        }

        repository.findAll().stream()
                .filter(product -> !skuRepository.existsByProductId(product.getId()))
                .forEach(product -> skuRepository.saveAll(skusFor(product)));

        Instant from = Instant.now().minus(1, ChronoUnit.MINUTES);
        Instant to = Instant.now().plus(365, ChronoUnit.DAYS);
        StoreProduct phone = repository.findByCode("DIG-1001").orElse(null);
        ensureCoupon(new StoreCoupon("WELCOME-30", "满 200 减 30", StoreCoupon.Type.FULL_REDUCTION,
                new BigDecimal("200"), new BigDecimal("30"), null, true, null, null, from, to));
        ensureCoupon(new StoreCoupon("WELCOME-95", "全场 95 折", StoreCoupon.Type.DISCOUNT,
                new BigDecimal("100"), null, new BigDecimal("0.9500"), true, null, null, from, to));
        ensureCoupon(new StoreCoupon("DIGITAL-88", "数码家电 88 折", StoreCoupon.Type.CATEGORY_DISCOUNT,
                new BigDecimal("300"), null, new BigDecimal("0.8800"), true, null, "数码家电", from, to));
        ensureCoupon(new StoreCoupon("PHONE-100", "指定手机立减 100", StoreCoupon.Type.PRODUCT_FIXED,
                new BigDecimal("1000"), new BigDecimal("100"), null, false,
                phone == null ? null : phone.getId(), null, from, to));
    }

    private void ensureCoupon(StoreCoupon coupon) {
        if (couponRepository.findByCode(coupon.getCode()).isEmpty()) {
            couponRepository.save(coupon);
        }
    }

    private StoreProduct product(String code, String name, String subtitle, String description, String category,
            String price, String originalPrice, int stock, int sales, String rating, String imageUrl,
            boolean featured) {
        return new StoreProduct(code, name, subtitle, description, category, new BigDecimal(price),
                new BigDecimal(originalPrice), stock, sales, new BigDecimal(rating), imageUrl, featured);
    }

    private List<StoreSku> skusFor(StoreProduct product) {
        int firstStock = Math.max(1, product.getStock() / 2);
        int secondStock = Math.max(1, product.getStock() - firstStock);
        return switch (product.getCode()) {
            case "DIG-1001" -> List.of(
                    sku(product, "BK-256", "{\"颜色\":\"曜石黑\",\"容量\":\"256GB\"}",
                            BigDecimal.ZERO, firstStock),
                    sku(product, "WH-512", "{\"颜色\":\"云川白\",\"容量\":\"512GB\"}",
                            new BigDecimal("600"), secondStock));
            case "DIG-1002" -> List.of(
                    sku(product, "BK", "{\"颜色\":\"曜石黑\"}", BigDecimal.ZERO, firstStock),
                    sku(product, "SL", "{\"颜色\":\"雾银\"}", new BigDecimal("50"), secondStock));
            case "CLO-2001" -> List.of(
                    sku(product, "M-BK", "{\"颜色\":\"经典黑\",\"尺码\":\"M\"}",
                            BigDecimal.ZERO, firstStock),
                    sku(product, "L-GR", "{\"颜色\":\"松柏绿\",\"尺码\":\"L\"}",
                            BigDecimal.ZERO, secondStock));
            case "CLO-2002" -> List.of(
                    sku(product, "39-RD", "{\"颜色\":\"珊瑚红\",\"尺码\":\"39\"}",
                            BigDecimal.ZERO, firstStock),
                    sku(product, "42-BK", "{\"颜色\":\"深空黑\",\"尺码\":\"42\"}",
                            BigDecimal.ZERO, secondStock));
            default -> List.of(
                    sku(product, "STD", "{\"款式\":\"标准款\"}", BigDecimal.ZERO, firstStock),
                    sku(product, "PLUS", "{\"款式\":\"升级款\"}", new BigDecimal("80"), secondStock));
        };
    }

    private StoreSku sku(StoreProduct product, String suffix, String specs, BigDecimal priceOffset, int stock) {
        return new StoreSku(product, product.getCode() + "-" + suffix, specs,
                product.getPrice().add(priceOffset), product.getOriginalPrice().add(priceOffset), stock,
                product.getSales() / 2, product.getImageUrl(), true);
    }
}
