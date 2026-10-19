package com.campus.repository;

import com.campus.model.DeliveryTask;
import com.campus.model.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface DeliveryTaskRepository extends JpaRepository<DeliveryTask, Long> {

    @Query("""
            select t from DeliveryTask t
            where (lower(t.publisherName) = lower(:userA) and lower(t.claimerName) = lower(:userB))
               or (lower(t.publisherName) = lower(:userB) and lower(t.claimerName) = lower(:userA))
            """)
    List<DeliveryTask> findTaskLinksBetweenUsers(@Param("userA") String userA, @Param("userB") String userB);

    @Query("""
            select t from DeliveryTask t
            where lower(t.publisherName) = lower(:me)
               or lower(t.claimerName) = lower(:me)
            """)
    List<DeliveryTask> findTaskLinksForUser(@Param("me") String me);

    @Query("""
            select avg(t.claimerRating)
            from DeliveryTask t
            where lower(t.claimerName) = lower(:claimerName)
              and t.claimerRating is not null
            """)
    BigDecimal calcClaimerAverageRating(@Param("claimerName") String claimerName);

    @Query("""
            select count(t)
            from DeliveryTask t
            where lower(t.claimerName) = lower(:claimerName)
              and t.claimerRating is not null
            """)
    long countClaimerRatings(@Param("claimerName") String claimerName);

    @Query("""
            select t from DeliveryTask t
            where lower(t.claimerName) = lower(:claimerName)
              and t.claimerRating is not null
            order by coalesce(t.updatedAt, t.createdAt) desc
            """)
    List<DeliveryTask> findClaimerRatings(@Param("claimerName") String claimerName);

    @Query("""
            select count(t)
            from DeliveryTask t
            where lower(t.claimerName) = lower(:claimerName)
              and t.status in (com.campus.model.TaskStatus.CLAIMED,
                               com.campus.model.TaskStatus.PENDING_CONFIRMATION,
                               com.campus.model.TaskStatus.COMPLETED)
            """)
    long countClaimedTasks(@Param("claimerName") String claimerName);

    @Query("""
            select avg(t.claimerRating)
            from DeliveryTask t
            where lower(t.publisherName) = lower(:publisherName)
              and t.claimerRating is not null
            """)
    BigDecimal calcPublisherAverageRating(@Param("publisherName") String publisherName);

    @Query("""
            select count(t)
            from DeliveryTask t
            where lower(t.publisherName) = lower(:publisherName)
              and t.claimerRating is not null
            """)
    long countPublisherRatings(@Param("publisherName") String publisherName);

    List<DeliveryTask> findByClaimerNameIgnoreCaseAndStatus(String claimerName, TaskStatus status);

    @Query("""
            select t from DeliveryTask t
            where (:status is null or t.status = :status)
              and (:taskType is null or :taskType = '' or t.taskType = :taskType)
              and (
                :keyword is null or :keyword = ''
                or lower(t.title) like lower(concat('%', :keyword, '%'))
                or lower(t.pickupLocation) like lower(concat('%', :keyword, '%'))
                or lower(t.dropoffLocation) like lower(concat('%', :keyword, '%'))
              )
            """)
    Page<DeliveryTask> searchTasks(
            @Param("status") TaskStatus status,
            @Param("taskType") String taskType,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    /** 大厅排序：加急加权窗内优先，再按加急费降序，再按创建时间降序 */
    @Query("""
            select t from DeliveryTask t
            where (:status is null or t.status = :status)
              and (:taskType is null or :taskType = '' or t.taskType = :taskType)
              and (
                :keyword is null or :keyword = ''
                or lower(t.title) like lower(concat('%', :keyword, '%'))
                or lower(t.pickupLocation) like lower(concat('%', :keyword, '%'))
                or lower(t.dropoffLocation) like lower(concat('%', :keyword, '%'))
              )
            order by
              case
                when coalesce(t.urgentEnabled, false) = true
                 and t.urgentAt is not null
                 and t.urgentAt >= :urgentSince
                then 1 else 0
              end desc,
              case
                when coalesce(t.urgentEnabled, false) = true
                 and t.urgentAt is not null
                 and t.urgentAt >= :urgentSince
                then coalesce(t.urgentFee, 0)
                else 0
              end desc,
              t.createdAt desc
            """)
    Page<DeliveryTask> searchTasksHallOrdered(
            @Param("status") TaskStatus status,
            @Param("taskType") String taskType,
            @Param("keyword") String keyword,
            @Param("urgentSince") LocalDateTime urgentSince,
            Pageable pageable
    );

    @Query("""
            select count(t) from DeliveryTask t
            where lower(t.publisherName) = lower(:publisherName)
              and coalesce(t.urgentEnabled, false) = true
              and coalesce(t.bountyPrepaid, false) = true
              and t.status not in (com.campus.model.TaskStatus.CANCELLED,
                                   com.campus.model.TaskStatus.REJECTED)
              and t.createdAt >= :weekStart
            """)
    long countUrgentUsedThisWeek(
            @Param("publisherName") String publisherName,
            @Param("weekStart") LocalDateTime weekStart
    );

    List<DeliveryTask> findByStatusAndLatestDeliveryAtBefore(TaskStatus status, LocalDateTime deadline);

    List<DeliveryTask> findByStatusAndLatestDeliveryAtLessThanEqual(TaskStatus status, LocalDateTime deadline);

    List<DeliveryTask> findByStatusAndUpdatedAtBefore(TaskStatus status, LocalDateTime deadline);

    List<DeliveryTask> findByStatusAndBountyPrepaidAndCreatedAtBefore(
            TaskStatus status,
            Boolean bountyPrepaid,
            LocalDateTime deadline
    );

    List<DeliveryTask> findTop50ByStatusAndBountyPrepaidOrderByCreatedAtAsc(
            TaskStatus status,
            Boolean bountyPrepaid
    );

    boolean existsByPublisherNameIgnoreCaseAndStatusAndBountyPrepaid(
            String publisherName,
            TaskStatus status,
            Boolean bountyPrepaid
    );

    List<DeliveryTask> findByPublisherNameIgnoreCaseAndStatusAndBountyPrepaid(
            String publisherName,
            TaskStatus status,
            Boolean bountyPrepaid
    );

    /** 大厅完成率：近 N 天内完结状态计数 */
    long countByStatusAndUpdatedAtGreaterThanEqual(TaskStatus status, LocalDateTime since);

    /** 接单后取消：取消时仍保留接单人 */
    @Query("""
            select count(t) from DeliveryTask t
            where t.status = com.campus.model.TaskStatus.CANCELLED
              and t.updatedAt >= :since
              and t.claimerName is not null
              and t.claimerName <> ''
            """)
    long countClaimedCancelledSince(@Param("since") LocalDateTime since);
}
