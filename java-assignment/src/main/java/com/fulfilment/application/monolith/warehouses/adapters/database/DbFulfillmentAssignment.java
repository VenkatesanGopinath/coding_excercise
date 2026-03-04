package com.fulfilment.application.monolith.warehouses.adapters.database;

import com.fulfilment.application.monolith.warehouses.domain.models.FulfillmentAssignment;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * JPA entity for persisting fulfillment assignments.
 * The unique constraint prevents the same (warehouse, product, store) triple
 * from being assigned more than once.
 */
@Entity
@Table(
    name = "fulfillment_assignment",
    uniqueConstraints =
        @UniqueConstraint(
            columnNames = {"warehouse_buc", "product_id", "store_id"}))
public class DbFulfillmentAssignment extends PanacheEntityBase {

  @Id @GeneratedValue public Long id;

  @Column(name = "warehouse_buc", nullable = false)
  public String warehouseBusinessUnitCode;

  @Column(name = "product_id", nullable = false)
  public Long productId;

  @Column(name = "store_id", nullable = false)
  public Long storeId;

  public FulfillmentAssignment toDomain() {
    var f = new FulfillmentAssignment();
    f.id = this.id;
    f.warehouseBusinessUnitCode = this.warehouseBusinessUnitCode;
    f.productId = this.productId;
    f.storeId = this.storeId;
    return f;
  }
}
