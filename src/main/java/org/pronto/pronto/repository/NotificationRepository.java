package org.pronto.pronto.repository;

import org.pronto.pronto.entity.MessageType;
import org.pronto.pronto.entity.Notification;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    long countByUser_IdAndMessageTypeAndSentAtBetween(Long userId, MessageType type, Instant from, Instant to);

    @Query("select max(n.sentAt) from Notification n "
            + "where n.user.id = :userId and n.campaign.id = :campaignId and n.sentAt <= :now")
    Instant findLastSentAtByCampaign(@Param("userId") Long userId, @Param("campaignId") Long campaignId,
                                     @Param("now") Instant now);

    @Query("select max(n.sentAt) from Notification n "
            + "where n.user.id = :userId and n.messageType = :type and n.sentAt <= :now")
    Instant findLastSentAtByType(@Param("userId") Long userId, @Param("type") MessageType type,
                                 @Param("now") Instant now);

    Optional<Notification> findByDecision_Id(Long decisionId);

    List<Notification> findByDecision_IdIn(Collection<Long> decisionIds);

    List<Notification> findByUser_IdOrderBySentAtDescIdDesc(Long userId, Limit limit);

    long countByUser_Id(Long userId);
}
