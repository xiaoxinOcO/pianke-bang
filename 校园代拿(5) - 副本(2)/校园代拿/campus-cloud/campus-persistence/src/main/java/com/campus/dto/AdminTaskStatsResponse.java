package com.campus.dto;

import java.util.ArrayList;
import java.util.List;

public class AdminTaskStatsResponse {

    private long totalCount;
    private long todayCount;
    private int trendDays;
    private List<AdminTaskStatsTrendPoint> publishTrend = new ArrayList<>();
    private List<AdminTaskStatsItem> statusDistribution = new ArrayList<>();
    private List<AdminTaskStatsItem> hotPickupLocations = new ArrayList<>();
    private List<AdminTaskStatsItem> hotDropoffLocations = new ArrayList<>();
    private String source = "elasticsearch";

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public long getTodayCount() {
        return todayCount;
    }

    public void setTodayCount(long todayCount) {
        this.todayCount = todayCount;
    }

    public int getTrendDays() {
        return trendDays;
    }

    public void setTrendDays(int trendDays) {
        this.trendDays = trendDays;
    }

    public List<AdminTaskStatsTrendPoint> getPublishTrend() {
        return publishTrend;
    }

    public void setPublishTrend(List<AdminTaskStatsTrendPoint> publishTrend) {
        this.publishTrend = publishTrend;
    }

    public List<AdminTaskStatsItem> getStatusDistribution() {
        return statusDistribution;
    }

    public void setStatusDistribution(List<AdminTaskStatsItem> statusDistribution) {
        this.statusDistribution = statusDistribution;
    }

    public List<AdminTaskStatsItem> getHotPickupLocations() {
        return hotPickupLocations;
    }

    public void setHotPickupLocations(List<AdminTaskStatsItem> hotPickupLocations) {
        this.hotPickupLocations = hotPickupLocations;
    }

    public List<AdminTaskStatsItem> getHotDropoffLocations() {
        return hotDropoffLocations;
    }

    public void setHotDropoffLocations(List<AdminTaskStatsItem> hotDropoffLocations) {
        this.hotDropoffLocations = hotDropoffLocations;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
