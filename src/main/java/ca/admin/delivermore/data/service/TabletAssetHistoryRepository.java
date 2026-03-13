/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package ca.admin.delivermore.data.service;

/**
 *
 * @author birch
 */
import ca.admin.delivermore.data.entity.TabletAssetHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TabletAssetHistoryRepository extends JpaRepository<TabletAssetHistory, Long> {

    List<TabletAssetHistory> findByTabletAssetIdOrderByChangedAtDesc(Long tabletAssetId);
}