/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information.
 * The ASF licenses this file to you under the Apache License, Version 2.0.
 */
package org.apache.ofbiz.modern.template;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TemplateServiceApplicationTest {
    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void servesVersionedExample() {
        var response = restTemplate.getForObject(
                "http://localhost:" + port + "/api/v1/example", String.class);
        assertThat(response).contains("template-service is ready");
    }

    @Test
    void exposesReadinessProbe() {
        var response = restTemplate.getForObject(
                "http://localhost:" + port + "/actuator/health/readiness", String.class);
        assertThat(response).contains("UP");
    }
}
