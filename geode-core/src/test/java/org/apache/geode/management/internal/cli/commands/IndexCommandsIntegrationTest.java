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
package org.apache.geode.management.internal.cli.commands;

import static org.apache.geode.distributed.ConfigurationProperties.GROUPS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.DataPolicy;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionFactory;
import org.apache.geode.cache.query.Index;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.management.cli.Result;
import org.apache.geode.management.internal.cli.domain.Stock;
import org.apache.geode.management.internal.cli.i18n.CliStrings;
import org.apache.geode.management.internal.cli.result.CommandResult;
import org.apache.geode.management.internal.cli.util.CommandStringBuilder;
import org.apache.geode.test.junit.categories.IntegrationTest;
import org.apache.geode.test.junit.rules.GfshShellConnectionRule;
import org.apache.geode.test.junit.rules.Server;
import org.apache.geode.test.junit.rules.ServerStarterRule;

/**
 * this test class test: CreateIndexCommand, DestroyIndexCommand, ListIndexCommand
 */
@Category(IntegrationTest.class)
public class IndexCommandsIntegrationTest {
  private static final String regionName = "regionA";
  private static final String groupName = "groupA";
  private static final String indexName = "indexA";


  @ClassRule
  public static ServerStarterRule server =
      new ServerStarterRule().withProperty(GROUPS, groupName).withJMXManager().withAutoStart();

  @BeforeClass
  public static void beforeClass() throws Exception {
    InternalCache cache = server.getCache();
    Region region = createPartitionedRegion(regionName, cache, String.class, Stock.class);
    region.put("VMW", new Stock("VMW", 98));
    region.put("APPL", new Stock("APPL", 600));
  }

  @Rule
  public GfshShellConnectionRule gfsh = new GfshShellConnectionRule();

  @Before
  public void before() throws Exception {
    connect(server);
  }

  @After
  public void after() throws Exception {
    // destroy all existing indexes
    Collection<Index> indices = server.getCache().getQueryService().getIndexes();
    indices.stream().map(Index::getName).forEach(indexName -> {
      gfsh.executeAndVerifyCommand("destroy index --name=" + indexName);
    });

    gfsh.executeAndVerifyCommand("list index");
    assertThat(gfsh.getGfshOutput()).contains("No Indexes Found");
  }

  public void connect(Server server) throws Exception {
    gfsh.connectAndVerify(server.getJmxPort(), GfshShellConnectionRule.PortType.jmxManager);
  }

  @Test
  public void testCreate() throws Exception {
    createSimpleIndexA();
  }

  @Test
  public void testCreateIndexWithMultipleIterators() throws Exception {
    CommandStringBuilder createStringBuilder = new CommandStringBuilder(CliStrings.CREATE_INDEX);
    createStringBuilder.addOption(CliStrings.CREATE_INDEX__NAME, "indexA");
    createStringBuilder.addOption(CliStrings.CREATE_INDEX__EXPRESSION, "\"h.low\"");
    createStringBuilder.addOption(CliStrings.CREATE_INDEX__REGION,
        "\"/" + regionName + " s, s.history h\"");

    gfsh.executeAndVerifyCommand(createStringBuilder.toString());
    assertThat(gfsh.getGfshOutput()).contains("Index successfully created");

    gfsh.executeAndVerifyCommand("list index");
    assertThat(gfsh.getGfshOutput()).contains("indexA");
  }

  @Test
  public void testListIndexValidField() throws Exception {
    CommandStringBuilder createStringBuilder = new CommandStringBuilder(CliStrings.CREATE_INDEX);
    createStringBuilder.addOption(CliStrings.CREATE_INDEX__NAME, indexName);
    createStringBuilder.addOption(CliStrings.CREATE_INDEX__EXPRESSION, "\"h.low\"");
    createStringBuilder.addOption(CliStrings.CREATE_INDEX__REGION,
        "\"/" + regionName + " s, s.history h\"");

    gfsh.executeAndVerifyCommand(createStringBuilder.toString());
    assertThat(gfsh.getGfshOutput()).contains("Index successfully created");

    gfsh.executeAndVerifyCommand("list index");
    assertThat(gfsh.getGfshOutput()).contains("indexA");
  }

