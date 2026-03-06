package com.fulfilment.application.monolith.fulfillment.domain.ports;

import com.fulfilment.application.monolith.fulfillment.domain.models.FulfillmentAssignment;
import java.util.List;

public interface FulfillmentStore {

  boolean productExists(Long productId);

  boolean storeExists(Long storeId);

  // Constraint 3: max 5 distinct product types per warehouse
  long countDistinctProductsForWarehouse(String warehouseBuc);

  // Constraint 1: max 2 warehouses per (product, store) pair
  long countDistinctWarehousesForProductAndStore(Long productId, Long storeId);

  // Constraint 2: max 3 warehouses per store
  long countDistinctWarehousesForStore(Long storeId);

  FulfillmentAssignment assign(String warehouseBuc, Long productId, Long storeId);

  List<FulfillmentAssignment> findByWarehouse(String warehouseBuc);

  FulfillmentAssignment findAssignment(Long id, String warehouseBuc);

  void remove(Long id);

  /** Removes all fulfillment assignments for the given warehouse BUC (cascade on archive). */
  void removeByWarehouse(String warehouseBuc);
}
