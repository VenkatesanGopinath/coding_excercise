package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.DomainNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.fulfillment.domain.ports.FulfillmentStore;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ArchiveWarehouseUseCase implements ArchiveWarehouseOperation {

  private static final Logger LOG = Logger.getLogger(ArchiveWarehouseUseCase.class);

  private final WarehouseStore warehouseStore;
  private final FulfillmentStore fulfillmentStore;

  public ArchiveWarehouseUseCase(WarehouseStore warehouseStore, FulfillmentStore fulfillmentStore) {
    this.warehouseStore = warehouseStore;
    this.fulfillmentStore = fulfillmentStore;
  }

  @Override
  @Transactional
  public void archive(String businessUnitCode) {
    LOG.infof("Archiving warehouse [buc=%s]", businessUnitCode);
    Warehouse existing = warehouseStore.findByBusinessUnitCode(businessUnitCode);
    if (existing == null) {
      LOG.warnf("Archive rejected: warehouse '%s' not found or already archived", businessUnitCode);
      throw new DomainNotFoundException(
          "Warehouse '" + businessUnitCode + "' not found or already archived.");
    }

    existing.archivedAt = LocalDateTime.now();
    warehouseStore.update(existing);
    LOG.infof("Warehouse [buc=%s] archived successfully at %s",
        businessUnitCode, existing.archivedAt);

    // Cascade: remove all fulfillment assignments pointing to this now-archived warehouse.
    fulfillmentStore.removeByWarehouse(businessUnitCode);
    LOG.infof("Fulfillment assignments for warehouse [buc=%s] removed", businessUnitCode);
  }
}
