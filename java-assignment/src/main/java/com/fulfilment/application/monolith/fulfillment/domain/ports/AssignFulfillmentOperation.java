package com.fulfilment.application.monolith.fulfillment.domain.ports;

import com.fulfilment.application.monolith.fulfillment.domain.models.FulfillmentAssignment;

public interface AssignFulfillmentOperation {

  FulfillmentAssignment assign(String warehouseBuc, Long productId, Long storeId);
}
