package com.fulfilment.application.monolith.warehouses.domain.ports;

import com.fulfilment.application.monolith.warehouses.domain.models.FulfillmentAssignment;

public interface AssignFulfillmentOperation {

  FulfillmentAssignment assign(String warehouseBuc, Long productId, Long storeId);
}
