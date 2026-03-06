package com.fulfilment.application.monolith.warehouses.domain.models;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class Warehouse {

  // unique identifier
  @NotBlank
  public String businessUnitCode;

  @NotBlank
  public String location;

  @NotNull
  @Min(0)
  public Integer capacity;

  @Min(0)
  public Integer stock;

  public LocalDateTime createdAt;

  public LocalDateTime archivedAt;
}
