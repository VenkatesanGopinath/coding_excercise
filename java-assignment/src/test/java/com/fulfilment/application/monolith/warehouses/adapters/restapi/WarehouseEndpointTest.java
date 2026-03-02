package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/**
 * HTTP-layer tests for the Warehouse REST adapter using a real Quarkus test context.
 * These tests cover status codes, response shapes, and error paths that cannot be
 * exercised by pure unit tests (use cases) or integration tests (WarehouseEndpointIT).
 */
@QuarkusTest
public class WarehouseEndpointTest {

  private static final String PATH = "warehouse";

  // --- GET /warehouse ---

  @Test
  void listAll_returns200_withSeedWarehouses() {
    given()
        .when()
        .get(PATH)
        .then()
        .statusCode(200)
        .body(
            containsString("MWH.001"),
            containsString("MWH.012"),
            containsString("MWH.023"));
  }

  // --- GET /warehouse/{id} ---

  @Test
  void getById_existingWarehouse_returns200_withCorrectFields() {
    given()
        .when()
        .get(PATH + "/MWH.012")
        .then()
        .statusCode(200)
        .body("businessUnitCode", equalTo("MWH.012"))
        .body("location", equalTo("AMSTERDAM-001"));
  }

  @Test
  void getById_nonExistentWarehouse_returns404() {
    given()
        .when()
        .get(PATH + "/MWH.NONEXISTENT")
        .then()
        .statusCode(404);
  }

  // --- POST /warehouse ---

  @Test
  void create_invalidLocation_returns400() {
    String body =
        """
        {
          "businessUnitCode": "MWH.BADLOC",
          "location": "NOWHERE-999",
          "capacity": 20,
          "stock": 0
        }
        """;
    given()
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .post(PATH)
        .then()
        .statusCode(400);
  }

  @Test
  void create_duplicateBusinessUnitCode_returns400() {
    // MWH.023 already exists from seed data
    String body =
        """
        {
          "businessUnitCode": "MWH.023",
          "location": "EINDHOVEN-001",
          "capacity": 20,
          "stock": 0
        }
        """;
    given()
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .post(PATH)
        .then()
        .statusCode(400);
  }

  @Test
  void create_stockExceedsCapacity_returns400() {
    String body =
        """
        {
          "businessUnitCode": "MWH.BADSTOCK",
          "location": "EINDHOVEN-001",
          "capacity": 10,
          "stock": 50
        }
        """;
    given()
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .post(PATH)
        .then()
        .statusCode(400);
  }

  // --- DELETE /warehouse/{id} ---

  @Test
  void archive_nonExistentWarehouse_returns404() {
    given()
        .when()
        .delete(PATH + "/MWH.DOESNOTEXIST")
        .then()
        .statusCode(404);
  }

  // --- POST /warehouse/{businessUnitCode}/replacement ---

  @Test
  void replace_nonExistentWarehouse_returns404() {
    String body =
        """
        {
          "businessUnitCode": "MWH.GHOST",
          "location": "EINDHOVEN-001",
          "capacity": 30,
          "stock": 0
        }
        """;
    given()
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .post(PATH + "/MWH.GHOST/replacement")
        .then()
        .statusCode(404);
  }

  @Test
  void replace_stockMismatch_returns400() {
    // MWH.012 seed stock=5; sending stock=99 should fail validation
    String body =
        """
        {
          "businessUnitCode": "MWH.012",
          "location": "AMSTERDAM-001",
          "capacity": 60,
          "stock": 99
        }
        """;
    given()
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .post(PATH + "/MWH.012/replacement")
        .then()
        .statusCode(400);
  }
}
