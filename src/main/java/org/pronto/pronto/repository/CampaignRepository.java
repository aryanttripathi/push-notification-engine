package org.pronto.pronto.repository;

import org.pronto.pronto.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    Optional<Campaign> findByCode(String code);

    boolean existsByCode(String code);
}
