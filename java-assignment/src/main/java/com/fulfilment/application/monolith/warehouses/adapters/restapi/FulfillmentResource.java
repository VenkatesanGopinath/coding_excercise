package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fulfilment.application.monolith.warehouses.domain.models.FulfillmentAssignment;
import com.fulfilment.application.monolith.warehouses.domain.ports.AssignFulfillmentOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.FulfillmentStore;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.jboss.logging.Logger;

@RequestScoped
@Path("/warehouse/{businessUnitCode}/fulfillment")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FulfillmentResource {

  private static final Logger LOG = Logger.getLogger(FulfillmentResource.class);

  @Inject private AssignFulfillmentOperation assignFulfillmentOperation;
  @Inject private FulfillmentStore fulfillmentStore;

  @POST
  public Response assign(
      @PathParam("businessUnitCode") String buc,
      @Valid FulfillmentRequest request) {
    LOG.infof("POST /warehouse/%s/fulfillment — assigning product=%d to store=%d",
        buc, request.productId, request.storeId);
    FulfillmentAssignment assignment =
        assignFulfillmentOperation.assign(buc, request.productId, request.storeId);
    return Response.status(Response.Status.CREATED).entity(assignment).build();
  }

  @GET
  public List<FulfillmentAssignment> listByWarehouse(
      @PathParam("businessUnitCode") String buc) {
    LOG.debugf("GET /warehouse/%s/fulfillment", buc);
    return fulfillmentStore.findByWarehouse(buc);
  }

  @DELETE
  @Path("/{id}")
  @Transactional
  public Response remove(
      @PathParam("businessUnitCode") String buc,
      @PathParam("id") Long id) {
    LOG.infof("DELETE /warehouse/%s/fulfillment/%d", buc, id);
    FulfillmentAssignment found = fulfillmentStore.findAssignment(id, buc);
    if (found == null) {
      LOG.warnf("Fulfillment assignment %d not found for warehouse '%s'", id, buc);
      throw new WebApplicationException(
          "Fulfillment assignment " + id + " not found for warehouse '" + buc + "'.", 404);
    }
    fulfillmentStore.remove(id);
    return Response.noContent().build();
  }

  public static class FulfillmentRequest {
    @NotNull public Long productId;
    @NotNull public Long storeId;
  }
}
