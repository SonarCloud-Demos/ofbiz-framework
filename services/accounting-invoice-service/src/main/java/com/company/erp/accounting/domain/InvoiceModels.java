package com.company.erp.accounting.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class InvoiceModels {
    private InvoiceModels() { }

    public record Summary(String invoiceId, String invoiceTypeId, String invoiceTypeDescription,
            String statusId, String statusDescription, String partyIdFrom, String partyId,
            Instant invoiceDate, Instant dueDate, String currencyUomId, long itemCount,
            BigDecimal invoiceTotal, BigDecimal appliedPaymentTotal, BigDecimal outstandingTotal) { }

    public record Item(String invoiceItemSeqId, String invoiceItemTypeId, String productId,
            String description, BigDecimal quantity, BigDecimal amount, BigDecimal lineTotal) { }

    public record PaymentApplication(String paymentApplicationId, String paymentId,
            String invoiceItemSeqId, BigDecimal amountApplied) { }

    public record StatusHistory(String statusId, String description, Instant statusDate,
            String changeByUserLoginId) { }

    public record Detail(Summary header, String description, String referenceNumber,
            List<Item> lineItems, List<PaymentApplication> paymentApplications,
            List<StatusHistory> statusHistory, String source, String totalsNote) { }

    public record Page(List<Summary> invoices, int limit, int offset, long total,
            String source, String totalsNote) { }

    public record Search(int limit, int offset, String invoiceId, String invoiceTypeId,
            String statusId, String partyIdFrom, String partyId) { }

    public record HeaderCommand(String invoiceId, String invoiceTypeId, String partyIdFrom,
            String partyId, String roleTypeId, String statusId, Instant invoiceDate,
            Instant dueDate, String description, String referenceNumber, String currencyUomId) { }
}
