package ca.admin.delivermore.data.service;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ca.admin.delivermore.collector.data.entity.RestaurantMenuItem;

public interface RestaurantMenuItemEditorRepository extends JpaRepository<RestaurantMenuItem, Long> {

    List<RestaurantMenuItem> findByMenuVersionIdOrderByDisplayOrderAscNameAsc(Long menuVersionId);

    List<RestaurantMenuItem> findByMenuVersionIdAndCategoryIdOrderByDisplayOrderAscNameAsc(Long menuVersionId, Long categoryId);
}
