/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0.
 */
package org.apache.ofbiz.modern.shell;

import java.net.URI;
import java.security.Principal;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/bff/catalog")
class CatalogBffController {
    private static final Logger LOG = LoggerFactory.getLogger(CatalogBffController.class);
    private final RestClient catalog;

    CatalogBffController(RestClient.Builder builder, @Value("${catalog-service.base-url}") String baseUrl) {
        this.catalog = builder.baseUrl(baseUrl).build();
    }

    @GetMapping(value = "/products", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) String type, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset,
            Principal principal) {
        URI uri = UriComponentsBuilder.fromPath("/internal/v1/catalog/products")
                .queryParamIfPresent("q", Optional.ofNullable(q))
                .queryParamIfPresent("type", Optional.ofNullable(type))
                .queryParamIfPresent("status", Optional.ofNullable(status))
                .queryParam("limit", limit).queryParam("offset", offset).build().encode().toUri();
        LOG.info("catalog_search actor={} limit={} offset={}", principal.getName(), limit, offset);
        return get(uri);
    }

    @GetMapping(value = "/products/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> detail(@PathVariable String id, Principal principal) {
        LOG.info("catalog_detail actor={} product_id={}", principal.getName(), id);
        try {
            return ResponseEntity.ok(catalog.get().uri("/internal/v1/catalog/products/{id}", id)
                    .retrieve().body(String.class));
        } catch (RestClientResponseException error) {
            throw new ResponseStatusException(error.getStatusCode(), "catalog service request failed", error);
        }
    }

    private ResponseEntity<String> get(URI uri) {
        try {
            return ResponseEntity.ok(catalog.get().uri(uri).retrieve().body(String.class));
        } catch (RestClientResponseException error) {
            throw new ResponseStatusException(error.getStatusCode(), "catalog service request failed", error);
        }
    }
}
