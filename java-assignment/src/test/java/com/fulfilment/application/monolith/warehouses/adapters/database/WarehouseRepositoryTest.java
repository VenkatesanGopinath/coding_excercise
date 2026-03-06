package com.fulfilment.application.monolith.warehouses.adapters.database;

import static org.junit.jupiter.api.Assertions.*;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * Persistence layer tests for WarehouseRepository.
 * Each test runs inside its own transaction that is rolled back afterwards (@TestTransaction),
 * so the seed data is never mutated between tests.
 */
@QuarkusTest
public class WarehouseRepositoryTest {

  @Inject WarehouseRepository repository;

  // --- Helpers ---

  private Warehouse makeWarehouse(String buc, String location, int capacity, int stock) {
    var w = new Warehouse();
    w.businessUnitCode = buc;
    w.location = location;
    w.capacity = capacity;
    w.stock = stock;
    w.createdAt = LocalDateTime.now();
    return w;
  }

  // --- getAll() ---

  @Test
  @TestTransaction
  void getAll_returnsSeedWarehousesAsActive() {
    // The import.sql seeds MWH.001, MWH.012, MWH.023 — all active
    var all = repository.getAll();

    assertTrue(all.stream().anyMatch(w -> "MWH.001".equals(w.businessUnitCode)));
    assertTrue(all.stream().anyMatch(w -> "MWH.012".equals(w.businessUnitCode)));
    assertTrue(all.stream().anyMatch(w -> "MWH.023".equals(w.businessUnitCode)));
  }

  @Test
  @TestTransaction
  void getAll_excludesArchivedWarehouses() {
    // Insert one active and one that we immediately archive
    repository.create(makeWarehouse("TEST.ACTIVE", "EINDHOVEN-001", 30, 0));
    repository.create(makeWarehouse("TEST.TO_ARCHIVE", "EINDHOVEN-001", 20, 0));

    // Archive the second one
    var toArchive = repository.findById("TEST.TO_ARCHIVE");
    assertNotNull(toArchive);
    toArchive.archivedAt = LocalDateTime.now();
    repository.update(toArchive);

    var all = repository.getAll();
    assertTrue(all.stream().anyMatch(w -> "TEST.ACTIVE".equals(w.businessUnitCode)));
    assertTrue(all.stream().noneMatch(w -> "TEST.TO_ARCHIVE".equals(w.businessUnitCode)));
  }

  // --- create() ---

  @Test
  @TestTransaction
  void create_persistsAllFields() {
    var warehouse = makeWarehouse("TEST.CREATE", "EINDHOVEN-001", 25, 5);

    repository.create(warehouse);

    var found = repository.findById("TEST.CREATE");
    assertNotNull(found);
    assertEquals("TEST.CREATE", found.businessUnitCode);
    assertEquals("EINDHOVEN-001", found.location);
    assertEquals(25, found.capacity);
    assertEquals(5, found.stock);
    assertNotNull(found.createdAt);
    assertNull(found.archivedAt);
  }

  // --- update() ---

  @Test
  @TestTransaction
  void update_setsArchivedAt_makesWarehouseInvisibleToGetAll() {
    repository.create(makeWarehouse("TEST.UPDATE", "EINDHOVEN-001", 20, 0));

    var toArchive = repository.findById("TEST.UPDATE");
    assertNotNull(toArchive);
    toArchive.archivedAt = LocalDateTime.now();
    repository.update(toArchive);

    // Must not appear in getAll() and findById must return null
    assertNull(repository.findById("TEST.UPDATE"));
    assertTrue(repository.getAll().stream().noneMatch(w -> "TEST.UPDATE".equals(w.businessUnitCode)));
  }

  // --- findById() ---

  @Test
  @TestTransaction
  void findById_returnsNullForNonExistent() {
    assertNull(repository.findById("DOES.NOT.EXIST"));
  }

  @Test
  @TestTransaction
  void findById_returnsNullForArchivedWarehouse() {
    repository.create(makeWarehouse("TEST.FINDARCHIVED", "EINDHOVEN-001", 20, 0));

    // Archive it
    var w = repository.findById("TEST.FINDARCHIVED");
    w.archivedAt = LocalDateTime.now();
    repository.update(w);

    // After archiving, lookup by BUC must return null (only active warehouses are returned)
    assertNull(repository.findById("TEST.FINDARCHIVED"));
  }
}
