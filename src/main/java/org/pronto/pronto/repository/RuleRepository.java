package org.pronto.pronto.repository;

import org.pronto.pronto.entity.Rule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RuleRepository extends JpaRepository<Rule, Long> {

    List<Rule> findByEnabledTrueOrderByPriorityAscIdAsc();

    Optional<Rule> findByName(String name);
}
