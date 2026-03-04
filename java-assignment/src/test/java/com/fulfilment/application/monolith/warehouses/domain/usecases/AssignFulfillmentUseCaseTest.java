package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fulfilment.application.monolith.warehouses.domain.models.FulfillmentAssignment;
import com.fulfilment.application.monolith.warehouses.domain.ports.FulfillmentStore;
import jakarta.ws.rs.WebApplicationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for AssignFulfillmentUseCase.
 * Uses an in-memory stub for FulfillmentStore — no database, no CDI, runs in milliseconds.
 */
public class AssignFulfillmentUseCaseTest {

  // ---------------------------------------------------------------------------
  // In-memory stub
  // ---------------------------------------------------------------------------

  static class InMemoryFulfillmentStore implements FulfillmentStore {

    final Set<String> activeWarehouses;
    final Set<Long> existingProducts;
    final Set<Long> existingStores;
    final List<FulfillmentAssignment> assignments = new ArrayList<>();
    private long idSeq = 1;

    InMemoryFulfillmentStore(
        Set<String> activeWarehouses, Set<Long> existingProducts, Set<Long> existingStores) {
      this.activeWarehouses = new HashSet<>(activeWarehouses);
      this.existingProducts = new HashSet<>(existingProducts);
      this.existingStores = new HashSet<>(existingStores);
    }

    @Override
    public boolean warehouseIsActive(String buc) {
      return activeWarehouses.contains(buc);
    }

    @Override
    public boolean productExists(Long id) {
      return existingProducts.contains(id);
    }

    @Override
    public boolean storeExists(Long id) {
      return existingStores.contains(id);
    }

    @Override
    public long countDistinctProductsForWarehouse(String buc) {
      return assignments.stream()
          .filter(a -> buc.equals(a.warehouseBusinessUnitCode))
          .map(a -> a.productId)
          .distinct()
          .count();
    }

    @Override
    public long countDistinctWarehousesForProductAndStore(Long productId, Long storeId) {
      return assignments.stream()
          .filter(a -> productId.equals(a.productId) && storeId.equals(a.storeId))
          .map(a -> a.warehouseBusinessUnitCode)
          .distinct()
          .count();
    }

    @Override
    public long countDistinctWarehousesForStore(Long storeId) {
      return assignments.stream()
          .filter(a -> storeId.equals(a.storeId))
          .map(a -> a.warehouseBusinessUnitCode)
          .distinct()
          .count();
    }

    @Override
    public FulfillmentAssignment assign(String buc, Long productId, Long storeId) {
      var f = new FulfillmentAssignment();
      f.id = idSeq++;
      f.warehouseBusinessUnitCode = buc;
      f.productId = productId;
      f.storeId = storeId;
      assignments.add(f);
      return f;
    }

    @Override
    public List<FulfillmentAssignment> findByWarehouse(String buc) {
      return assignments.stream()
          .filter(a -> buc.equals(a.warehouseBusinessUnitCode))
          .toList();
    }

    @Override
    public FulfillmentAssignment findAssignment(Long id, String buc) {
      return assignments.stream()
          .filter(a -> id.equals(a.id) && buc.equals(a.warehouseBusinessUnitCode))
          .findFirst()
          .orElse(null);
    }

    @Override
    public void remove(Long id) {
      assignments.removeIf(a -> id.equals(a.id));
    }

    /** Helper: pre-load assignments directly, bypassing the use case. */
    void addAssignment(String buc, Long productId, Long storeId) {
      var f = new FulfillmentAssignment();
      f.id = idSeq++;
      f.warehouseBusinessUnitCode = buc;
      f.productId = productId;
      f.storeId = storeId;
      assignments.add(f);
    }
  }

  // ---------------------------------------------------------------------------
  // Test setup
  // ---------------------------------------------------------------------------

  private InMemoryFulfillmentStore store;
  private AssignFulfillmentUseCase useCase;

  @BeforeEach
  void setUp() {
    store = new InMemoryFulfillmentStore(
        Set.of("MWH.001", "MWH.002", "MWH.003", "MWH.004"),
        Set.of(1L, 2L, 3L, 4L, 5L, 6L),
        Set.of(1L, 2L, 3L, 4L));
    useCase = new AssignFulfillmentUseCase(store);
  }

  // ---------------------------------------------------------------------------
  // Happy path
  // ---------------------------------------------------------------------------

  @Test
  void assign_happyPath_returnsAssignmentWithId() {
    FulfillmentAssignment result = useCase.assign("MWH.001", 1L, 1L);

    assertNotNull(result);
    assertNotNull(result.id);
    assertEquals("MWH.001", result.warehouseBusinessUnitCode);
    assertEquals(1L, result.productId);
    assertEquals(1L, result.storeId);
    assertEquals(1, store.assignments.size());
  }

