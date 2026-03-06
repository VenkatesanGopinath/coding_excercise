package com.fulfilment.application.monolith.fulfillment.adapters.restapi;

import jakarta.validation.constraints.NotNull;

/**
 * Request body DTO for POST /warehouse/{buc}/fulfillment.
 * Declared as a top-level class so that @Valid + @NotNull fire correctly
 * in Quarkus RESTEasy Reactive (static inner-class body params are not validated).
 */
public class FulfillmentRequest {

  @NotNull
  public Long productId;

  @NotNull
  public Long storeId;
}
