package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import java.time.LocalDateTime;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ArchiveWarehouseUseCase implements ArchiveWarehouseOperation {

  private static final Logger LOG = Logger.getLogger(ArchiveWarehouseUseCase.class);

  private final WarehouseStore warehouseStore;

  public ArchiveWarehouseUseCase(WarehouseStore warehouseStore) {
    this.warehouseStore = warehouseStore;
  }

  @Override
  @Transactional
  public void archive(String id) {
    LOG.infof("Archiving warehouse [buc=%s]", id);
    Warehouse existing = warehouseStore.findById(id);
    if (existing == null) {
      LOG.warnf("Archive rejected: warehouse '%s' not found or already archived", id);
      throw new WebApplicationException(
          "Warehouse '" + id + "' not found or already archived.", 404);
    }

    existing.archivedAt = LocalDateTime.now();
    warehouseStore.update(existing);
    LOG.infof("Warehouse [buc=%s] archived successfully at %s", id, existing.archivedAt);
  }
}
