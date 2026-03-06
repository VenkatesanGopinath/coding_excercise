package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fulfilment.application.monolith.location.LocationGateway;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.ws.rs.WebApplicationException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for CreateWarehouseUseCase using in-memory stubs.
 * No Quarkus context needed — constructor injection enables direct instantiation.
 */
public class CreateWarehouseUseCaseTest {

  // --- In-memory stubs ---

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
      warehouses.replaceAll(
          w -> w.businessUnitCode.equals(warehouse.businessUnitCode) ? warehouse : w);
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

  static class GatewayLocationResolver implements LocationResolver {
    private final LocationGateway gateway = new LocationGateway();

    @Override
    public com.fulfilment.application.monolith.warehouses.domain.models.Location resolveByIdentifier(
        String identifier) {
      return gateway.resolveByIdentifier(identifier);
    }
  }

  // --- Helpers ---

  private InMemoryWarehouseStore store;
  private CreateWarehouseUseCase useCase;

  @BeforeEach
  void setUp() {
    store = new InMemoryWarehouseStore();
    useCase = new CreateWarehouseUseCase(store, new GatewayLocationResolver());
  }

  private Warehouse newWarehouse(String buc, String location, int capacity, int stock) {
    var w = new Warehouse();
    w.businessUnitCode = buc;
    w.location = location;
    w.capacity = capacity;
    w.stock = stock;
    return w;
  }

  // --- Tests ---

  @Test
  void createWarehouse_happyPath_warehouseIsPersisted() {
    // EINDHOVEN-001: max 2 warehouses, max total capacity 70
    var warehouse = newWarehouse("MWH.NEW", "EINDHOVEN-001", 50, 10);

    useCase.create(warehouse);

    assertNotNull(store.findById("MWH.NEW"));
    assertNotNull(warehouse.createdAt);
  }

  @Test
  void createWarehouse_duplicateBusinessUnitCode_returns400() {
    store.create(newWarehouse("MWH.EXISTS", "EINDHOVEN-001", 30, 5));

    var duplicate = newWarehouse("MWH.EXISTS", "EINDHOVEN-001", 20, 5);
    var ex = assertThrows(WebApplicationException.class, () -> useCase.create(duplicate));

    assertEquals(400, ex.getResponse().getStatus());
  }

  @Test
  void createWarehouse_nullLocation_returns400() {
    var warehouse = newWarehouse("MWH.NEW", null, 10, 5);

    var ex = assertThrows(WebApplicationException.class, () -> useCase.create(warehouse));

    assertEquals(400, ex.getResponse().getStatus());
  }

  @Test
  void createWarehouse_invalidLocation_returns400() {
    var warehouse = newWarehouse("MWH.NEW", "NOWHERE-999", 10, 5);

    var ex = assertThrows(WebApplicationException.class, () -> useCase.create(warehouse));

    assertEquals(400, ex.getResponse().getStatus());
  }

  @Test
  void createWarehouse_maxWarehousesAtLocationReached_returns400() {
    // ZWOLLE-001 allows only 1 warehouse
    store.create(newWarehouse("MWH.EXISTING", "ZWOLLE-001", 30, 5));

    var warehouse = newWarehouse("MWH.NEW", "ZWOLLE-001", 5, 0);
    var ex = assertThrows(WebApplicationException.class, () -> useCase.create(warehouse));

    assertEquals(400, ex.getResponse().getStatus());
  }

  @Test
  void createWarehouse_capacityExceedsLocationMax_returns400() {
    // ZWOLLE-001 max total capacity is 40
    var warehouse = newWarehouse("MWH.BIG", "ZWOLLE-001", 50, 5);

    var ex = assertThrows(WebApplicationException.class, () -> useCase.create(warehouse));

    assertEquals(400, ex.getResponse().getStatus());
  }

