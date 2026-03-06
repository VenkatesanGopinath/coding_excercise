package com.fulfilment.application.monolith.fulfillment.adapters.database;

import com.fulfilment.application.monolith.fulfillment.domain.models.FulfillmentAssignment;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "fulfillment_assignment",
    uniqueConstraints =
        @UniqueConstraint(columnNames = {"warehouseBusinessUnitCode", "productId", "storeId"}),
    indexes = {
      @Index(name = "idx_fa_warehouse", columnList = "warehouseBusinessUnitCode"),
      @Index(name = "idx_fa_product_store", columnList = "productId, storeId"),
      @Index(name = "idx_fa_store", columnList = "storeId")
    })
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
