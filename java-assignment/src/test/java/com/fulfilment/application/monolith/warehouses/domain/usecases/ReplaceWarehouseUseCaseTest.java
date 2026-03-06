package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fulfilment.application.monolith.location.LocationGateway;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.DomainNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.DomainValidationException;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for ReplaceWarehouseUseCase using in-memory stubs.
 */
public class ReplaceWarehouseUseCaseTest {

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

  static class GatewayLocationResolver implements LocationResolver {
    private final LocationGateway gateway = new LocationGateway();

    @Override
    public com.fulfilment.application.monolith.warehouses.domain.models.Location resolveByIdentifier(
        String identifier) {
      return gateway.resolveByIdentifier(identifier);
    }
  }

  private InMemoryWarehouseStore store;
  private ReplaceWarehouseUseCase useCase;

  @BeforeEach
  void setUp() {
    store = new InMemoryWarehouseStore();
    useCase = new ReplaceWarehouseUseCase(store, new GatewayLocationResolver());
  }

  private Warehouse existingWarehouse(String buc, String location, int capacity, int stock) {
    var w = new Warehouse();
    w.businessUnitCode = buc;
    w.location = location;
    w.capacity = capacity;
    w.stock = stock;
    return w;
  }

  @Test
  void replace_happyPath_oldIsArchivedAndNewIsCreated() {
    store.create(existingWarehouse("MWH.001", "AMSTERDAM-001", 50, 20));

    var newWarehouse = new Warehouse();
    newWarehouse.businessUnitCode = "MWH.001";
    newWarehouse.location = "AMSTERDAM-001";
    newWarehouse.capacity = 60;
    newWarehouse.stock = 20;

    useCase.replace(newWarehouse);

    var records = store.warehouses.stream()
        .filter(w -> "MWH.001".equals(w.businessUnitCode))
        .toList();
    assertEquals(2, records.size());

    var archived = records.stream().filter(w -> w.archivedAt != null).findFirst().orElse(null);
    var active = records.stream().filter(w -> w.archivedAt == null).findFirst().orElse(null);

    assertNotNull(archived);
    assertNotNull(active);
    assertEquals(60, active.capacity);
    assertNotNull(active.createdAt);
  }

  @Test
  void replace_oldWarehouseNotFound_returns404() {
    var newWarehouse = new Warehouse();
    newWarehouse.businessUnitCode = "MWH.NONEXISTENT";
    newWarehouse.location = "AMSTERDAM-001";
    newWarehouse.capacity = 50;
    newWarehouse.stock = 10;

    assertThrows(DomainNotFoundException.class, () -> useCase.replace(newWarehouse));
  }

  @Test
  void replace_nullLocation_returns400() {
    store.create(existingWarehouse("MWH.001", "AMSTERDAM-001", 50, 10));

    var newWarehouse = new Warehouse();
    newWarehouse.businessUnitCode = "MWH.001";
    newWarehouse.location = null;
    newWarehouse.capacity = 50;
    newWarehouse.stock = 10;

    assertThrows(DomainValidationException.class, () -> useCase.replace(newWarehouse));
  }

  @Test
  void replace_invalidLocation_returns400() {
    store.create(existingWarehouse("MWH.001", "AMSTERDAM-001", 50, 10));

    var newWarehouse = new Warehouse();
    newWarehouse.businessUnitCode = "MWH.001";
    newWarehouse.location = "NOWHERE-999";
    newWarehouse.capacity = 50;
    newWarehouse.stock = 10;

    assertThrows(DomainValidationException.class, () -> useCase.replace(newWarehouse));
  }

  @Test
  void replace_newCapacityTooSmallForOldStock_returns400() {
    store.create(existingWarehouse("MWH.001", "AMSTERDAM-001", 50, 30));

    var newWarehouse = new Warehouse();
    newWarehouse.businessUnitCode = "MWH.001";
    newWarehouse.location = "AMSTERDAM-001";
    newWarehouse.capacity = 20;
    newWarehouse.stock = 30;

    assertThrows(DomainValidationException.class, () -> useCase.replace(newWarehouse));
  }

