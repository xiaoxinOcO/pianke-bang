package com.campus.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.campus.dto.AdminTaskStatsItem;
import com.campus.dto.AdminTaskStatsResponse;
import com.campus.dto.AdminTaskStatsTrendPoint;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.campus.dto.TaskPageResponse;
import com.campus.model.DeliveryTask;
import com.campus.model.TaskStatus;
import com.campus.repository.DeliveryTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "app.elasticsearch", name = "enabled", havingValue = "true")
public class TaskElasticsearchService {

    private static final Logger log = LoggerFactory.getLogger(TaskElasticsearchService.class);

    private static final String INDEX_MAPPING = """
            {
              "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0
              },
              "mappings": {
                "properties": {
                  "id": { "type": "long" },
                  "title": { "type": "text", "analyzer": "standard" },
                  "taskType": { "type": "keyword" },
                  "pickupLocation": {
                    "type": "text",
                    "analyzer": "standard",
                    "fields": {
                      "keyword": { "type": "keyword", "ignore_above": 256 }
                    }
                  },
                  "dropoffLocation": {
                    "type": "text",
                    "analyzer": "standard",
                    "fields": {
                      "keyword": { "type": "keyword", "ignore_above": 256 }
                    }
                  },
                  "description": { "type": "text", "analyzer": "standard" },
                  "status": { "type": "keyword" },
                  "publisherName": { "type": "keyword" },
                  "createdAt": { "type": "date" }
                }
              }
            }
            """;

    private final ElasticsearchClient client;
    private final ElasticsearchProperties properties;
    private final DeliveryTaskRepository deliveryTaskRepository;