  @Test
  public void testCannotCreateIndexWithExistingIndexName() throws Exception {
    createSimpleIndexA();

    // CREATE the same index
    CommandStringBuilder csb = new CommandStringBuilder(CliStrings.CREATE_INDEX);
    csb.addOption(CliStrings.CREATE_INDEX__NAME, indexName);
    csb.addOption(CliStrings.CREATE_INDEX__EXPRESSION, "key");
    csb.addOption(CliStrings.CREATE_INDEX__REGION, "/" + regionName);
    csb.addOption(CliStrings.CREATE_INDEX__TYPE, "hash");

    CommandResult result = gfsh.executeCommand(csb.toString());
    assertThat(result.getStatus()).isEqualTo(Result.Status.ERROR);
  }

  @Test
  public void creatIndexWithNoBeginningSlash() throws Exception {
    CommandStringBuilder csb = new CommandStringBuilder(CliStrings.CREATE_INDEX);
    csb.addOption(CliStrings.CREATE_INDEX__NAME, indexName);
    csb.addOption(CliStrings.CREATE_INDEX__EXPRESSION, "key");
    csb.addOption(CliStrings.CREATE_INDEX__REGION, regionName);
    gfsh.executeAndVerifyCommand(csb.toString());
    assertThat(gfsh.getGfshOutput()).contains("Index successfully created");

    gfsh.executeAndVerifyCommand("list index");
    assertThat(gfsh.getGfshOutput()).contains("indexA");
  }

  @Test
  public void testCannotCreateIndexInIncorrectRegion() throws Exception {
    // Create index on a wrong regionPath
    CommandStringBuilder csb = new CommandStringBuilder(CliStrings.CREATE_INDEX);
    csb.addOption(CliStrings.CREATE_INDEX__NAME, indexName);
    csb.addOption(CliStrings.CREATE_INDEX__EXPRESSION, "key");
    csb.addOption(CliStrings.CREATE_INDEX__REGION, "/InvalidRegionName");
    csb.addOption(CliStrings.CREATE_INDEX__TYPE, "hash");

    CommandResult result = gfsh.executeCommand(csb.toString());
    assertThat(result.getStatus()).isEqualTo(Result.Status.ERROR);
    assertThat(gfsh.getGfshOutput()).contains("Region not found : \"/InvalidRegionName\"");
  }

  @Test
  public void cannotCreateWithTheSameName() throws Exception {
    createSimpleIndexA();
    gfsh.execute("create index --name=indexA --expression=key --region=/regionA");
    assertThat(gfsh.getGfshOutput())
        .contains("Index \"indexA\" already exists.  Create failed due to duplicate name");
  }

  @Test
  public void testCannotCreateIndexWithInvalidIndexExpression() throws Exception {
    // Create index with wrong expression
    CommandStringBuilder csb = new CommandStringBuilder(CliStrings.CREATE_INDEX);
    csb.addOption(CliStrings.CREATE_INDEX__NAME, indexName);
    csb.addOption(CliStrings.CREATE_INDEX__EXPRESSION, "InvalidExpressionOption");
    csb.addOption(CliStrings.CREATE_INDEX__REGION, "/" + regionName);
    csb.addOption(CliStrings.CREATE_INDEX__TYPE, "hash");

    CommandResult result = gfsh.executeCommand(csb.toString());
    assertThat(result.getStatus()).isEqualTo(Result.Status.ERROR);
  }

