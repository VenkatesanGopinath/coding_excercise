package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * HTTP-layer tests for the Fulfillment REST adapter.
 * Seed data (import.sql): warehouses MWH.001/012/023, products id=2,3 (id=1 deleted by ProductEndpointTest),
 * stores id=1,2,3.
 *
 * Note: tests share one Quarkus instance and no @TestTransaction, so each test uses
 * a distinct (warehouse, product, store) combination to avoid interference.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FulfillmentEndpointTest {

  // --- POST: request body validation (400) ---

  @Test
  @Order(4)
  void assign_missingProductId_returns400() {
    // productId is @NotNull — omitting it must trigger bean-validation → 400
    given()
        .contentType(ContentType.JSON)
        .body("{\"storeId\": 1}")
        .when()
        .post("/warehouse/MWH.001/fulfillment")
        .then()
        .statusCode(400);
  }

  @Test
  @Order(5)
  void assign_missingStoreId_returns400() {
    // storeId is @NotNull — omitting it must trigger bean-validation → 400
    given()
        .contentType(ContentType.JSON)
        .body("{\"productId\": 2}")
        .when()
        .post("/warehouse/MWH.001/fulfillment")
        .then()
        .statusCode(400);
  }

  // --- POST: existence validation (404) ---

  @Test
  @Order(1)
  void assign_inactiveWarehouse_returns404() {
    given()
        .contentType(ContentType.JSON)
        .body("{\"productId\": 2, \"storeId\": 1}")
        .when()
        .post("/warehouse/MWH.GHOST/fulfillment")
        .then()
        .statusCode(404);
  }

  @Test
  @Order(2)
  void assign_nonExistentProduct_returns404() {
    given()
        .contentType(ContentType.JSON)
        .body("{\"productId\": 9999, \"storeId\": 1}")
        .when()
        .post("/warehouse/MWH.001/fulfillment")
        .then()
        .statusCode(404);
  }

  @Test
  @Order(3)
  void assign_nonExistentStore_returns404() {
    given()
        .contentType(ContentType.JSON)
        .body("{\"productId\": 2, \"storeId\": 9999}")
        .when()
        .post("/warehouse/MWH.001/fulfillment")
        .then()
        .statusCode(404);
  }

  // --- POST: happy path and constraint 1 (max 2 warehouses per product per store) ---

  @Test
  @Order(10)
  void assign_firstWarehouse_returns201() {
    // Product 2 for store 1 via MWH.001 — first assignment
    given()
        .contentType(ContentType.JSON)
        .body("{\"productId\": 2, \"storeId\": 1}")
        .when()
        .post("/warehouse/MWH.001/fulfillment")
        .then()
        .statusCode(201)
        .body(containsString("MWH.001"))
        .body(containsString("\"productId\":2"))
        .body(containsString("\"storeId\":1"));
  }

  @Test
  @Order(11)
  void assign_secondWarehouse_samProductAndStore_returns201() {
    // Product 2 for store 1 via MWH.012 — second warehouse, still within the limit of 2
    given()
        .contentType(ContentType.JSON)
        .body("{\"productId\": 2, \"storeId\": 1}")
        .when()
        .post("/warehouse/MWH.012/fulfillment")
        .then()
        .statusCode(201);
  }

  @Test
  @Order(12)
  void assign_thirdWarehouse_sameProductAndStore_returns400() {
    // Product 2 for store 1 already has 2 warehouses (MWH.001 + MWH.012) — third must be rejected
    given()
        .contentType(ContentType.JSON)
        .body("{\"productId\": 2, \"storeId\": 1}")
        .when()
        .post("/warehouse/MWH.023/fulfillment")
        .then()
        .statusCode(400);
  }

  // --- GET ---

  @Test
  @Order(20)
  void listByWarehouse_returns200() {
    given()
        .when()
        .get("/warehouse/MWH.001/fulfillment")
        .then()
        .statusCode(200);
  }

  @Test
  @Order(21)
  void listByWarehouse_nonExistentWarehouse_returns200WithEmptyList() {
    // The listing endpoint delegates to the repository filtered by BUC.
    // It does NOT check warehouse existence, so it returns an empty array.
    given()
        .when()
        .get("/warehouse/MWH.GHOST/fulfillment")
        .then()
        .statusCode(200)
        .body(containsString("[]"));
  }

  // --- DELETE ---

  @Test
  @Order(30)
  void remove_nonExistentAssignment_returns404() {
    given()
        .when()
        .delete("/warehouse/MWH.001/fulfillment/99999")
        .then()
        .statusCode(404);
  }
}
