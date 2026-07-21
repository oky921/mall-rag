package com.example.ragdemo.store;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreProductRepository extends JpaRepository<StoreProduct, Long> {

    List<StoreProduct> findByCodeIn(Collection<String> codes);

    List<StoreProduct> findByCodeInAndActiveTrue(Collection<String> codes);

    Optional<StoreProduct> findByCode(String code);

    List<StoreProduct> findAllByActiveTrueOrderByIdAsc();

    @Query("""
            select product from StoreProduct product
            where product.active = true
              and (:category is null or lower(product.category) = lower(:category))
              and (:keyword is null
                   or lower(product.name) like lower(concat('%', :keyword, '%'))
                   or lower(product.subtitle) like lower(concat('%', :keyword, '%'))
                   or lower(product.description) like lower(concat('%', :keyword, '%')))
              and (:featured is null or product.featured = :featured)
            order by product.featured desc, product.sales desc, product.id asc
            """)
    List<StoreProduct> search(
            @Param("category") String category,
            @Param("keyword") String keyword,
            @Param("featured") Boolean featured);

    @Query("select product.category, count(product) from StoreProduct product where product.active = true group by product.category order by product.category")
    List<Object[]> countByCategory();
}
