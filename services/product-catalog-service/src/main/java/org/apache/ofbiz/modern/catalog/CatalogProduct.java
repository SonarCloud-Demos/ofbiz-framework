/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0.
 */
package org.apache.ofbiz.modern.catalog;

import java.time.Instant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CatalogProduct(
        @NotBlank @Size(max = 64) String productId,
        @Size(max = 255) String internalName,
        @Size(max = 255) String productName,
        @Size(max = 64) String productTypeId,
        @Size(max = 64) String statusId,
        @Size(max = 2000) String description,
        @NotNull Instant sourceUpdatedAt,
        @NotBlank String sourceChecksum) { }
