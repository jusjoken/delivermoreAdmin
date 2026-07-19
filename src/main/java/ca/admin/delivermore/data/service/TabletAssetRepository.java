/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package ca.admin.delivermore.data.service;

/**
 *
 * @author birch
 */
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import ca.admin.delivermore.data.entity.TabletAsset;

public interface TabletAssetRepository extends JpaRepository<TabletAsset, Long> {

    List<TabletAsset> findByArchivedFalseOrderByAssetNameAsc();

    @Query("select t from TabletAsset t order by t.assetName")
    List<TabletAsset> findAllOrderByAssetNameAsc();

    boolean existsByAssetTag(String assetTag);

    long countByArchivedFalse();

    TabletAsset findByAssetTag(String assetTag);

    Optional<TabletAsset> findFirstByProvisioningNonceAndArchivedFalse(String provisioningNonce);

    Optional<TabletAsset> findFirstByRestaurantIdAndArchivedFalse(Long restaurantId);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Transactional
        @Query("""
            update TabletAsset t
               set t.lastHeartbeatAt = :heartbeatAt,
               t.lastHeartbeatAppVersion = case when :appVersion is null or :appVersion = ''
                             then t.lastHeartbeatAppVersion
                             else :appVersion end
             where t.assetTag = :assetTag
               and t.archived = false
            """)
        int updateHeartbeatByAssetTag(
            @Param("assetTag") String assetTag,
            @Param("heartbeatAt") java.time.LocalDateTime heartbeatAt,
            @Param("appVersion") String appVersion);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Transactional
        @Query("""
            update TabletAsset t
               set t.fcmRegistrationToken = :registrationToken,
               t.fcmTokenUpdatedAt = :updatedAt
             where t.assetTag = :assetTag
               and t.archived = false
            """)
        int updateTokenByAssetTag(
            @Param("assetTag") String assetTag,
            @Param("registrationToken") String registrationToken,
            @Param("updatedAt") java.time.LocalDateTime updatedAt);
}