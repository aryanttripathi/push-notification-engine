package org.pronto.pronto.repository;

import org.pronto.pronto.entity.MessageTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, Long> {

    Optional<MessageTemplate> findByCode(String code);

    boolean existsByCode(String code);
}
