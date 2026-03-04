package com.fulfilment.application.monolith.warehouses.domain.ports;

import com.fulfilment.application.monolith.warehouses.domain.models.FulfillmentAssignment;
import java.util.List;

/**
 * Persistence port for fulfillment assignments.
 * All constraint-counting queries are expressed here so the domain use case
 * remains independent of any specific database or ORM technology.
 */
public interface FulfillmentStore {

  /** Returns true if the warehouse is active (exists and not archived). */
  boolean warehouseIsActive(String warehouseBuc);

  /** Returns true if a product with the given id exists. */
  boolean productExists(Long productId);

  /** Returns true if a store with the given id exists. */
  boolean storeExists(Long storeId);

  /**
   * Counts the number of distinct product types currently assigned to this warehouse.
   * Constraint: max 5 per warehouse.
   */
  long countDistinctProductsForWarehouse(String warehouseBuc);

  /**
   * Counts the number of distinct warehouses fulfilling a specific product for a specific store.
   * Constraint: max 2 per (product, store) pair.
   */
  long countDistinctWarehousesForProductAndStore(Long productId, Long storeId);

  /**
   * Counts the number of distinct warehouses fulfilling any product for a specific store.
   * Constraint: max 3 per store.
   */
  long countDistinctWarehousesForStore(Long storeId);

  /** Persists a new assignment and returns it with the generated id. */
  FulfillmentAssignment assign(String warehouseBuc, Long productId, Long storeId);

  /** Returns all assignments for the given warehouse. */
  List<FulfillmentAssignment> findByWarehouse(String warehouseBuc);

  /** Returns the assignment with the given id that belongs to the given warehouse, or null. */
  FulfillmentAssignment findAssignment(Long id, String warehouseBuc);

  /** Removes the assignment with the given id. */
  void remove(Long id);
}