    public TaskElasticsearchService(
            ElasticsearchClient client,
            ElasticsearchProperties properties,
            DeliveryTaskRepository deliveryTaskRepository
    ) {
        this.client = client;
        this.properties = properties;
        this.deliveryTaskRepository = deliveryTaskRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            ensureIndex();
            if (properties.isReindexOnStartup()) {
                reindexAll();
            }
        } catch (Exception ex) {
            log.warn("Elasticsearch 初始化失败，关键词搜索将回退 MySQL: {}", ex.getMessage());
        }
    }

    public void ensureIndex() throws IOException {
        String index = properties.getIndex();
        boolean exists = client.indices().exists(e -> e.index(index)).value();
        if (!exists) {
            client.indices().create(c -> c
                    .index(index)
                    .withJson(new StringReader(INDEX_MAPPING))
            );
            log.info("已创建 Elasticsearch 索引: {}", index);
        }
    }

    public void reindexAll() throws IOException {
        List<DeliveryTask> tasks = deliveryTaskRepository.findAll();
        if (tasks.isEmpty()) {
            return;
        }
        String index = properties.getIndex();
        List<BulkOperation> operations = new ArrayList<>(tasks.size());
        for (DeliveryTask task : tasks) {
            operations.add(BulkOperation.of(op -> op.index(IndexOperation.of(idx -> idx
                    .index(index)
                    .id(String.valueOf(task.getId()))
                    .document(toSource(task))
            ))));
        }
        client.bulk(BulkRequest.of(b -> b.index(index).operations(operations)));
        log.info("已向 Elasticsearch 同步 {} 条任务", tasks.size());
    }

    public void sync(DeliveryTask task) {
        if (task == null || task.getId() == null) {
            return;
        }
        try {
            ensureIndex();
            client.index(i -> i
                    .index(properties.getIndex())
                    .id(String.valueOf(task.getId()))
                    .document(toSource(task))
            );
        } catch (Exception ex) {
            log.warn("同步任务 {} 到 Elasticsearch 失败: {}", task.getId(), ex.getMessage());
        }
    }

    public void delete(Long taskId) {
        if (taskId == null) {
            return;
        }
        try {
            client.delete(d -> d
                    .index(properties.getIndex())
                    .id(String.valueOf(taskId))
            );
        } catch (Exception ex) {
            log.warn("从 Elasticsearch 删除任务 {} 失败: {}", taskId, ex.getMessage());
        }
    }

    public AdminTaskStatsResponse buildAdminStats(int days) {
        int trendDays = Math.min(Math.max(days, 1), 90);
        try {
            ensureIndex();
            String index = properties.getIndex();

            SearchResponse<Void> response = client.search(s -> s
                            .index(index)
                            .size(0)
                            .trackTotalHits(t -> t.enabled(true))
                            .query(q -> q.matchAll(m -> m))
                            .aggregations("status_agg", a -> a.terms(t -> t.field("status").size(20)))
                            .aggregations("pickup_agg", a -> a.terms(t -> t.field("pickupLocation.keyword").size(10)))
                            .aggregations("dropoff_agg", a -> a.terms(t -> t.field("dropoffLocation.keyword").size(10)))
                            .aggregations("trend_agg", a -> a.dateHistogram(dh -> dh
                                    .field("createdAt")
                                    .calendarInterval(CalendarInterval.Day)
                                    .format("yyyy-MM-dd")
                                    .minDocCount(0)
                            )),
                    Void.class
            );

            CountResponse todayResponse = client.count(c -> c
                    .index(index)
                    .query(q -> q.range(r -> r
                            .field("createdAt")
                            .gte(co.elastic.clients.json.JsonData.of("now/d"))
                            .lte(co.elastic.clients.json.JsonData.of("now"))
                    ))
            );

            AdminTaskStatsResponse stats = new AdminTaskStatsResponse();
            stats.setTrendDays(trendDays);
            stats.setTotalCount(response.hits().total() == null ? 0 : response.hits().total().value());
            stats.setTodayCount(todayResponse.count());
            stats.setStatusDistribution(parseStringTerms(response, "status_agg"));
            stats.setHotPickupLocations(parseStringTerms(response, "pickup_agg"));
            stats.setHotDropoffLocations(parseStringTerms(response, "dropoff_agg"));
            stats.setPublishTrend(parseTrend(response, "trend_agg", trendDays));
            return stats;
        } catch (Exception ex) {
            log.warn("Elasticsearch 任务统计失败: {}", ex.getMessage());
            return null;
        }
    }

    private List<AdminTaskStatsItem> parseStringTerms(SearchResponse<Void> response, String aggName) {
        List<AdminTaskStatsItem> items = new ArrayList<>();
        var aggregate = response.aggregations().get(aggName);
        if (aggregate == null || !aggregate.isSterms()) {
            return items;
        }
        for (StringTermsBucket bucket : aggregate.sterms().buckets().array()) {
            String key = bucket.key().stringValue();
            if (key == null || key.isBlank()) {
                continue;
            }
            items.add(new AdminTaskStatsItem(key, bucket.docCount()));
        }
        return items;
    }

    private List<AdminTaskStatsTrendPoint> parseTrend(SearchResponse<Void> response, String aggName, int trendDays) {
        List<AdminTaskStatsTrendPoint> points = new ArrayList<>();
        var aggregate = response.aggregations().get(aggName);
        if (aggregate == null || !aggregate.isDateHistogram()) {
            return points;
        }
        java.time.LocalDate cutoff = java.time.LocalDate.now().minusDays(trendDays - 1L);
        for (DateHistogramBucket bucket : aggregate.dateHistogram().buckets().array()) {
            String key = bucket.keyAsString();
            if (key == null || key.isBlank()) {
                continue;
            }
            try {
                java.time.LocalDate day = java.time.LocalDate.parse(key);
                if (day.isBefore(cutoff)) {
                    continue;
                }
            } catch (Exception ignored) {
                // keep bucket if date parse fails
            }
            points.add(new AdminTaskStatsTrendPoint(key, bucket.docCount()));
        }
        return points;
    }

    public TaskPageResponse searchTasks(TaskStatus status, String taskType, String keyword, int page, int size) {
        try {
            ensureIndex();
            int from = Math.max(page - 1, 0) * size;
            SearchResponse<Map> response = client.search(s -> s
                            .index(properties.getIndex())
                            .from(from)
                            .size(size)
                            .trackTotalHits(t -> t.enabled(true))
                            .sort(so -> so.field(f -> f.field("createdAt").order(SortOrder.Desc)))
                            .query(q -> q.bool(b -> {
                                if (status != null) {
                                    b.filter(f -> f.term(t -> t.field("status").value(status.name())));
                                }
                                if (taskType != null && !taskType.isBlank()) {
                                    b.filter(f -> f.term(t -> t.field("taskType").value(taskType)));
                                }
                                b.must(m -> m.bool(kb -> {
                                    kb.should(sh -> sh.multiMatch(mm -> mm
                                            .query(keyword)
                                            .fields("title", "pickupLocation", "dropoffLocation", "description", "publisherName")
                                            .type(TextQueryType.BestFields)
                                            .operator(Operator.Or)
                                    ));
                                    String pattern = "*" + escapeWildcard(keyword) + "*";
                                    kb.should(sh -> sh.wildcard(w -> w.field("title").value(pattern)));
                                    kb.should(sh -> sh.wildcard(w -> w.field("pickupLocation").value(pattern)));
                                    kb.should(sh -> sh.wildcard(w -> w.field("dropoffLocation").value(pattern)));
                                    kb.should(sh -> sh.wildcard(w -> w.field("description").value(pattern)));
                                    kb.should(sh -> sh.wildcard(w -> w.field("publisherName").value(pattern)));
                                    kb.minimumShouldMatch("1");
                                    return kb;
                                }));
                                return b;
                            })),
                    Map.class
            );

            List<Long> ids = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source == null) {
                    continue;
                }
                Object idValue = source.get("id");
                if (idValue instanceof Number number) {
                    ids.add(number.longValue());
                }
            }

            Map<Long, DeliveryTask> taskMap = deliveryTaskRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(DeliveryTask::getId, task -> task, (a, b) -> a, LinkedHashMap::new));

            List<DeliveryTask> ordered = ids.stream()
                    .map(taskMap::get)
                    .filter(Objects::nonNull)
                    .toList();

            long total = response.hits().total() == null ? ordered.size() : response.hits().total().value();
            int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) total / size);
            return new TaskPageResponse(ordered, page, size, total, totalPages);
        } catch (Exception ex) {
            log.warn("Elasticsearch 搜索失败，回退 MySQL: {}", ex.getMessage());
            return null;
        }
    }

    private static String escapeWildcard(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("?", "\\?");
    }

    private Map<String, Object> toSource(DeliveryTask task) {
        Map<String, Object> source = new HashMap<>();
        source.put("id", task.getId());
        source.put("title", safe(task.getTitle()));
        source.put("taskType", safe(task.getTaskType()));
        source.put("pickupLocation", safe(task.getPickupLocation()));
        source.put("dropoffLocation", safe(task.getDropoffLocation()));
        source.put("description", safe(task.getDescription()));
        source.put("status", task.getStatus() == null ? null : task.getStatus().name());
        source.put("publisherName", safe(task.getPublisherName()));
        if (task.getCreatedAt() != null) {
            source.put("createdAt", task.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toString());
        }
        return source;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
