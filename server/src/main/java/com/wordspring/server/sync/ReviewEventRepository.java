package com.wordspring.server.sync;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewEventRepository extends JpaRepository<ReviewEventEntity, Long> {

    boolean existsByUserIdAndAnswerId(long userId, String answerId);

    List<ReviewEventEntity> findByUserIdAndLocalDate(long userId, String localDate);

    @Query("""
            select count(distinct e.cardUid) from ReviewEventEntity e
            where e.userId = :userId and e.localDate = :date
            """)
    int countDistinctCards(@Param("userId") long userId, @Param("date") String date);

    @Query("""
            select count(distinct e.cardUid) from ReviewEventEntity e
            where e.userId = :userId and e.localDate = :date and e.grade <> 'AGAIN'
            """)
    int countDistinctCorrectCards(@Param("userId") long userId, @Param("date") String date);

    @Query("""
            select count(distinct e.cardUid) from ReviewEventEntity e
            where e.userId = :userId and e.localDate = :date
              and not exists (
                  select 1 from ReviewEventEntity p
                  where p.userId = :userId and p.cardUid = e.cardUid and p.answeredAt < e.answeredAt
              )
            """)
    int countCardsFirstSeenOn(@Param("userId") long userId, @Param("date") String date);

    @Query("""
            select coalesce(sum(e.durationMs), 0) from ReviewEventEntity e
            where e.userId = :userId and e.localDate = :date
            """)
    long sumDurationMs(@Param("userId") long userId, @Param("date") String date);
}
