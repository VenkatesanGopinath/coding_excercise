package com.fulfilment.application.monolith.warehouses.adapters.health;

import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Readiness health check for the Warehouse subsystem.
 * Reports UP when the warehouse store is accessible and returns the count of active warehouses.
 * Exposed at: GET /q/health/ready
 */
@Readiness
@ApplicationScoped
public class WarehouseHealthCheck implements HealthCheck {

  @Inject WarehouseStore warehouseStore;

  @Override
  public HealthCheckResponse call() {
    try {
      int activeCount = warehouseStore.getAll().size();
      return HealthCheckResponse.named("warehouse-store")
          .up()
          .withData("activeWarehouses", activeCount)
          .build();
    } catch (Exception e) {
      return HealthCheckResponse.named("warehouse-store")
          .down()
          .withData("error", e.getMessage())
          .build();
    }
  }
}
