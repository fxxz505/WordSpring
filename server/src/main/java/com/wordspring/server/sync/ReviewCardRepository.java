package com.wordspring.server.sync;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewCardRepository extends JpaRepository<ReviewCardEntity, Long> {

    Optional<ReviewCardEntity> findByUserIdAndCardUid(long userId, String cardUid);

    List<ReviewCardEntity> findByUserIdAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(long userId, long since);

    List<ReviewCardEntity> findByUserId(long userId);
}
