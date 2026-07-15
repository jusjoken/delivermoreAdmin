package ca.admin.delivermore.data.service;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ca.admin.delivermore.collector.data.entity.RestaurantMenuItemSize;

public interface RestaurantMenuItemSizeEditorRepository extends JpaRepository<RestaurantMenuItemSize, Long> {

    List<RestaurantMenuItemSize> findByMenuVersionIdAndItemIdOrderByDisplayOrderAscNameAsc(Long menuVersionId, Long itemId);

    List<RestaurantMenuItemSize> findByMenuVersionIdAndItemIdOrderByDisplayOrderAscIdAsc(Long menuVersionId, Long itemId);
}