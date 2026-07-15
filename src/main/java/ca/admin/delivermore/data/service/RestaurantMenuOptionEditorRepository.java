package ca.admin.delivermore.data.service;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ca.admin.delivermore.collector.data.entity.RestaurantMenuOption;

public interface RestaurantMenuOptionEditorRepository extends JpaRepository<RestaurantMenuOption, Long> {

    List<RestaurantMenuOption> findByOptionGroupIdOrderByDisplayOrderAscNameAsc(Long optionGroupId);

    void deleteByOptionGroupId(Long optionGroupId);
}
