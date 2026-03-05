package com.fulfilment.application.monolith.warehouses.adapters.database;

import com.fulfilment.application.monolith.products.Product;
import com.fulfilment.application.monolith.stores.Store;
import com.fulfilment.application.monolith.warehouses.domain.models.FulfillmentAssignment;
import com.fulfilment.application.monolith.warehouses.domain.ports.FulfillmentStore;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.jboss.logging.Logger;

@ApplicationScoped
public class FulfillmentRepository implements FulfillmentStore, PanacheRepository<DbFulfillmentAssignment> {

  private static final Logger LOG = Logger.getLogger(FulfillmentRepository.class);

  @Inject EntityManager em;

  @Override
  public boolean productExists(Long productId) {
    return em.find(Product.class, productId) != null;
  }

  @Override
  public boolean storeExists(Long storeId) {
    return em.find(Store.class, storeId) != null;
  }

  @Override
  public long countDistinctProductsForWarehouse(String warehouseBuc) {
    return em.createQuery(
            "SELECT COUNT(DISTINCT f.productId) FROM DbFulfillmentAssignment f "
                + "WHERE f.warehouseBusinessUnitCode = :buc",
            Long.class)
        .setParameter("buc", warehouseBuc)
        .getSingleResult();
  }

  @Override
  public long countDistinctWarehousesForProductAndStore(Long productId, Long storeId) {
    return em.createQuery(
            "SELECT COUNT(DISTINCT f.warehouseBusinessUnitCode) FROM DbFulfillmentAssignment f "
                + "WHERE f.productId = :pid AND f.storeId = :sid",
            Long.class)
        .setParameter("pid", productId)
        .setParameter("sid", storeId)
        .getSingleResult();
  }

  @Override
  public long countDistinctWarehousesForStore(Long storeId) {
    return em.createQuery(
            "SELECT COUNT(DISTINCT f.warehouseBusinessUnitCode) FROM DbFulfillmentAssignment f "
                + "WHERE f.storeId = :sid",
            Long.class)
        .setParameter("sid", storeId)
        .getSingleResult();
  }

  @Override
  public FulfillmentAssignment assign(String warehouseBuc, Long productId, Long storeId) {
    LOG.infof("Persisting fulfillment assignment [warehouse=%s, product=%d, store=%d]",
        warehouseBuc, productId, storeId);
    var db = new DbFulfillmentAssignment();
    db.warehouseBusinessUnitCode = warehouseBuc;
    db.productId = productId;
    db.storeId = storeId;
    this.persist(db);
    return db.toDomain();
  }

  @Override
  public List<FulfillmentAssignment> findByWarehouse(String warehouseBuc) {
    return this.find("warehouseBusinessUnitCode", warehouseBuc)
        .stream()
        .map(DbFulfillmentAssignment::toDomain)
        .toList();
  }

  @Override
  public FulfillmentAssignment findAssignment(Long id, String warehouseBuc) {
    return this.find("id = ?1 and warehouseBusinessUnitCode = ?2", id, warehouseBuc)
        .firstResultOptional()
        .map(DbFulfillmentAssignment::toDomain)
        .orElse(null);
  }

  @Override
  public void remove(Long id) {
    LOG.infof("Removing fulfillment assignment [id=%d]", id);
    DbFulfillmentAssignment entity = this.findById(id);
    if (entity != null) {
      this.delete(entity);
    }
  }
}
