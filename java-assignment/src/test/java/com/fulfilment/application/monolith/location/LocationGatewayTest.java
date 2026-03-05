package com.fulfilment.application.monolith.location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LocationGatewayTest {

  private LocationGateway locationGateway;

  @BeforeEach
  void setUp() {
    locationGateway = new LocationGateway();
  }

  @Test
  public void testWhenResolveExistingLocationShouldReturn() {
    Location location = locationGateway.resolveByIdentifier("ZWOLLE-001");

    assertNotNull(location);
    assertEquals("ZWOLLE-001", location.identification);
    assertEquals(1, location.maxNumberOfWarehouses);
    assertEquals(40, location.maxCapacity);
  }

  @Test
  public void testWhenResolveAnotherExistingLocationShouldReturn() {
    Location location = locationGateway.resolveByIdentifier("AMSTERDAM-001");

    assertNotNull(location);
    assertEquals("AMSTERDAM-001", location.identification);
    assertEquals(5, location.maxNumberOfWarehouses);
    assertEquals(100, location.maxCapacity);
  }

  @Test
  public void testWhenResolveNonExistentLocationShouldReturnNull() {
    Location location = locationGateway.resolveByIdentifier("NOWHERE-999");

    assertNull(location);
  }

  @Test
  public void testWhenResolveNullIdentifierShouldReturnNull() {
    Location location = locationGateway.resolveByIdentifier(null);

    assertNull(location);
  }

  @Test
  public void testWhenResolveBlankIdentifierShouldReturnNull() {
    Location location = locationGateway.resolveByIdentifier("   ");

    assertNull(location);
  }
}
