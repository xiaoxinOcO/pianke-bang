package com.campus.elasticsearch;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.elasticsearch")
public class ElasticsearchProperties {

    private boolean enabled = false;
    private String hosts = "http://127.0.0.1:9200";
    private String index = "campus_delivery_task";
    private boolean reindexOnStartup = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHosts() {
        return hosts;
    }

    public void setHosts(String hosts) {
        this.hosts = hosts;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public boolean isReindexOnStartup() {
        return reindexOnStartup;
    }

    public void setReindexOnStartup(boolean reindexOnStartup) {
        this.reindexOnStartup = reindexOnStartup;
    }
}