  @Test
  void replace_stockMismatch_returns400() {
    store.create(existingWarehouse("MWH.001", "AMSTERDAM-001", 50, 30));

    var newWarehouse = new Warehouse();
    newWarehouse.businessUnitCode = "MWH.001";
    newWarehouse.location = "AMSTERDAM-001";
    newWarehouse.capacity = 60;
    newWarehouse.stock = 25; // does not match old stock of 30

    assertThrows(DomainValidationException.class, () -> useCase.replace(newWarehouse));
  }

  // --- Boundary value tests ---

  @Test
  void replace_capacityExactlyMatchingOldStock_succeeds() {
    store.create(existingWarehouse("MWH.001", "AMSTERDAM-001", 50, 20));

    var newWarehouse = new Warehouse();
    newWarehouse.businessUnitCode = "MWH.001";
    newWarehouse.location = "AMSTERDAM-001";
    newWarehouse.capacity = 20; // exactly equals old stock (boundary)
    newWarehouse.stock = 20;

    useCase.replace(newWarehouse);

    var active = store.warehouses.stream()
        .filter(w -> "MWH.001".equals(w.businessUnitCode) && w.archivedAt == null)
        .findFirst().orElse(null);
    assertNotNull(active);
    assertEquals(20, active.capacity);
  }

  @Test
  void replace_newLocationAtMaxWarehouseCount_returns400() {
    // ZWOLLE-001 allows only 1 warehouse; another is already there
    store.create(existingWarehouse("MWH.001", "AMSTERDAM-001", 50, 10));
    store.create(existingWarehouse("MWH.OTHER", "ZWOLLE-001", 35, 5));

    var newWarehouse = new Warehouse();
    newWarehouse.businessUnitCode = "MWH.001";
    newWarehouse.location = "ZWOLLE-001"; // already full (max = 1)
    newWarehouse.capacity = 35;
    newWarehouse.stock = 10;

    assertThrows(DomainValidationException.class, () -> useCase.replace(newWarehouse));
  }

  @Test
  void replace_newCapacityExceedsLocationMax_returns400() {
    // HELMOND-001 maxCapacity = 45; requesting 46 should be rejected
    store.create(existingWarehouse("MWH.001", "AMSTERDAM-001", 50, 30));

    var newWarehouse = new Warehouse();
    newWarehouse.businessUnitCode = "MWH.001";
    newWarehouse.location = "HELMOND-001";
    newWarehouse.capacity = 46; // exceeds HELMOND-001 max of 45
    newWarehouse.stock = 30;

    assertThrows(DomainValidationException.class, () -> useCase.replace(newWarehouse));
  }

  @Test
  void replace_differentValidLocation_succeeds() {
    store.create(existingWarehouse("MWH.001", "AMSTERDAM-001", 50, 20));

    var newWarehouse = new Warehouse();
    newWarehouse.businessUnitCode = "MWH.001";
    newWarehouse.location = "HELMOND-001"; // different from original
    newWarehouse.capacity = 45;
    newWarehouse.stock = 20;

    useCase.replace(newWarehouse);

    var active = store.warehouses.stream()
        .filter(w -> "MWH.001".equals(w.businessUnitCode) && w.archivedAt == null)
        .findFirst().orElse(null);
    assertNotNull(active);
    assertEquals("HELMOND-001", active.location);
  }

  // --- Same-location capacity checks ---

  @Test
  void replace_sameLocation_newCapacityExceedsRemainingLocationCapacity_returns400() {
    // ZWOLLE-002: maxWarehouses=2, maxCapacity=50
    // Other warehouse occupies 30; old warehouse occupies 15 → effective remaining = 50 - 30 = 20
    // New warehouse requests 25 → 30 + 25 = 55 > 50 → reject
    store.create(existingWarehouse("MWH.OTHER", "ZWOLLE-002", 30, 0));
    store.create(existingWarehouse("MWH.001", "ZWOLLE-002", 15, 10));

    var newWarehouse = new Warehouse();
    newWarehouse.businessUnitCode = "MWH.001";
    newWarehouse.location = "ZWOLLE-002";
    newWarehouse.capacity = 25;
    newWarehouse.stock = 10;

    assertThrows(DomainValidationException.class, () -> useCase.replace(newWarehouse));
  }