  @Test
  void createWarehouse_stockExceedsCapacity_returns400() {
    var warehouse = newWarehouse("MWH.NEW", "EINDHOVEN-001", 30, 50);

    var ex = assertThrows(WebApplicationException.class, () -> useCase.create(warehouse));

    assertEquals(400, ex.getResponse().getStatus());
  }

  // --- Boundary value tests ---

  @Test
  void createWarehouse_capacityExactlyAtLocationMax_succeeds() {
    // HELMOND-001: maxCapacity=45, maxWarehouses=1 — exactly at the limit should pass
    var warehouse = newWarehouse("MWH.EXACT", "HELMOND-001", 45, 0);

    useCase.create(warehouse);

    assertNotNull(store.findById("MWH.EXACT"));
  }

  @Test
  void createWarehouse_oneOverLocationMaxCapacity_returns400() {
    // HELMOND-001: maxCapacity=45 — one over the limit should fail
    var warehouse = newWarehouse("MWH.TOOBIG", "HELMOND-001", 46, 0);

    var ex = assertThrows(WebApplicationException.class, () -> useCase.create(warehouse));

    assertEquals(400, ex.getResponse().getStatus());
  }

  @Test
  void createWarehouse_stockIsZero_succeeds() {
    // Zero stock is a valid initial state (empty warehouse)
    var warehouse = newWarehouse("MWH.ZERO", "EINDHOVEN-001", 50, 0);

    useCase.create(warehouse);

    assertNotNull(store.findById("MWH.ZERO"));
  }

  @Test
  void createWarehouse_secondWarehouseAtMultiWarehouseLocation_succeeds() {
    // ZWOLLE-002: maxWarehouses=2, maxCapacity=50 — two warehouses at 20 each (total 40) should pass
    store.create(newWarehouse("MWH.FIRST", "ZWOLLE-002", 20, 5));

    var second = newWarehouse("MWH.SECOND", "ZWOLLE-002", 20, 0);
    useCase.create(second);

    assertNotNull(store.findById("MWH.SECOND"));
  }

  @Test
  void createWarehouse_blankLocation_returns400() {
    var warehouse = newWarehouse("MWH.NEW", "   ", 10, 0);

    var ex = assertThrows(WebApplicationException.class, () -> useCase.create(warehouse));

    assertEquals(400, ex.getResponse().getStatus());
  }

  @Test
  void createWarehouse_nullCapacity_returns400() {
    var warehouse = new Warehouse();
    warehouse.businessUnitCode = "MWH.NOCAP";
    warehouse.location = "EINDHOVEN-001";
    warehouse.capacity = null;
    warehouse.stock = 0;

    var ex = assertThrows(WebApplicationException.class, () -> useCase.create(warehouse));

    assertEquals(400, ex.getResponse().getStatus());
  }

  @Test
  void createWarehouse_cumulativeCapacity_secondExceedsLocationMax_returns400() {
    // ZWOLLE-002: maxWarehouses=2, maxCapacity=50
    // First warehouse uses 30, second requests 25 → 30+25=55 > 50 → reject
    store.create(newWarehouse("MWH.FIRST", "ZWOLLE-002", 30, 0));

    var second = newWarehouse("MWH.SECOND", "ZWOLLE-002", 25, 0);
    var ex = assertThrows(WebApplicationException.class, () -> useCase.create(second));

    assertEquals(400, ex.getResponse().getStatus());
  }

  @Test
  void createWarehouse_cumulativeCapacity_secondExactlyFitsLocationMax_succeeds() {
    // ZWOLLE-002: maxWarehouses=2, maxCapacity=50
    // First warehouse uses 30, second requests 20 → 30+20=50 = 50 (not >) → accept
    store.create(newWarehouse("MWH.FIRST", "ZWOLLE-002", 30, 0));

    var second = newWarehouse("MWH.SECOND", "ZWOLLE-002", 20, 0);
    useCase.create(second);

    assertNotNull(store.findById("MWH.SECOND"));
  }
}
