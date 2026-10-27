package org.pronto.pronto.repository;

import org.pronto.pronto.entity.NotificationDecision;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationDecisionRepository extends JpaRepository<NotificationDecision, Long> {

    Optional<NotificationDecision> findByIdempotencyKey(String idempotencyKey);

    List<NotificationDecision> findByUser_IdOrderByDecidedAtDescIdDesc(Long userId, Limit limit);

    List<NotificationDecision> findAllByOrderByIdDesc(Limit limit);

    long countByUser_Id(Long userId);
}
