package com.fulfilment.application.monolith.fulfillment.domain.usecases;

import com.fulfilment.application.monolith.fulfillment.domain.models.FulfillmentAssignment;
import com.fulfilment.application.monolith.fulfillment.domain.ports.AssignFulfillmentOperation;
import com.fulfilment.application.monolith.fulfillment.domain.ports.FulfillmentStore;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.DomainNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.DomainValidationException;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AssignFulfillmentUseCase implements AssignFulfillmentOperation {

  private static final Logger LOG = Logger.getLogger(AssignFulfillmentUseCase.class);

  private static final int MAX_WAREHOUSES_PER_PRODUCT_PER_STORE = 2;
  private static final int MAX_WAREHOUSES_PER_STORE = 3;
  private static final int MAX_PRODUCTS_PER_WAREHOUSE = 5;

  private final WarehouseStore warehouseStore;
  private final FulfillmentStore fulfillmentStore;

  public AssignFulfillmentUseCase(WarehouseStore warehouseStore, FulfillmentStore fulfillmentStore) {
    this.warehouseStore = warehouseStore;
    this.fulfillmentStore = fulfillmentStore;
  }

  @Override
  @Transactional
  public FulfillmentAssignment assign(String warehouseBuc, Long productId, Long storeId) {
    LOG.infof("Assigning fulfillment [warehouse=%s, product=%d, store=%d]",
        warehouseBuc, productId, storeId);

    if (warehouseStore.findByBusinessUnitCode(warehouseBuc) == null) {
      LOG.warnf("Assignment rejected: warehouse '%s' not found or archived", warehouseBuc);
      throw new DomainNotFoundException(
          "Warehouse '" + warehouseBuc + "' not found or not active.");
    }

    if (!fulfillmentStore.productExists(productId)) {
      LOG.warnf("Assignment rejected: product %d not found", productId);
      throw new DomainNotFoundException("Product " + productId + " not found.");
    }

    if (!fulfillmentStore.storeExists(storeId)) {
      LOG.warnf("Assignment rejected: store %d not found", storeId);
      throw new DomainNotFoundException("Store " + storeId + " not found.");
    }

    // Constraint 3: Each Warehouse can store at most 5 product types
    long productTypesInWarehouse = fulfillmentStore.countDistinctProductsForWarehouse(warehouseBuc);
    if (productTypesInWarehouse >= MAX_PRODUCTS_PER_WAREHOUSE) {
      LOG.warnf("Assignment rejected: warehouse '%s' already fulfils %d product types (max %d)",
          warehouseBuc, productTypesInWarehouse, MAX_PRODUCTS_PER_WAREHOUSE);
      throw new DomainValidationException(
          "Warehouse '" + warehouseBuc + "' already fulfils the maximum of "
              + MAX_PRODUCTS_PER_WAREHOUSE + " product types.");
    }

    // Constraint 1: Each Product can be fulfilled by at most 2 Warehouses per Store
    long warehousesForProductAtStore =
        fulfillmentStore.countDistinctWarehousesForProductAndStore(productId, storeId);
    if (warehousesForProductAtStore >= MAX_WAREHOUSES_PER_PRODUCT_PER_STORE) {
      LOG.warnf("Assignment rejected: product %d already fulfilled by %d warehouses for store %d (max %d)",
          productId, warehousesForProductAtStore, storeId, MAX_WAREHOUSES_PER_PRODUCT_PER_STORE);
      throw new DomainValidationException(
          "Product " + productId + " is already fulfilled by "
              + MAX_WAREHOUSES_PER_PRODUCT_PER_STORE + " warehouses for store " + storeId + ".");
    }

    // Constraint 2: Each Store can be fulfilled by at most 3 Warehouses
    long warehousesForStore = fulfillmentStore.countDistinctWarehousesForStore(storeId);
    if (warehousesForStore >= MAX_WAREHOUSES_PER_STORE) {
      LOG.warnf("Assignment rejected: store %d already fulfilled by %d warehouses (max %d)",
          storeId, warehousesForStore, MAX_WAREHOUSES_PER_STORE);
      throw new DomainValidationException(
          "Store " + storeId + " is already fulfilled by the maximum of "
              + MAX_WAREHOUSES_PER_STORE + " warehouses.");
    }

    FulfillmentAssignment result = fulfillmentStore.assign(warehouseBuc, productId, storeId);
    LOG.infof("Fulfillment assignment created [id=%d, warehouse=%s, product=%d, store=%d]",
        result.id, warehouseBuc, productId, storeId);
    return result;
  }
}
