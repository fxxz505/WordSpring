package com.wordspring.server.sync;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailySummaryRepository extends JpaRepository<DailySummaryEntity, Long> {

    Optional<DailySummaryEntity> findByUserIdAndLocalDate(long userId, String localDate);

    List<DailySummaryEntity> findByUserIdAndLocalDateBetweenOrderByLocalDateAsc(
            long userId, String from, String to);
}
