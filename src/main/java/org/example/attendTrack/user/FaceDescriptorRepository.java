package org.example.attendTrack.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FaceDescriptorRepository extends JpaRepository<FaceDescriptor, UUID> {

    @Query("SELECT fd FROM FaceDescriptor fd WHERE fd.user.id = :userId")
    Optional<FaceDescriptor> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT fd.user.id FROM FaceDescriptor fd")
    List<UUID> findAllUserIdsWithFace();

    @Query("SELECT fd FROM FaceDescriptor fd WHERE fd.user.id != :excludeUserId")
    List<FaceDescriptor> findAllExcludingUser(@Param("excludeUserId") UUID excludeUserId);

    @Modifying
    @Query("DELETE FROM FaceDescriptor fd WHERE fd.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
