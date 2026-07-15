package ca.admin.delivermore.data.service;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ca.admin.delivermore.collector.data.entity.RestaurantMenuCategory;

public interface RestaurantMenuCategoryEditorRepository extends JpaRepository<RestaurantMenuCategory, Long> {

    List<RestaurantMenuCategory> findByMenuVersionIdOrderByDisplayOrderAscNameAsc(Long menuVersionId);
}
