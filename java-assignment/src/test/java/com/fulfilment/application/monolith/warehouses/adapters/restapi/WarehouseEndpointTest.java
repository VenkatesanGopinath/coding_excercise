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

  // --- POST /warehouse: additional validation ---

  @Test
  void create_missingCapacity_returns400() {
    // capacity is null in JSON — use-case guard rejects it
    String body =
        """
        {
          "businessUnitCode": "MWH.NOCAP",
          "location": "AMSTERDAM-001",
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
  void create_blankLocation_returns400() {
    String body =
        """
        {
          "businessUnitCode": "MWH.BLANKLOC",
          "location": "   ",
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

  // --- Lifecycle: create → archive → GET ---

  @Test
  void create_thenArchive_thenGetById_returns404() {
    // Use a unique BUC to avoid interference with other tests.
    // AMSTERDAM-001 allows up to 5 warehouses so there is capacity.
    given()
        .contentType(ContentType.JSON)
        .body("""
            {
              "businessUnitCode": "MWH.LIFECYCLE",
              "location": "AMSTERDAM-001",
              "capacity": 10,
              "stock": 0
            }
            """)
        .when()
        .post(PATH)
        .then()
        .statusCode(200);

    given()
        .when()
        .delete(PATH + "/MWH.LIFECYCLE")
        .then()
        .statusCode(204);

    given()
        .when()
        .get(PATH + "/MWH.LIFECYCLE")
        .then()
        .statusCode(404);
  }

  @Test
  void create_sameBucAsArchivedWarehouse_succeeds() {
    // BUC uniqueness is enforced only among ACTIVE warehouses.
    // After archiving, the same BUC can be reused.
    given()
        .contentType(ContentType.JSON)
        .body("""
            {
              "businessUnitCode": "MWH.REUSE",
              "location": "AMSTERDAM-001",
              "capacity": 10,
              "stock": 0
            }
            """)
        .when()
        .post(PATH)
        .then()
        .statusCode(200);

    given()
        .when()
        .delete(PATH + "/MWH.REUSE")
        .then()
        .statusCode(204);

    // Creating the same BUC again must succeed
    given()
        .contentType(ContentType.JSON)
        .body("""
            {
              "businessUnitCode": "MWH.REUSE",
              "location": "AMSTERDAM-001",
              "capacity": 10,
              "stock": 0
            }
            """)
        .when()
        .post(PATH)
        .then()
        .statusCode(200);
  }

  // --- GET /q/health/ready ---

  @Test
  void healthCheck_returns200_withActiveWarehouseCount() {
    given()
        .when()
        .get("/q/health/ready")
        .then()
        .statusCode(200)
        .body(containsString("warehouse-store"))
        .body(containsString("UP"));
  }
}
