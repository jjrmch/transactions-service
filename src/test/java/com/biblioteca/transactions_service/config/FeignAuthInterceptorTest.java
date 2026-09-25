package com.biblioteca.transactions_service.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FeignAuthInterceptorTest {

    private final RequestInterceptor interceptor = new FeignAuthInterceptor().propagarAuthorization();

    @AfterEach
    void limpiarContexto() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void copiaElHeaderAuthorizationDeLaPeticionEntrante() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-123");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertEquals("Bearer token-123", template.headers().get(HttpHeaders.AUTHORIZATION).iterator().next());
    }

    @Test
    void sinPeticionEntranteNoAnadeElHeader() {
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertFalse(template.headers().containsKey(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void siLaPeticionNoTraeTokenNoAnadeNada() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertFalse(template.headers().containsKey(HttpHeaders.AUTHORIZATION));
    }
}
