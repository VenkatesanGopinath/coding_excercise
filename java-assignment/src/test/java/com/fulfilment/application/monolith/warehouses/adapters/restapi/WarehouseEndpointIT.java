package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WarehouseEndpointIT {

  @Test
  @Order(1)
  public void testSimpleListWarehouses() {

    final String path = "warehouse";

    // List all, should have all 3 warehouses the database has initially:
    given()
        .when()
        .get(path)
        .then()
        .statusCode(200)
        .body(containsString("MWH.001"), containsString("MWH.012"), containsString("MWH.023"));
  }

  @Test
  @Order(2)
  public void testSimpleCheckingArchivingWarehouses() {

    final String path = "warehouse";

    // List all initially — verify locations are present:
    given()
        .when()
        .get(path)
        .then()
        .statusCode(200)
        .body(
            containsString("MWH.001"),
            containsString("MWH.012"),
            containsString("MWH.023"),
            containsString("ZWOLLE-001"),
            containsString("AMSTERDAM-001"),
            containsString("TILBURG-001"));

    // Archive MWH.001 (at ZWOLLE-001) using its businessUnitCode:
    given().when().delete(path + "/MWH.001").then().statusCode(204);

    // List all — MWH.001 / ZWOLLE-001 should be absent now:
    given()
        .when()
        .get(path)
        .then()
        .statusCode(200)
        .body(
            not(containsString("MWH.001")),
            not(containsString("ZWOLLE-001")),
            containsString("AMSTERDAM-001"),
            containsString("TILBURG-001"));
  }
}
