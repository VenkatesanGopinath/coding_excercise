package com.fulfilment.application.monolith.warehouses.domain.ports;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import java.util.List;

public interface WarehouseStore {

  List<Warehouse> getAll();

  /** Returns only active (non-archived) warehouses at the given location. */
  List<Warehouse> findByLocation(String location);

  /** Returns the count of active (non-archived) warehouses without loading entities. */
  long countActive();

  void create(Warehouse warehouse);

  void update(Warehouse warehouse);

  void remove(Warehouse warehouse);

  /** Finds an active warehouse by its business unit code. Returns null if not found. */
  Warehouse findByBusinessUnitCode(String businessUnitCode);
}
