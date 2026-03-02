package com.fulfilment.application.monolith.warehouses.adapters.database;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.util.List;
import org.jboss.logging.Logger;

@ApplicationScoped
public class WarehouseRepository implements WarehouseStore, PanacheRepository<DbWarehouse> {

  private static final Logger LOG = Logger.getLogger(WarehouseRepository.class);

  @Override
  public List<Warehouse> getAll() {
    // Return only active (non-archived) warehouses
    List<Warehouse> result = this.find("archivedAt is null").stream().map(DbWarehouse::toWarehouse).toList();
    LOG.debugf("getAll() returned %d active warehouses", result.size());
    return result;
  }

  @Override
  public void create(Warehouse warehouse) {
    LOG.infof("Persisting new warehouse [buc=%s, location=%s, capacity=%d]",
        warehouse.businessUnitCode, warehouse.location, warehouse.capacity);
    DbWarehouse db = new DbWarehouse();
    db.businessUnitCode = warehouse.businessUnitCode;
    db.location = warehouse.location;
    db.capacity = warehouse.capacity;
    db.stock = warehouse.stock;
    db.createdAt = warehouse.createdAt != null ? warehouse.createdAt : LocalDateTime.now();
    db.archivedAt = null;
    this.persist(db);
  }

  @Override
  public void update(Warehouse warehouse) {
    LOG.infof("Updating warehouse [buc=%s, archivedAt=%s]",
        warehouse.businessUnitCode, warehouse.archivedAt);
    DbWarehouse db =
        this.find("businessUnitCode = ?1 and archivedAt is null", warehouse.businessUnitCode)
            .firstResult();
    if (db != null) {
      db.location = warehouse.location;
      db.capacity = warehouse.capacity;
      db.stock = warehouse.stock;
      db.archivedAt = warehouse.archivedAt;
      // Hibernate dirty-checking flushes the changes automatically within the transaction
    } else {
      LOG.warnf("update() called but no active warehouse found for buc='%s'", warehouse.businessUnitCode);
    }
  }

  @Override
  public void remove(Warehouse warehouse) {
    LOG.infof("Removing warehouse [buc=%s]", warehouse.businessUnitCode);
    DbWarehouse db =
        this.find("businessUnitCode = ?1", warehouse.businessUnitCode).firstResult();
    if (db != null) {
      this.delete(db);
    } else {
      LOG.warnf("remove() called but warehouse not found for buc='%s'", warehouse.businessUnitCode);
    }
  }

  @Override
  public Warehouse findByBusinessUnitCode(String buCode) {
    Warehouse found = this.find("businessUnitCode = ?1 and archivedAt is null", buCode)
        .firstResultOptional()
        .map(DbWarehouse::toWarehouse)
        .orElse(null);
    if (found == null) {
      LOG.debugf("No active warehouse found for buc='%s'", buCode);
    }
    return found;
  }
}
