package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/**
 * HTTP-layer tests for the Warehouse REST adapter using a real Quarkus test context.
 *
 * GET /warehouse/{id} and DELETE /warehouse/{id} use the numeric database primary key,
 * as defined in warehouse-openapi.yaml. Seed data (import.sql) sets:
 *   id=1 → MWH.001, id=2 → MWH.012, id=3 → MWH.023
 */
@QuarkusTest
@TestSecurity(user = "admin", roles = {"warehouse-admin"})
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

  @Test
  void listAll_returns200_responseIncludesIdField() {
    // Each warehouse in the list must carry a non-null numeric id field.
    given()
        .when()
        .get(PATH)
        .then()
        .statusCode(200)
        .body("[0].id", notNullValue());
  }

  // --- GET /warehouse/{id}  (numeric DB primary key, per OpenAPI spec) ---

  @Test
  void getById_existingWarehouse_returns200_withCorrectFields() {
    // Seed: id=2 → MWH.012, AMSTERDAM-001
    given()
        .when()
        .get(PATH + "/2")
        .then()
        .statusCode(200)
        .body("id", equalTo("2"))
        .body("businessUnitCode", equalTo("MWH.012"))
        .body("location", equalTo("AMSTERDAM-001"));
  }

  @Test
  void getById_nonExistentId_returns404() {
    given()
        .when()
        .get(PATH + "/99999")
        .then()
        .statusCode(404);
  }

  @Test
  void getById_nonNumericId_returns404() {
    // Path param must be a Long; a BUC string is not a valid id.
    given()
        .when()
        .get(PATH + "/MWH.NONEXISTENT")
        .then()
        .statusCode(404);
  }

  // --- POST /warehouse ---

  @Test
  void create_invalidLocation_returns400() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            {
              "businessUnitCode": "MWH.BADLOC",
              "location": "NOWHERE-999",
              "capacity": 20,
              "stock": 0
            }
            """)
        .when()
        .post(PATH)
        .then()
        .statusCode(400);
  }

  @Test
  void create_duplicateBusinessUnitCode_returns400() {
    // MWH.023 already exists from seed data
    given()
        .contentType(ContentType.JSON)
        .body("""
            {
              "businessUnitCode": "MWH.023",
              "location": "EINDHOVEN-001",
              "capacity": 20,
              "stock": 0
            }
            """)
        .when()
        .post(PATH)
        .then()
        .statusCode(400);
  }

  @Test
  void create_stockExceedsCapacity_returns400() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            {
              "businessUnitCode": "MWH.BADSTOCK",
              "location": "EINDHOVEN-001",
              "capacity": 10,
              "stock": 50
            }
            """)
        .when()
        .post(PATH)
        .then()
        .statusCode(400);
  }

  @Test
  void create_missingCapacity_returns400() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            {
              "businessUnitCode": "MWH.NOCAP",
              "location": "AMSTERDAM-001",
              "stock": 0
            }
            """)
        .when()
        .post(PATH)
        .then()
        .statusCode(400);
  }

  @Test
  void create_blankLocation_returns400() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            {
              "businessUnitCode": "MWH.BLANKLOC",
              "location": "   ",
              "capacity": 20,
              "stock": 0
            }
            """)
        .when()
        .post(PATH)
        .then()
        .statusCode(400);
  }

  @Test
  void create_returns200_withIdInResponse() {
    // The POST response must include the generated numeric id so callers can
    // use GET /warehouse/{id} and DELETE /warehouse/{id} immediately.
    given()
        .contentType(ContentType.JSON)
        .body("""
            {
              "businessUnitCode": "MWH.IDCHECK",
              "location": "AMSTERDAM-001",
              "capacity": 10,
              "stock": 0
            }
            """)
        .when()
        .post(PATH)
        .then()
        .statusCode(200)
        .body("id", notNullValue())
        .body("businessUnitCode", equalTo("MWH.IDCHECK"));
  }

  // --- DELETE /warehouse/{id} ---

  @Test
  void archive_nonExistentId_returns404() {
    given()
        .when()
        .delete(PATH + "/99999")
        .then()
        .statusCode(404);
  }

  @Test
  void archive_nonNumericId_returns404() {
    given()
        .when()
        .delete(PATH + "/MWH.DOESNOTEXIST")
        .then()
        .statusCode(404);
  }

  // --- POST /warehouse/{businessUnitCode}/replacement ---

  @Test
  void replace_nonExistentWarehouse_returns404() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            {
              "businessUnitCode": "MWH.GHOST",
              "location": "EINDHOVEN-001",
              "capacity": 30,
              "stock": 0
            }
            """)
        .when()
        .post(PATH + "/MWH.GHOST/replacement")
        .then()
        .statusCode(404);
  }

  @Test
  void replace_stockMismatch_returns400() {
    // MWH.012 seed stock=5; sending stock=99 should fail validation
    given()
        .contentType(ContentType.JSON)
        .body("""
            {
              "businessUnitCode": "MWH.012",
              "location": "AMSTERDAM-001",
              "capacity": 60,
              "stock": 99
            }
            """)
        .when()
        .post(PATH + "/MWH.012/replacement")
        .then()
        .statusCode(400);
  }

  // --- Lifecycle: create → archive (by numeric id) → GET (by numeric id) ---

  @Test
  void create_thenArchive_thenGetById_returns404() {
    // Step 1: create — response contains the generated numeric id.
    String id =
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
            .statusCode(200)
            .extract()
            .path("id");

    // Step 2: archive using the numeric id from the response.
    given()
        .when()
        .delete(PATH + "/" + id)
        .then()
        .statusCode(204);

    // Step 3: GET by the same numeric id must now return 404.
    given()
        .when()
        .get(PATH + "/" + id)
        .then()
        .statusCode(404);
  }

  @Test
  void create_sameBucAsArchivedWarehouse_succeeds() {
    // BUC uniqueness is enforced only among ACTIVE warehouses.
    // After archiving (by numeric id), the same BUC can be reused.
    String id =
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
            .statusCode(200)
            .extract()
            .path("id");

    given()
        .when()
        .delete(PATH + "/" + id)
        .then()
        .statusCode(204);

    // Creating the same BUC again must succeed.
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
  void readinessCheck_returns200_withDbConnectionOk() {
    given()
        .when()
        .get("/q/health/ready")
        .then()
        .statusCode(200)
        .body(containsString("warehouse-store"))
        .body(containsString("UP"))
        .body(containsString("dbConnection"))
        .body(containsString("OK"));
  }

  // --- GET /q/health/live ---

  @Test
  void livenessCheck_returns200_withUptimeData() {
    given()
        .when()
        .get("/q/health/live")
        .then()
        .statusCode(200)
        .body(containsString("application-liveness"))
        .body(containsString("UP"))
        .body(containsString("uptimeMs"));
  }

  // --- GET /q/health (aggregated) ---

  @Test
  void aggregatedHealth_returns200_whenBothChecksPass() {
    given()
        .when()
        .get("/q/health")
        .then()
        .statusCode(200)
        .body(containsString("application-liveness"))
        .body(containsString("warehouse-store"))
        .body(containsString("UP"));
  }
}
