package com.fulfilment.application.monolith.products;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.core.IsNot.not;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ProductEndpointTest {

  @Test
  public void testCrudProduct() {
    final String path = "product";

    // List all, should have all 3 products the database has initially:
    given()
        .when()
        .get(path)
        .then()
        .statusCode(200)
        .body(containsString("TONSTAD"), containsString("KALLAX"), containsString("BESTÅ"));

    // Delete the TONSTAD:
    given().when().delete(path + "/1").then().statusCode(204);

    // List all, TONSTAD should be missing now:
    given()
        .when()
        .get(path)
        .then()
        .statusCode(200)
        .body(not(containsString("TONSTAD")), containsString("KALLAX"), containsString("BESTÅ"));
  }

  @Test
  public void testGetNonExistentProduct_returns404() {
    given().when().get("product/99999").then().statusCode(404);
  }

  @Test
  public void testDeleteNonExistentProduct_returns404() {
    given().when().delete("product/99999").then().statusCode(404);
  }

  @Test
  public void testCreateProduct_duplicateName_returnsError() {
    // TONSTAD already exists in seed data — creating it again violates the unique constraint
    String body = "{\"name\": \"TONSTAD\", \"stock\": 5}";
    int status =
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("product")
            .then()
            .extract()
            .statusCode();
    // DB unique constraint violation — framework returns 500 (constraint violation)
    org.junit.jupiter.api.Assertions.assertTrue(
        status >= 400,
        "Expected an error status for duplicate product name, got: " + status);
  }
}
