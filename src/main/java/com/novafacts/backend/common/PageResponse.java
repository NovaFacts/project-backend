package com.novafacts.backend.common;

import org.springframework.data.domain.Page;

import java.util.List;

public class PageResponse<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean last;

    public PageResponse(Page<T> springPage) {
        this.content       = springPage.getContent();
        this.page          = springPage.getNumber();
        this.size          = springPage.getSize();
        this.totalElements = springPage.getTotalElements();
        this.totalPages    = springPage.getTotalPages();
        this.last          = springPage.isLast();
    }

    public List<T> getContent()       { return content; }
    public int getPage()              { return page; }
    public int getSize()              { return size; }
    public long getTotalElements()    { return totalElements; }
    public int getTotalPages()        { return totalPages; }
    public boolean isLast()           { return last; }
}
