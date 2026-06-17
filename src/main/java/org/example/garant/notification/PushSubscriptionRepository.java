package org.example.garant.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    @Query("SELECT ps FROM PushSubscription ps WHERE ps.user.id = :userId")
    List<PushSubscription> findByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM PushSubscription ps WHERE ps.endpoint = :endpoint")
    void deleteByEndpoint(@Param("endpoint") String endpoint);

    @Query("SELECT ps FROM PushSubscription ps WHERE ps.user.id IN :userIds")
    List<PushSubscription> findByUserIds(@Param("userIds") List<UUID> userIds);
}
