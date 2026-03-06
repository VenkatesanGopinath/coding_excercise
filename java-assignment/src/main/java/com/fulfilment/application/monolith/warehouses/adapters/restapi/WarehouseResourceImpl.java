package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.warehouse.api.WarehouseResource;
import com.warehouse.api.beans.Warehouse;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.WebApplicationException;
import java.util.List;
import org.jboss.logging.Logger;

@Authenticated
@RequestScoped
public class WarehouseResourceImpl implements WarehouseResource {

  private static final Logger LOG = Logger.getLogger(WarehouseResourceImpl.class);

  @Inject private WarehouseRepository warehouseRepository;
  @Inject private CreateWarehouseOperation createWarehouseOperation;
  @Inject private ArchiveWarehouseOperation archiveWarehouseOperation;
  @Inject private ReplaceWarehouseOperation replaceWarehouseOperation;

  @Override
  public List<Warehouse> listAllWarehousesUnits() {
    LOG.debug("GET /warehouse — listing all active warehouses");
    return warehouseRepository.getAll().stream().map(this::toWarehouseResponse).toList();
  }

  @Override
  public Warehouse createANewWarehouseUnit(@NotNull Warehouse data) {
    LOG.infof("POST /warehouse — creating warehouse [buc=%s]", data.getBusinessUnitCode());
    var domain = toDomainWarehouse(data);
    createWarehouseOperation.create(domain);
    // Return the created entity (with its generated id) so the caller can use it for GET/DELETE.
    return toWarehouseResponse(warehouseRepository.findByBusinessUnitCode(domain.businessUnitCode));
  }

  @Override
  public Warehouse getAWarehouseUnitByID(String id) {
    LOG.debugf("GET /warehouse/%s", id);
    var warehouse = resolveByDatabaseId(id);
    if (warehouse == null) {
      LOG.warnf("GET /warehouse/%s — not found", id);
      throw new WebApplicationException("Warehouse with id '" + id + "' not found.", 404);
    }
    return toWarehouseResponse(warehouse);
  }

  @Override
  public void archiveAWarehouseUnitByID(String id) {
    LOG.infof("DELETE /warehouse/%s — archiving", id);
    // Resolve the numeric id to the domain entity to get its businessUnitCode.
    var warehouse = resolveByDatabaseId(id);
    if (warehouse == null) {
      LOG.warnf("DELETE /warehouse/%s — not found", id);
      throw new WebApplicationException("Warehouse with id '" + id + "' not found.", 404);
    }
    // The archive use case operates on the business unit code (the domain identifier).
    archiveWarehouseOperation.archive(warehouse.businessUnitCode);
  }

  @Override
  public Warehouse replaceTheCurrentActiveWarehouse(
      String businessUnitCode, @NotNull Warehouse data) {
    LOG.infof("POST /warehouse/%s/replacement — replacing", businessUnitCode);
    var newWarehouse = toDomainWarehouse(data);
    newWarehouse.businessUnitCode = businessUnitCode;
    replaceWarehouseOperation.replace(newWarehouse);
    return toWarehouseResponse(warehouseRepository.findByBusinessUnitCode(businessUnitCode));
  }

  /** Parses the String path param as a Long and delegates to the repository's true findById. */
  private com.fulfilment.application.monolith.warehouses.domain.models.Warehouse resolveByDatabaseId(
      String id) {
    try {
      return warehouseRepository.findByDatabaseId(Long.parseLong(id));
    } catch (NumberFormatException e) {
      return null; // non-numeric id → treat as not found
    }
  }

  private Warehouse toWarehouseResponse(
      com.fulfilment.application.monolith.warehouses.domain.models.Warehouse warehouse) {
    var response = new Warehouse();
    response.setId(warehouse.id != null ? String.valueOf(warehouse.id) : null);
    response.setBusinessUnitCode(warehouse.businessUnitCode);
    response.setLocation(warehouse.location);
    response.setCapacity(warehouse.capacity);
    response.setStock(warehouse.stock);
    return response;
  }

  private com.fulfilment.application.monolith.warehouses.domain.models.Warehouse toDomainWarehouse(
      Warehouse data) {
    var warehouse = new com.fulfilment.application.monolith.warehouses.domain.models.Warehouse();
    warehouse.businessUnitCode = data.getBusinessUnitCode();
    warehouse.location = data.getLocation();
    warehouse.capacity = data.getCapacity();
    warehouse.stock = data.getStock();
    return warehouse;
  }
}
