package com.fulfilment.application.monolith.warehouses.adapters.database;

import com.fulfilment.application.monolith.warehouses.domain.models.FulfillmentAssignment;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "fulfillment_assignment",
    uniqueConstraints =
        @UniqueConstraint(columnNames = {"warehouseBusinessUnitCode", "productId", "storeId"}))
public class DbFulfillmentAssignment {

  @Id @GeneratedValue public Long id;

  public String warehouseBusinessUnitCode;

  public Long productId;

  public Long storeId;

  public DbFulfillmentAssignment() {}

  public FulfillmentAssignment toDomain() {
    var f = new FulfillmentAssignment();
    f.id = this.id;
    f.warehouseBusinessUnitCode = this.warehouseBusinessUnitCode;
    f.productId = this.productId;
    f.storeId = this.storeId;
    return f;
  }
}
