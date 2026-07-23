package ca.admin.delivermore.data.service;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ca.admin.delivermore.collector.data.entity.RestaurantMenuOptionGroup;

public interface RestaurantMenuOptionGroupEditorRepository extends JpaRepository<RestaurantMenuOptionGroup, Long> {

    List<RestaurantMenuOptionGroup> findByMenuVersionIdOrderByDisplayOrderAscNameAsc(Long menuVersionId);

    List<RestaurantMenuOptionGroup> findByMenuVersionIdAndCategoryIdOrderByDisplayOrderAscNameAsc(Long menuVersionId, Long categoryId);

    List<RestaurantMenuOptionGroup> findByMenuVersionIdAndItemIdOrderByDisplayOrderAscNameAsc(Long menuVersionId, Long itemId);

    List<RestaurantMenuOptionGroup> findByMenuVersionIdAndItemId(Long menuVersionId, Long itemId);

    List<RestaurantMenuOptionGroup> findByMenuVersionIdAndItemSizeIdOrderByDisplayOrderAscNameAsc(Long menuVersionId, Long itemSizeId);

    List<RestaurantMenuOptionGroup> findByMenuVersionIdAndItemSizeId(Long menuVersionId, Long itemSizeId);

    List<RestaurantMenuOptionGroup> findByMenuVersionIdAndSourceGroupId(Long menuVersionId, Long sourceGroupId);
}
