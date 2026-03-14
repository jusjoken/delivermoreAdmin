/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package ca.admin.delivermore.data.service;

/**
 *
 * @author birch
 */
import ca.admin.delivermore.data.entity.TabletAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TabletAssetRepository extends JpaRepository<TabletAsset, Long> {

    List<TabletAsset> findByArchivedFalseOrderByAssetNameAsc();

    @Query("select t from TabletAsset t order by t.assetName")
    List<TabletAsset> findAllOrderByAssetNameAsc();

    boolean existsByAssetTag(String assetTag);

    long countByArchivedFalse();

    TabletAsset findByAssetTag(String assetTag);
}