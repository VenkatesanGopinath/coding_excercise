package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import java.time.LocalDateTime;
import java.util.List;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CreateWarehouseUseCase implements CreateWarehouseOperation {

  private static final Logger LOG = Logger.getLogger(CreateWarehouseUseCase.class);

  private final WarehouseStore warehouseStore;
  private final LocationResolver locationResolver;

  public CreateWarehouseUseCase(WarehouseStore warehouseStore, LocationResolver locationResolver) {
    this.warehouseStore = warehouseStore;
    this.locationResolver = locationResolver;
  }

  @Override
  @Transactional
  public void create(Warehouse warehouse) {
    LOG.infof("Creating warehouse [buc=%s, location=%s, capacity=%d]",
        warehouse.businessUnitCode, warehouse.location, warehouse.capacity);

    // 1. Business Unit Code must be unique among active warehouses
    if (warehouseStore.findById(warehouse.businessUnitCode) != null) {
      LOG.warnf("Creation rejected: BUC '%s' already exists", warehouse.businessUnitCode);
      throw new WebApplicationException(
          "Business Unit Code '" + warehouse.businessUnitCode + "' already exists.", 400);
    }

    // 2. Location must be provided and must be a known valid location
    if (warehouse.location == null || warehouse.location.isBlank()) {
      LOG.warnf("Creation rejected: location must not be blank");
      throw new WebApplicationException("Location must not be blank.", 400);
    }
    Location location = locationResolver.resolveByIdentifier(warehouse.location);
    if (location == null) {
      LOG.warnf("Creation rejected: location '%s' is not valid", warehouse.location);
      throw new WebApplicationException(
          "Location '" + warehouse.location + "' is not valid.", 400);
    }

    // 3. & 4. Fetch all active warehouses at this location once
    List<Warehouse> atSameLocation =
        warehouseStore.getAll().stream()
            .filter(w -> warehouse.location.equals(w.location))
            .toList();

    // 3. Location must not have reached its max number of active warehouses
    if (atSameLocation.size() >= location.maxNumberOfWarehouses) {
      LOG.warnf("Creation rejected: max warehouses (%d) reached at location '%s'",
          location.maxNumberOfWarehouses, warehouse.location);
      throw new WebApplicationException(
          "Maximum number of warehouses already reached for location '"
              + warehouse.location
              + "'.",
          400);
    }

    // 4. Total capacity at the location (existing + new) must not exceed the location's max
    long existingTotalCapacity =
        atSameLocation.stream()
            .mapToLong(w -> w.capacity != null ? w.capacity : 0)
            .sum();
    if (warehouse.capacity == null || existingTotalCapacity + warehouse.capacity > location.maxCapacity) {
      LOG.warnf("Creation rejected: capacity %d would exceed max %d at location '%s'",
          warehouse.capacity, location.maxCapacity, warehouse.location);
      throw new WebApplicationException(
          "Warehouse capacity would exceed the maximum allowed for location '"
              + warehouse.location
              + "'.",
          400);
    }

    // 5. Stock must not exceed the warehouse's own capacity
    if (warehouse.stock != null && warehouse.stock > warehouse.capacity) {
      LOG.warnf("Creation rejected: stock %d exceeds capacity %d", warehouse.stock, warehouse.capacity);
      throw new WebApplicationException("Stock cannot exceed warehouse capacity.", 400);
    }

    warehouse.createdAt = LocalDateTime.now();
    warehouseStore.create(warehouse);
    LOG.infof("Warehouse [buc=%s] created successfully", warehouse.businessUnitCode);
  }
}
