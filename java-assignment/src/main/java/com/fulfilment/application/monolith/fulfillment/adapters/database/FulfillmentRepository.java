package com.fulfilment.application.monolith.fulfillment.adapters.database;

import com.fulfilment.application.monolith.fulfillment.domain.models.FulfillmentAssignment;
import com.fulfilment.application.monolith.fulfillment.domain.ports.FulfillmentStore;
import com.fulfilment.application.monolith.products.Product;
import com.fulfilment.application.monolith.stores.Store;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import org.jboss.logging.Logger;

@ApplicationScoped
public class FulfillmentRepository implements FulfillmentStore, PanacheRepository<DbFulfillmentAssignment> {

  private static final Logger LOG = Logger.getLogger(FulfillmentRepository.class);

  @Override
  public boolean productExists(Long productId) {
    // Product is a plain JPA entity; use Panache's getEntityManager() rather than injecting one.
    return getEntityManager().find(Product.class, productId) != null;
  }

  @Override
  public boolean storeExists(Long storeId) {
    // Store extends PanacheEntity — use its static finder directly.
    return Store.findById(storeId) != null;
  }

  @Override
  public long countDistinctProductsForWarehouse(String warehouseBuc) {
    // COUNT(DISTINCT ...) is not supported by Panache's built-in count(); use JPQL via
    // Panache's getEntityManager() — no separate @Inject EntityManager needed.
    return getEntityManager().createQuery(
            "SELECT COUNT(DISTINCT f.productId) FROM DbFulfillmentAssignment f "
                + "WHERE f.warehouseBusinessUnitCode = :buc",
            Long.class)
        .setParameter("buc", warehouseBuc)
        .getSingleResult();
  }

  @Override
  public long countDistinctWarehousesForProductAndStore(Long productId, Long storeId) {
    return getEntityManager().createQuery(
            "SELECT COUNT(DISTINCT f.warehouseBusinessUnitCode) FROM DbFulfillmentAssignment f "
                + "WHERE f.productId = :pid AND f.storeId = :sid",
            Long.class)
        .setParameter("pid", productId)
        .setParameter("sid", storeId)
        .getSingleResult();
  }

  @Override
  public long countDistinctWarehousesForStore(Long storeId) {
    return getEntityManager().createQuery(
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
    List<FulfillmentAssignment> result = this.find("warehouseBusinessUnitCode", warehouseBuc)
        .stream()
        .map(DbFulfillmentAssignment::toDomain)
        .toList();
    LOG.debugf("findByWarehouse('%s') returned %d assignments", warehouseBuc, result.size());
    return result;
  }

  @Override
  public FulfillmentAssignment findAssignment(Long id, String warehouseBuc) {
    FulfillmentAssignment found = this.find(
            "id = ?1 and warehouseBusinessUnitCode = ?2", id, warehouseBuc)
        .firstResultOptional()
        .map(DbFulfillmentAssignment::toDomain)
        .orElse(null);
    if (found == null) {
      LOG.debugf("No fulfillment assignment found for id=%d and warehouse='%s'", id, warehouseBuc);
    }
    return found;
  }

  @Override
  public void remove(Long id) {
    LOG.infof("Removing fulfillment assignment [id=%d]", id);
    DbFulfillmentAssignment entity = this.findById(id);
    if (entity != null) {
      this.delete(entity);
    } else {
      LOG.warnf("remove() called but fulfillment assignment not found for id=%d", id);
    }
  }

  @Override
  public void removeByWarehouse(String warehouseBuc) {
    long deleted = this.delete("warehouseBusinessUnitCode", warehouseBuc);
    LOG.infof("removeByWarehouse('%s') deleted %d assignment(s)", warehouseBuc, deleted);
  }
}
