package com.fulfilment.application.monolith.warehouses.domain.models;

/**
 * Domain model representing a fulfillment assignment:
 * a Warehouse acting as a fulfillment unit for a Product delivered to a Store.
 */
public class FulfillmentAssignment {

  public Long id;
  public String warehouseBusinessUnitCode;
  public Long productId;
  public Long storeId;
}
