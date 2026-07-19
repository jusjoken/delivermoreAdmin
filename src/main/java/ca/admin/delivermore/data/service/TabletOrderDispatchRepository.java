package ca.admin.delivermore.data.service;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ca.admin.delivermore.data.entity.TabletOrderDispatch;

public interface TabletOrderDispatchRepository extends JpaRepository<TabletOrderDispatch, Long> {

    Optional<TabletOrderDispatch> findByStagedOrderId(Long stagedOrderId);

    java.util.List<TabletOrderDispatch> findByStagedOrderIdIn(Collection<Long> stagedOrderIds);
}