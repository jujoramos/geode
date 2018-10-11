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
package org.apache.geode.management.internal.security;

import static org.apache.geode.distributed.ConfigurationProperties.SECURITY_MANAGER;
import static org.mockito.Mockito.mock;

import javax.management.ObjectName;

import org.assertj.core.api.SoftAssertions;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import org.apache.geode.management.GatewaySenderMXBean;
import org.apache.geode.management.ManagementService;
import org.apache.geode.management.internal.beans.GatewaySenderMBean;
import org.apache.geode.security.SimpleTestSecurityManager;
import org.apache.geode.test.junit.categories.SecurityTest;
import org.apache.geode.test.junit.rules.ConnectionConfiguration;
import org.apache.geode.test.junit.rules.MBeanServerConnectionRule;
import org.apache.geode.test.junit.rules.ServerStarterRule;

@Category(SecurityTest.class)
public class GatewaySenderMBeanSecurityTest {
  private static GatewaySenderMBean mock = mock(GatewaySenderMBean.class);
  private static ObjectName mockBeanName = null;
  private static ManagementService service = null;

  private GatewaySenderMXBean bean;

  @ClassRule
  public static ServerStarterRule server = new ServerStarterRule().withJMXManager()
      .withProperty(SECURITY_MANAGER, SimpleTestSecurityManager.class.getName()).withAutoStart();

  @Rule
  public MBeanServerConnectionRule connectionRule =
      new MBeanServerConnectionRule(server::getJmxPort);

  @BeforeClass
  public static void beforeClass() throws Exception {
    // the server does not have a GatewaySenderMXBean registered initially, has to register a mock
    // one.
    service = ManagementService.getManagementService(server.getCache());
    mockBeanName = ObjectName.getInstance("GemFire", "key", "value");
    service.registerMBean(mock, mockBeanName);
  }

  @AfterClass
  public static void afterClass() {
    service.unregisterMBean(mockBeanName);
  }

  @Before
  public void before() throws Exception {
    bean = connectionRule.getProxyMXBean(GatewaySenderMXBean.class);
  }

  @Test
  @ConnectionConfiguration(user = "data,cluster", password = "data,cluster")
  public void testAllAccess() {
    bean.getAlertThreshold();
    bean.getAverageDistributionTimePerBatch();
    bean.getBatchSize();
    bean.getMaximumQueueMemory();
    bean.getOrderPolicy();
    bean.isBatchConflationEnabled();
    bean.isManualStart();
    bean.pause();
    bean.rebalance();
    bean.resume();
    bean.start();
    bean.stop();
  }

  @Test
  @ConnectionConfiguration(user = "stranger", password = "stranger")
  public void testNoAccess() {
    SoftAssertions softly = new SoftAssertions();

    softly.assertThatThrownBy(() -> bean.getAlertThreshold())
        .hasMessageContaining(ResourcePermissions.CLUSTER_READ.toString());
    softly.assertThatThrownBy(() -> bean.getAverageDistributionTimePerBatch())
        .hasMessageContaining(ResourcePermissions.CLUSTER_READ.toString());
    softly.assertThatThrownBy(() -> bean.getBatchSize())
        .hasMessageContaining(ResourcePermissions.CLUSTER_READ.toString());
    softly.assertThatThrownBy(() -> bean.getMaximumQueueMemory())
        .hasMessageContaining(ResourcePermissions.CLUSTER_READ.toString());
    softly.assertThatThrownBy(() -> bean.getOrderPolicy())
        .hasMessageContaining(ResourcePermissions.CLUSTER_READ.toString());
    softly.assertThatThrownBy(() -> bean.isBatchConflationEnabled())
        .hasMessageContaining(ResourcePermissions.CLUSTER_READ.toString());
    softly.assertThatThrownBy(() -> bean.isManualStart())
        .hasMessageContaining(ResourcePermissions.CLUSTER_READ.toString());
    softly.assertThatThrownBy(() -> bean.pause())
        .hasMessageContaining(TestCommand.clusterManageGateway.toString());
    softly.assertThatThrownBy(() -> bean.rebalance())
        .hasMessageContaining(TestCommand.clusterManageGateway.toString());
    softly.assertThatThrownBy(() -> bean.resume())
        .hasMessageContaining(TestCommand.clusterManageGateway.toString());
    softly.assertThatThrownBy(() -> bean.start())
        .hasMessageContaining(TestCommand.clusterManageGateway.toString());
    softly.assertThatThrownBy(() -> bean.stop())
        .hasMessageContaining(TestCommand.clusterManageGateway.toString());

    softly.assertAll();
  }
}
