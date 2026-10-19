package com.campus.dto;

import java.util.List;

public class CsRatingPageResponse {
    private List<CsRatingDTO> records;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private double averageScore;
    private long totalCount;

    public CsRatingPageResponse(
            List<CsRatingDTO> records,
            int page,
            int size,
            long totalElements,
            int totalPages,
            double averageScore,
            long totalCount
    ) {
        this.records = records;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.averageScore = averageScore;
        this.totalCount = totalCount;
    }

    public List<CsRatingDTO> getRecords() {
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

    public double getAverageScore() {
        return averageScore;
    }

    public long getTotalCount() {
        return totalCount;
    }
}