  // ---------------------------------------------------------------------------
  // Existence validation (404)
  // ---------------------------------------------------------------------------

  @Test
  void assign_inactiveWarehouse_returns404() {
    var ex = assertThrows(WebApplicationException.class,
        () -> useCase.assign("MWH.ARCHIVED", 1L, 1L));
    assertEquals(404, ex.getResponse().getStatus());
  }

  @Test
  void assign_nonExistentProduct_returns404() {
    var ex = assertThrows(WebApplicationException.class,
        () -> useCase.assign("MWH.001", 99L, 1L));
    assertEquals(404, ex.getResponse().getStatus());
  }

  @Test
  void assign_nonExistentStore_returns404() {
    var ex = assertThrows(WebApplicationException.class,
        () -> useCase.assign("MWH.001", 1L, 99L));
    assertEquals(404, ex.getResponse().getStatus());
  }

  // ---------------------------------------------------------------------------
  // Constraint 3: Warehouse max 5 product types
  // ---------------------------------------------------------------------------

  @Test
  void assign_warehouseAlreadyHas5ProductTypes_returns400() {
    // Fill MWH.001 with 5 different products for store 1
    store.addAssignment("MWH.001", 1L, 1L);
    store.addAssignment("MWH.001", 2L, 1L);
    store.addAssignment("MWH.001", 3L, 1L);
    store.addAssignment("MWH.001", 4L, 1L);
    store.addAssignment("MWH.001", 5L, 1L);

    var ex = assertThrows(WebApplicationException.class,
        () -> useCase.assign("MWH.001", 6L, 1L));
    assertEquals(400, ex.getResponse().getStatus());
  }

  @Test
  void assign_warehouseAt4ProductTypes_succeeds() {
    // 4 < 5 — should still accept one more
    store.addAssignment("MWH.001", 1L, 1L);
    store.addAssignment("MWH.001", 2L, 1L);
    store.addAssignment("MWH.001", 3L, 1L);
    store.addAssignment("MWH.001", 4L, 1L);

    FulfillmentAssignment result = useCase.assign("MWH.001", 5L, 1L);
    assertNotNull(result);
  }

  // ---------------------------------------------------------------------------
  // Constraint 1: Product max 2 warehouses per store
  // ---------------------------------------------------------------------------

  @Test
  void assign_productAlreadyFulfilledBy2WarehousesForStore_returns400() {
    // Product 1 for store 1 is already handled by 2 different warehouses
    store.addAssignment("MWH.001", 1L, 1L);
    store.addAssignment("MWH.002", 1L, 1L);

    var ex = assertThrows(WebApplicationException.class,
        () -> useCase.assign("MWH.003", 1L, 1L));
    assertEquals(400, ex.getResponse().getStatus());
  }

  @Test
  void assign_productFulfilledBy1WarehouseForStore_succeeds() {
    // 1 < 2 — boundary: should still accept a second warehouse
    store.addAssignment("MWH.001", 1L, 1L);

    FulfillmentAssignment result = useCase.assign("MWH.002", 1L, 1L);
    assertNotNull(result);
  }

  // ---------------------------------------------------------------------------
  // Constraint 2: Store max 3 warehouses
  // ---------------------------------------------------------------------------

  @Test
  void assign_storeAlreadyFulfilledBy3Warehouses_returns400() {
    // Store 1 already has 3 different warehouses (for different products)
    store.addAssignment("MWH.001", 1L, 1L);
    store.addAssignment("MWH.002", 2L, 1L);
    store.addAssignment("MWH.003", 3L, 1L);

    var ex = assertThrows(WebApplicationException.class,
        () -> useCase.assign("MWH.004", 4L, 1L));
    assertEquals(400, ex.getResponse().getStatus());
  }

  @Test
  void assign_storeWith2Warehouses_succeeds() {
    // 2 < 3 — boundary: should still accept a third warehouse
    store.addAssignment("MWH.001", 1L, 1L);
    store.addAssignment("MWH.002", 2L, 1L);

    FulfillmentAssignment result = useCase.assign("MWH.003", 3L, 1L);
    assertNotNull(result);
  }

  // ---------------------------------------------------------------------------
  // Constraint independence: different stores don't interfere
  // ---------------------------------------------------------------------------

  @Test
  void assign_sameProductAndWarehouse_differentStore_succeeds() {
    // Product 1 fulfilled by MWH.001 for store 1 — assigning same product/warehouse for store 2 is separate
    store.addAssignment("MWH.001", 1L, 1L);
    store.addAssignment("MWH.002", 1L, 1L);

    // Store 2 has a clean slate — assigning product 1 with MWH.001 is valid
    FulfillmentAssignment result = useCase.assign("MWH.001", 1L, 2L);
    assertNotNull(result);
  }
}
