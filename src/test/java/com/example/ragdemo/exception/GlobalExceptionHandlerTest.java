package com.example.ragdemo.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ragdemo.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    @Test
    void preservesResponseStatusExceptionHttpStatusAndReason() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/store/orders/5/cancel");
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ErrorResponse> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.CONFLICT, "已支付订单不能取消"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("CONFLICT");
        assertThat(response.getBody().getMessage()).isEqualTo("已支付订单不能取消");
        assertThat(response.getBody().getPath()).isEqualTo("/api/store/orders/5/cancel");
    }
}
