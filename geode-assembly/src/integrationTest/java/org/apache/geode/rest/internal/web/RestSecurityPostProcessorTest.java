/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.rest.internal.web;

import static org.apache.geode.distributed.ConfigurationProperties.SECURITY_MANAGER;
import static org.apache.geode.distributed.ConfigurationProperties.SECURITY_POST_PROCESSOR;
import static org.apache.geode.test.junit.rules.HttpResponseAssert.assertResponse;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.http.MediaType;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.rest.internal.web.controllers.Customer;
import org.apache.geode.rest.internal.web.controllers.RedactingPostProcessor;
import org.apache.geode.security.TestSecurityManager;
import org.apache.geode.test.junit.categories.RestAPITest;
import org.apache.geode.test.junit.categories.SecurityTest;
import org.apache.geode.test.junit.rules.GeodeDevRestClient;
import org.apache.geode.test.junit.rules.RequiresGeodeHome;
import org.apache.geode.test.junit.rules.ServerStarterRule;

@Category({SecurityTest.class, RestAPITest.class})
public class RestSecurityPostProcessorTest {

  @ClassRule
  public static ServerStarterRule serverStarter = new ServerStarterRule()
      .withProperty(TestSecurityManager.SECURITY_JSON,
          "org/apache/geode/management/internal/security/clientServer.json")
      .withProperty(SECURITY_MANAGER, TestSecurityManager.class.getName())
      .withProperty(SECURITY_POST_PROCESSOR, RedactingPostProcessor.class.getName())
      .withRestService().withAutoStart();

  private final GeodeDevRestClient restClient =
      new GeodeDevRestClient("localhost", serverStarter.getHttpPort());

  @Rule
  public RequiresGeodeHome requiresGeodeHome = new RequiresGeodeHome();

  @BeforeClass
  public static void before() {
    Region<String, Customer> region =
        serverStarter.getCache().<String, Customer>createRegionFactory(RegionShortcut.REPLICATE)
            .create("customers");
    region.put("1", new Customer(1L, "John", "Doe", "555555555"));
    region.put("2", new Customer(2L, "Richard", "Roe", "222533554"));
    region.put("3", new Customer(3L, "Jane", "Doe", "555223333"));
    region.put("4", new Customer(4L, "Jane", "Roe", "555443333"));
  }

  /**
   * Test post-processing of a retrieved key from the server.
   */
  @Test
  public void getRegionKey() throws Exception {
    // Test a single key
    JsonNode jsonNode =
        assertResponse(restClient.doGet("/customers/1", "dataReader", "1234567"))
            .hasStatusCode(200)
            .hasContentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
            .getJsonObject();

    assertThat(jsonNode.get("socialSecurityNumber").asText()).isEqualTo("*********");
    assertThat(jsonNode.get("customerId").asLong()).isEqualTo(1L);

    // Try with super-user
    jsonNode =
        assertResponse(restClient.doGet("/customers/1", "super-user", "1234567"))
            .hasStatusCode(200)
            .hasContentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
            .getJsonObject();
    assertThat(jsonNode.get("socialSecurityNumber").asText()).isEqualTo("555555555");
    assertThat(jsonNode.get("customerId").asLong()).isEqualTo(1L);
  }

  // Test multiple keys
  @Test
  public void getMultipleRegionKeys() throws Exception {
    JsonNode jsonNode =
        assertResponse(restClient.doGet("/customers/1,3", "dataReader", "1234567"))
            .hasStatusCode(200)
            .hasContentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
            .getJsonObject();

    JsonNode customers = jsonNode.get("customers");
    final int length = customers.size();
    assertThat(length).isEqualTo(2);
    JsonNode customer = customers.get(0);
    assertThat(customer.get("socialSecurityNumber").asText()).isEqualTo("*********");
    assertThat(customer.get("customerId").asLong()).isEqualTo(1);
    customer = customers.get(1);
    assertThat(customer.get("socialSecurityNumber").asText()).isEqualTo("*********");
    assertThat(customer.get("customerId").asLong()).isEqualTo(3);
  }

  @Test
  public void getRegion() throws Exception {
    JsonNode jsonNode = assertResponse(restClient.doGet("/customers", "dataReader", "1234567"))
        .hasStatusCode(200)
        .hasContentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
        .getJsonObject();

    JsonNode customers = jsonNode.get("customers");
    final int length = customers.size();
    for (int index = 0; index < length; ++index) {
      JsonNode customer = customers.get(index);
      assertThat(customer.get("socialSecurityNumber").asText()).isEqualTo("*********");
      assertThat(customer.get("customerId").asLong()).isEqualTo((long) index + 1);
    }
  }

  @Test
  public void adhocQuery() throws Exception {
    String query = "/queries/adhoc?q="
        + URLEncoder.encode("SELECT * FROM /customers order by customerId", "UTF-8");
    JsonNode jsonArray = assertResponse(restClient.doGet(query, "dataReader", "1234567"))
        .hasStatusCode(200)
        .hasContentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
        .getJsonObject();

    final int length = jsonArray.size();
    for (int index = 0; index < length; ++index) {
      JsonNode customer = jsonArray.get(index);
      assertThat(customer.get("socialSecurityNumber").asText()).isEqualTo("*********");
      assertThat(customer.get("customerId").asLong()).isEqualTo((long) index + 1);
    }
  }

  @Test
  public void namedQuery() throws Exception {
    // Declare the named query
    String namedQuery = "SELECT c FROM /customers c WHERE c.customerId = $1";

    // Install the named query
    assertResponse(
        restClient.doPost("/queries?id=selectCustomer&q=" + URLEncoder.encode(namedQuery, "UTF-8"),
            "dataReader", "1234567", ""))
                .hasStatusCode(201);

    // Verify the query has been installed
    String query = "/queries";
    assertResponse(restClient.doGet(query, "dataReader", "1234567"))
        .hasStatusCode(200)
        .hasContentType(MediaType.APPLICATION_JSON_UTF8_VALUE);

    // Execute the query
    JsonNode jsonArray =
        assertResponse(restClient.doPost("/queries/selectCustomer", "dataReader", "1234567",
            "{" + "\"@type\": \"int\"," + "\"@value\": 1" + "}"))
                .hasStatusCode(200)
                .getJsonObject();

    assertThat(jsonArray.size() == 1).isTrue();
    JsonNode customer = jsonArray.get(0);
    assertThat(customer.get("socialSecurityNumber").asText()).isEqualTo("*********");
    assertThat(customer.get("customerId").asLong()).isEqualTo(1L);
  }
}
