package com.example.ragdemo.store;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class StoreControllerTest {

    @Mock
    private StoreProductService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StoreController(service)).build();
    }

    @Test
    void exposesProductSearchContract() throws Exception {
        ProductResponse product = new ProductResponse(
                1L, "DIG-1", "测试耳机", "无线降噪", "适合通勤", "数码家电",
                new BigDecimal("299"), new BigDecimal("399"), 10, 20,
                new BigDecimal("4.8"), "https://example.com/product.jpg", true);
        when(service.findProducts("数码家电", "耳机", true))
                .thenReturn(new ProductListResponse(List.of(product), 1));

        mockMvc.perform(get("/api/store/products")
                        .param("category", "数码家电")
                        .param("keyword", "耳机")
                        .param("featured", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].code").value("DIG-1"))
                .andExpect(jsonPath("$.items[0].name").value("测试耳机"));

        verify(service).findProducts("数码家电", "耳机", true);
    }
}
