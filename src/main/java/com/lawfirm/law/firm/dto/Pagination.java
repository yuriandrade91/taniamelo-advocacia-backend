package com.lawfirm.law.firm.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Page;

/** Bloco de paginação do envelope padrão da API (pageNumber é 1-based). */
@Getter
@Setter
public class Pagination {

    private int pageNumber;
    private int pageSize;
    private long totalRecords;
    private int totalPages;
    private boolean hasNextPage;
    private boolean hasPreviousPage;

    public Pagination() {}

    public Pagination(int pageNumber, int pageSize, long totalRecords) {
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalRecords = totalRecords;
        this.totalPages = pageSize > 0 ? (int) Math.ceil((double) totalRecords / pageSize) : 0;
        this.hasPreviousPage = this.pageNumber > 1;
        this.hasNextPage = this.pageNumber < this.totalPages;
    }

    /** Constrói o bloco de paginação a partir de um Page do Spring Data. */
    public static Pagination of(Page<?> page) {
        return new Pagination(page.getNumber() + 1, page.getSize(), page.getTotalElements());
    }
}
