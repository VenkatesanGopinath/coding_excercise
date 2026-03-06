package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.DomainNotFoundException;
import com.fulfilment.application.monolith.fulfillment.domain.models.FulfillmentAssignment;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.fulfillment.domain.ports.FulfillmentStore;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for ArchiveWarehouseUseCase using an in-memory stub.
 */
public class ArchiveWarehouseUseCaseTest {

  static class InMemoryWarehouseStore implements WarehouseStore {
    final List<Warehouse> warehouses = new ArrayList<>();

    @Override
    public List<Warehouse> getAll() {
      return warehouses.stream().filter(w -> w.archivedAt == null).toList();
    }

    @Override
    public List<Warehouse> findByLocation(String location) {
      return warehouses.stream()
          .filter(w -> location.equals(w.location) && w.archivedAt == null)
          .toList();
    }

    @Override
    public long countActive() {
      return warehouses.stream().filter(w -> w.archivedAt == null).count();
    }

    @Override
    public void create(Warehouse warehouse) {
      warehouses.add(warehouse);
    }

    @Override
    public void update(Warehouse warehouse) {
      for (int i = 0; i < warehouses.size(); i++) {
        if (warehouses.get(i).businessUnitCode.equals(warehouse.businessUnitCode)
            && warehouses.get(i).archivedAt == null) {
          warehouses.set(i, warehouse);
          return;
        }
      }
    }

    @Override
    public void remove(Warehouse warehouse) {
      warehouses.removeIf(w -> w.businessUnitCode.equals(warehouse.businessUnitCode));
    }

    @Override
    public Warehouse findByBusinessUnitCode(String businessUnitCode) {
      return warehouses.stream()
          .filter(w -> businessUnitCode.equals(w.businessUnitCode) && w.archivedAt == null)
          .findFirst()
          .orElse(null);
    }
  }

  /** Minimal stub: tracks which BUCs were cascade-deleted. */
  static class TrackingFulfillmentStore implements FulfillmentStore {
    final List<String> removedBucs = new ArrayList<>();

    @Override public boolean productExists(Long id) { return false; }
    @Override public boolean storeExists(Long id) { return false; }
    @Override public long countDistinctProductsForWarehouse(String buc) { return 0; }
    @Override public long countDistinctWarehousesForProductAndStore(Long p, Long s) { return 0; }
    @Override public long countDistinctWarehousesForStore(Long sid) { return 0; }
    @Override public FulfillmentAssignment assign(String buc, Long p, Long s) { return null; }
    @Override public List<FulfillmentAssignment> findByWarehouse(String buc) { return List.of(); }
    @Override public FulfillmentAssignment findAssignment(Long id, String buc) { return null; }
    @Override public void remove(Long id) {}
    @Override public void removeByWarehouse(String buc) { removedBucs.add(buc); }
  }

  private InMemoryWarehouseStore store;
  private TrackingFulfillmentStore fulfillmentStore;
  private ArchiveWarehouseUseCase useCase;

  @BeforeEach
  void setUp() {
    store = new InMemoryWarehouseStore();
    fulfillmentStore = new TrackingFulfillmentStore();
    useCase = new ArchiveWarehouseUseCase(store, fulfillmentStore);
  }

  @Test
  void archive_happyPath_warehouseIsArchived() {
    var active = new Warehouse();
    active.businessUnitCode = "MWH.001";
    active.location = "ZWOLLE-001";
    active.capacity = 30;
    active.stock = 5;
    store.create(active);

    useCase.archive("MWH.001");

    assertNull(store.findByBusinessUnitCode("MWH.001"));
    var archived = store.warehouses.stream()
        .filter(w -> "MWH.001".equals(w.businessUnitCode))
        .findFirst()
        .orElse(null);
    assertNotNull(archived);
    assertNotNull(archived.archivedAt);
  }

  @Test
  void archive_happyPath_fulfillmentAssignmentsCascadeRemoved() {
    var active = new Warehouse();
    active.businessUnitCode = "MWH.001";
    active.location = "ZWOLLE-001";
    active.capacity = 30;
    active.stock = 5;
    store.create(active);

    useCase.archive("MWH.001");

    assertEquals(1, fulfillmentStore.removedBucs.size());
    assertEquals("MWH.001", fulfillmentStore.removedBucs.get(0));
  }

  @Test
  void archive_warehouseNotFound_returns404() {
    assertThrows(DomainNotFoundException.class, () -> useCase.archive("MWH.NONEXISTENT"));
  }

  @Test
  void archive_alreadyArchivedWarehouse_returns404() {
    var archived = new Warehouse();
    archived.businessUnitCode = "MWH.001";
    archived.location = "ZWOLLE-001";
    archived.capacity = 30;
    archived.stock = 5;
    archived.archivedAt = java.time.LocalDateTime.now().minusDays(1);
    store.warehouses.add(archived);

    assertThrows(DomainNotFoundException.class, () -> useCase.archive("MWH.001"));
  }

  @Test
  void archive_emptyStringId_returns404() {
    assertThrows(DomainNotFoundException.class, () -> useCase.archive(""));
  }

  @Test
  void archive_archivingTwice_secondAttemptReturns404() {
    var active = new Warehouse();
    active.businessUnitCode = "MWH.002";
    active.location = "ZWOLLE-001";
    active.capacity = 30;
    active.stock = 5;
    store.create(active);

    useCase.archive("MWH.002");

    assertThrows(DomainNotFoundException.class, () -> useCase.archive("MWH.002"));
  }

  @Test
  void archive_archivedAtTimestampIsSet() {
    var active = new Warehouse();
    active.businessUnitCode = "MWH.003";
    active.location = "ZWOLLE-001";
    active.capacity = 30;
    active.stock = 0;
    store.create(active);

    var before = java.time.LocalDateTime.now();
    useCase.archive("MWH.003");
    var after = java.time.LocalDateTime.now();

    var record = store.warehouses.stream()
        .filter(w -> "MWH.003".equals(w.businessUnitCode))
        .findFirst()
        .orElse(null);
    assertNotNull(record);
    assertNotNull(record.archivedAt);
    assertTrue(!record.archivedAt.isBefore(before) && !record.archivedAt.isAfter(after));
  }
}