  @Test
  public void testCannotDestroyIndexWithInvalidIndexName() throws Exception {
    // Destroy index with incorrect indexName
    CommandStringBuilder csb = new CommandStringBuilder(CliStrings.DESTROY_INDEX);
    csb.addOption(CliStrings.DESTROY_INDEX__NAME, "IncorrectIndexName");
    CommandResult result = gfsh.executeCommand(csb.toString());
    assertThat(result.getStatus()).isEqualTo(Result.Status.ERROR);
    assertThat(gfsh.getGfshOutput()).contains(
        CliStrings.format(CliStrings.DESTROY_INDEX__INDEX__NOT__FOUND, "IncorrectIndexName"));
  }

  @Test
  public void testCannotDestroyIndexWithInvalidRegion() throws Exception {
    // Destroy index with incorrect region
    CommandStringBuilder csb = new CommandStringBuilder(CliStrings.DESTROY_INDEX);
    csb.addOption(CliStrings.DESTROY_INDEX__NAME, indexName);
    csb.addOption(CliStrings.DESTROY_INDEX__REGION, "IncorrectRegion");
    CommandResult result = gfsh.executeCommand(csb.toString());
    assertThat(result.getStatus()).isEqualTo(Result.Status.ERROR);
    assertThat(gfsh.getGfshOutput()).contains("Region \"IncorrectRegion\" not found.");
  }

  @Test
  public void testCannotDestroyIndexWithInvalidMember() throws Exception {
    // Destroy index with incorrect member name
    CommandStringBuilder csb = new CommandStringBuilder(CliStrings.DESTROY_INDEX);
    csb.addOption(CliStrings.DESTROY_INDEX__NAME, indexName);
    csb.addOption(CliStrings.DESTROY_INDEX__REGION, "Region");
    csb.addOption(CliStrings.MEMBER, "InvalidMemberName");
    CommandResult result = gfsh.executeCommand(csb.toString());
    assertThat(result.getStatus()).isEqualTo(Result.Status.ERROR);
    assertThat(gfsh.getGfshOutput()).contains("No Members Found");
  }

  @Test
  public void testCannotDestroyIndexWithNoOptions() throws Exception {
    // Destroy index with no option
    CommandStringBuilder csb = new CommandStringBuilder(CliStrings.DESTROY_INDEX);
    CommandResult result = gfsh.executeCommand(csb.toString());
    assertThat(result.getStatus()).isEqualTo(Result.Status.ERROR);
    assertThat(gfsh.getGfshOutput()).contains("requires that one or more parameters be provided.");
  }

  @Test
  public void testDestroyIndexViaRegion() throws Exception {
    createSimpleIndexA();

    CommandStringBuilder csb = new CommandStringBuilder(CliStrings.DESTROY_INDEX);
    csb.addOption(CliStrings.DESTROY_INDEX__REGION, regionName);
    gfsh.executeAndVerifyCommand(csb.toString(),
        "Indexes on region : /regionA successfully destroyed");
  }

  @Test
  public void testDestroyIndexViaGroup() throws Exception {
    createSimpleIndexA();

    CommandStringBuilder csb = new CommandStringBuilder(CliStrings.DESTROY_INDEX);
    csb.addOption(CliStrings.GROUP, groupName);
    gfsh.executeAndVerifyCommand(csb.toString(), "Indexes successfully destroyed");
  }

  private void createSimpleIndexA() throws Exception {
    CommandStringBuilder csb = new CommandStringBuilder(CliStrings.CREATE_INDEX);
    csb.addOption(CliStrings.CREATE_INDEX__NAME, indexName);
    csb.addOption(CliStrings.CREATE_INDEX__EXPRESSION, "key");
    csb.addOption(CliStrings.CREATE_INDEX__REGION, "/" + regionName);
    gfsh.executeAndVerifyCommand(csb.toString(), "Index successfully created");
  }

  private static Region<?, ?> createPartitionedRegion(String regionName, Cache cache,
      Class keyConstraint, Class valueConstraint) {
    RegionFactory regionFactory = cache.createRegionFactory();
    regionFactory.setDataPolicy(DataPolicy.PARTITION);
    regionFactory.setKeyConstraint(keyConstraint);
    regionFactory.setValueConstraint(valueConstraint);
    return regionFactory.create(regionName);
  }

}

