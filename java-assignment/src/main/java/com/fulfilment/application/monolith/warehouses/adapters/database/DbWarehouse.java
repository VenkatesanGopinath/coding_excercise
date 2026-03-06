package com.fulfilment.application.monolith.warehouses.adapters.database;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "warehouse",
    indexes = {
      @Index(name = "idx_warehouse_buc_active", columnList = "businessUnitCode, archivedAt"),
      @Index(name = "idx_warehouse_location_active", columnList = "location, archivedAt")
    })
@Cacheable
public class DbWarehouse {

  @Id @GeneratedValue public Long id;

  // DB-level uniqueness is enforced per active warehouse via the application layer.
  // The column is indexed for fast lookup; a partial unique index would require DDL migration.
  @Column(nullable = false)
  public String businessUnitCode;

  @Column(nullable = false)
  public String location;

  public Integer capacity;

  public Integer stock;

  public LocalDateTime createdAt;

  public LocalDateTime archivedAt;

  public DbWarehouse() {}

  public Warehouse toWarehouse() {
    var warehouse = new Warehouse();
    warehouse.id = this.id;
    warehouse.businessUnitCode = this.businessUnitCode;
    warehouse.location = this.location;
    warehouse.capacity = this.capacity;
    warehouse.stock = this.stock;
    warehouse.createdAt = this.createdAt;
    warehouse.archivedAt = this.archivedAt;
    return warehouse;
  }
}