  @Test
  void replace_sameLocation_newCapacityExactlyAtRemainingLocationCapacity_succeeds() {
    // ZWOLLE-002: maxWarehouses=2, maxCapacity=50
    // Other warehouse occupies 30; old occupies 15 → effective remaining = 50 - 30 = 20
    // New warehouse requests 20 → 30 + 20 = 50, not > 50 → accept
    store.create(existingWarehouse("MWH.OTHER", "ZWOLLE-002", 30, 0));
    store.create(existingWarehouse("MWH.001", "ZWOLLE-002", 15, 10));

    var newWarehouse = new Warehouse();
    newWarehouse.businessUnitCode = "MWH.001";
    newWarehouse.location = "ZWOLLE-002";
    newWarehouse.capacity = 20;
    newWarehouse.stock = 10;

    useCase.replace(newWarehouse);

    var active = store.warehouses.stream()
        .filter(w -> "MWH.001".equals(w.businessUnitCode) && w.archivedAt == null)
        .findFirst().orElse(null);
    assertNotNull(active);
    assertEquals(20, active.capacity);
  }

  // --- Timestamp verification ---

  @Test
  void replace_oldWarehouseHasArchivedAtTimestampSet() {
    store.create(existingWarehouse("MWH.001", "AMSTERDAM-001", 50, 20));

    var newWarehouse = new Warehouse();
    newWarehouse.businessUnitCode = "MWH.001";
    newWarehouse.location = "AMSTERDAM-001";
    newWarehouse.capacity = 60;
    newWarehouse.stock = 20;

    var before = java.time.LocalDateTime.now();
    useCase.replace(newWarehouse);
    var after = java.time.LocalDateTime.now();

    var old = store.warehouses.stream()
        .filter(w -> "MWH.001".equals(w.businessUnitCode) && w.archivedAt != null)
        .findFirst().orElse(null);
    assertNotNull(old);
    assertNotNull(old.archivedAt);
    assertTrue(!old.archivedAt.isBefore(before) && !old.archivedAt.isAfter(after));
  }

  @Test
  void replace_newWarehouseHasCreatedAtTimestampSet() {
    store.create(existingWarehouse("MWH.001", "AMSTERDAM-001", 50, 20));

    var newWarehouse = new Warehouse();
    newWarehouse.businessUnitCode = "MWH.001";
    newWarehouse.location = "AMSTERDAM-001";
    newWarehouse.capacity = 60;
    newWarehouse.stock = 20;

    var before = java.time.LocalDateTime.now();
    useCase.replace(newWarehouse);
    var after = java.time.LocalDateTime.now();

    var created = store.warehouses.stream()
        .filter(w -> "MWH.001".equals(w.businessUnitCode) && w.archivedAt == null)
        .findFirst().orElse(null);
    assertNotNull(created);
    assertNotNull(created.createdAt);
    assertTrue(!created.createdAt.isBefore(before) && !created.createdAt.isAfter(after));
  }

  // --- BUC re-use after replacement ---

  @Test
  void replace_oldWarehouseInactiveFindByIdReturnsNewOne() {
    store.create(existingWarehouse("MWH.001", "AMSTERDAM-001", 50, 20));

    var newWarehouse = new Warehouse();
    newWarehouse.businessUnitCode = "MWH.001";
    newWarehouse.location = "AMSTERDAM-001";
    newWarehouse.capacity = 60;
    newWarehouse.stock = 20;

    useCase.replace(newWarehouse);

    var found = store.findByBusinessUnitCode("MWH.001");
    assertNotNull(found);
    assertNull(found.archivedAt);
    assertEquals(60, found.capacity);
  }
}
