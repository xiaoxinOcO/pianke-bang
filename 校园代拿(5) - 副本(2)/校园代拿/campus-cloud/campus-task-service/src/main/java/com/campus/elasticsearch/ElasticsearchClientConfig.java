package com.campus.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import jakarta.annotation.PreDestroy;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@ConditionalOnProperty(prefix = "app.elasticsearch", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ElasticsearchProperties.class)
public class ElasticsearchClientConfig {

    private RestClient restClient;

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchProperties properties) {
        String raw = properties.getHosts().trim();
        if (raw.contains(",")) {
            String[] parts = raw.split(",");
            HttpHost[] hosts = new HttpHost[parts.length];
            for (int i = 0; i < parts.length; i++) {
                hosts[i] = toHttpHost(parts[i].trim());
            }
            restClient = RestClient.builder(hosts).build();
        } else {
            restClient = RestClient.builder(toHttpHost(raw)).build();
        }
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }

    @PreDestroy
    public void close() throws IOException {
        if (restClient != null) {
            restClient.close();
        }
    }

    private static HttpHost toHttpHost(String value) {
        String normalized = value;
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://" + normalized;
        }
        return HttpHost.create(normalized);
    }
}
