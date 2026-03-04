package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import java.time.LocalDateTime;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ReplaceWarehouseUseCase implements ReplaceWarehouseOperation {

  private static final Logger LOG = Logger.getLogger(ReplaceWarehouseUseCase.class);

  private final WarehouseStore warehouseStore;
  private final LocationResolver locationResolver;

  public ReplaceWarehouseUseCase(WarehouseStore warehouseStore, LocationResolver locationResolver) {
    this.warehouseStore = warehouseStore;
    this.locationResolver = locationResolver;
  }

  @Override
  @Transactional
  public void replace(Warehouse newWarehouse) {
    LOG.infof("Replacing warehouse [buc=%s] with new location=%s, capacity=%d",
        newWarehouse.businessUnitCode, newWarehouse.location, newWarehouse.capacity);

    // The old warehouse is identified by the same businessUnitCode
    Warehouse oldWarehouse = warehouseStore.findByBusinessUnitCode(newWarehouse.businessUnitCode);
    if (oldWarehouse == null) {
      LOG.warnf("Replace rejected: no active warehouse found for buc='%s'", newWarehouse.businessUnitCode);
      throw new WebApplicationException(
          "Active warehouse '" + newWarehouse.businessUnitCode + "' not found.", 404);
    }

    // Validate the new warehouse's location
    Location location = locationResolver.resolveByIdentifier(newWarehouse.location);
    if (location == null) {
      LOG.warnf("Replace rejected: location '%s' is not valid", newWarehouse.location);
      throw new WebApplicationException(
          "Location '" + newWarehouse.location + "' is not valid.", 400);
    }

    // When moving to a different location, that location must still have a free slot
    boolean movingToNewLocation = !newWarehouse.location.equals(oldWarehouse.location);
    if (movingToNewLocation) {
      long activeAtNewLocation =
          warehouseStore.getAll().stream()
              .filter(w -> newWarehouse.location.equals(w.location))
              .count();
      if (activeAtNewLocation >= location.maxNumberOfWarehouses) {
        LOG.warnf("Replace rejected: max warehouses (%d) reached at new location '%s'",
            location.maxNumberOfWarehouses, newWarehouse.location);
        throw new WebApplicationException(
            "Maximum number of warehouses already reached for location '"
                + newWarehouse.location + "'.", 400);
      }
    }

    // New capacity must not push the new location over its total capacity limit.
    // If staying at the same location, the old warehouse's capacity is freed first.
    long existingCapacityAtNewLocation =
        warehouseStore.getAll().stream()
            .filter(w -> newWarehouse.location.equals(w.location))
            .mapToLong(w -> w.capacity != null ? w.capacity : 0)
            .sum();
    long effectiveExisting = movingToNewLocation
        ? existingCapacityAtNewLocation
        : existingCapacityAtNewLocation - (oldWarehouse.capacity != null ? oldWarehouse.capacity : 0);
    if (newWarehouse.capacity == null || effectiveExisting + newWarehouse.capacity > location.maxCapacity) {
      LOG.warnf("Replace rejected: new capacity %d would exceed max %d at location '%s'",
          newWarehouse.capacity, location.maxCapacity, newWarehouse.location);
      throw new WebApplicationException(
          "Warehouse capacity would exceed the maximum allowed for location '"
              + newWarehouse.location + "'.", 400);
    }

    // New warehouse capacity must be able to accommodate the old warehouse's stock
    if (newWarehouse.capacity == null || newWarehouse.capacity < oldWarehouse.stock) {
      LOG.warnf("Replace rejected: new capacity %d cannot accommodate old stock %d",
          newWarehouse.capacity, oldWarehouse.stock);
      throw new WebApplicationException(
          "New warehouse capacity must be able to accommodate the stock of the replaced warehouse ("
              + oldWarehouse.stock
              + ").",
          400);
    }

    // Stock of the new warehouse must match the stock of the old warehouse
    if (newWarehouse.stock == null || !newWarehouse.stock.equals(oldWarehouse.stock)) {
      LOG.warnf("Replace rejected: new stock %d does not match old stock %d",
          newWarehouse.stock, oldWarehouse.stock);
      throw new WebApplicationException(
          "New warehouse stock must match the replaced warehouse stock ("
              + oldWarehouse.stock
              + ").",
          400);
    }

    // Archive the old warehouse
    oldWarehouse.archivedAt = LocalDateTime.now();
    warehouseStore.update(oldWarehouse);
    LOG.infof("Old warehouse [buc=%s] archived", oldWarehouse.businessUnitCode);

    // Create the new warehouse with the same Business Unit Code
    newWarehouse.createdAt = LocalDateTime.now();
    warehouseStore.create(newWarehouse);
    LOG.infof("New warehouse [buc=%s] created successfully at location=%s",
        newWarehouse.businessUnitCode, newWarehouse.location);
  }
}
