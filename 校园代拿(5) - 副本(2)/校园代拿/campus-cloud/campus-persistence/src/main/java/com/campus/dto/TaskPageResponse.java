package com.campus.dto;

import com.campus.model.DeliveryTask;

import java.util.List;

public class TaskPageResponse {
    private List<DeliveryTask> records;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public TaskPageResponse(List<DeliveryTask> records, int page, int size, long totalElements, int totalPages) {
        this.records = records;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    public List<DeliveryTask> getRecords() {
        return records;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }
}
