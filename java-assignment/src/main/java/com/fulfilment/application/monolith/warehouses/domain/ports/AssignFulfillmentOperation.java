package com.fulfilment.application.monolith.warehouses.domain.ports;

import com.fulfilment.application.monolith.warehouses.domain.models.FulfillmentAssignment;

/**
 * Use-case port for assigning a Warehouse as a fulfillment unit
 * for a Product delivered to a Store.
 */
public interface AssignFulfillmentOperation {

  FulfillmentAssignment assign(String warehouseBuc, Long productId, Long storeId);
}
