package com.company.erp.accounting.application.port.in;

import com.company.erp.accounting.domain.InvoiceModels.Detail;
import com.company.erp.accounting.domain.InvoiceModels.HeaderCommand;
import com.company.erp.accounting.domain.InvoiceModels.Page;
import com.company.erp.accounting.domain.InvoiceModels.Search;

public interface InvoiceUseCase {
    Page search(Search search);
    Detail find(String invoiceId);
    Detail create(HeaderCommand command);
    Detail update(String invoiceId, HeaderCommand command);
    void delete(String invoiceId);
}
