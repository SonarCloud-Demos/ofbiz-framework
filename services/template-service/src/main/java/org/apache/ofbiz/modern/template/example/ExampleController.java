/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information.
 * The ASF licenses this file to you under the Apache License, Version 2.0.
 */
package org.apache.ofbiz.modern.template.example;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/example")
public class ExampleController {
    @GetMapping
    public Map<String, String> getExample() {
        return Map.of("message", "template-service is ready");
    }
}
