package com.fulfilment.application.monolith.warehouses.domain.models;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class Warehouse {

  /** Database-generated primary key — used by GET /warehouse/{id} and DELETE /warehouse/{id}. */
  public Long id;

  // unique business identifier
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
