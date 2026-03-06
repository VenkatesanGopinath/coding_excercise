package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.ws.rs.WebApplicationException;
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
    public Warehouse findById(String id) {
      return warehouses.stream()
          .filter(w -> id.equals(w.businessUnitCode) && w.archivedAt == null)
          .findFirst()
          .orElse(null);
    }
  }

  private InMemoryWarehouseStore store;
  private ArchiveWarehouseUseCase useCase;

  @BeforeEach
  void setUp() {
    store = new InMemoryWarehouseStore();
    useCase = new ArchiveWarehouseUseCase(store);
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

    // After archive, the warehouse should not be findable as active
    assertEquals(null, store.findById("MWH.001"));
    // But the archivedAt should be set on the stored record
    var archived = store.warehouses.stream()
        .filter(w -> "MWH.001".equals(w.businessUnitCode))
        .findFirst()
        .orElse(null);
    assertNotNull(archived);
    assertNotNull(archived.archivedAt);
  }

  @Test
  void archive_warehouseNotFound_returns404() {
    var ex = assertThrows(WebApplicationException.class, () -> useCase.archive("MWH.NONEXISTENT"));

    assertEquals(404, ex.getResponse().getStatus());
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

    var ex = assertThrows(WebApplicationException.class, () -> useCase.archive("MWH.001"));

    assertEquals(404, ex.getResponse().getStatus());
  }

  @Test
  void archive_emptyStringId_returns404() {
    // Empty string does not match any BUC — must be treated as not found
    var ex = assertThrows(WebApplicationException.class, () -> useCase.archive(""));

    assertEquals(404, ex.getResponse().getStatus());
  }

  @Test
  void archive_archivingTwice_secondAttemptReturns404() {
    var active = new Warehouse();
    active.businessUnitCode = "MWH.002";
    active.location = "ZWOLLE-001";
    active.capacity = 30;
    active.stock = 5;
    store.create(active);

    // First archive must succeed
    useCase.archive("MWH.002");

    // Second archive on an already-archived warehouse must return 404
    var ex = assertThrows(WebApplicationException.class, () -> useCase.archive("MWH.002"));
    assertEquals(404, ex.getResponse().getStatus());
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
