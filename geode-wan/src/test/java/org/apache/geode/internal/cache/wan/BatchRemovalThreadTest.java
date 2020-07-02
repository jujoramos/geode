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
package org.apache.geode.internal.cache.wan;

import static org.apache.geode.test.awaitility.GeodeAwaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.apache.geode.CancelCriterion;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.test.junit.runners.CategoryWithParameterizedRunnerFactory;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(CategoryWithParameterizedRunnerFactory.class)
public class BatchRemovalThreadTest {

  @Parameterized.Parameters(name = "syncInterval:{0}")
  public static Collection<Integer> data() {
    return Arrays.asList(5, AbstractBatchRemovalThread.DEFAULT_MESSAGE_SYNC_INTERVAL, 15);
  }

  // the old version of Geode we're testing against
  @Parameterized.Parameter
  public Integer messageSyncInterval;

  @Test
  public void distributeIfNeededMethodIsCalledEveryTenMillisecondsByDefault()
      throws InterruptedException {
    InternalCache mockCache = mock(InternalCache.class);
    CancelCriterion mockCriterion = mock(CancelCriterion.class);
    when(mockCriterion.isCancelInProgress()).thenReturn(false);
    when(mockCache.getCancelCriterion()).thenReturn(mockCriterion);
    AbstractBatchRemovalThread.messageSyncInterval = messageSyncInterval;

    DummyBatchRemovalThread dummyBatchRemovalThread = new DummyBatchRemovalThread(mockCache);
    dummyBatchRemovalThread.start();

    // Can not know when the OS will give control to our thread, so we just assert that average
    // difference is close to 10 milliseconds.
    await().untilAsserted(
        () -> assertThat(dummyBatchRemovalThread.getInvocations().size()).isGreaterThan(1000));
    dummyBatchRemovalThread.shutdown();
    dummyBatchRemovalThread.join();

    List<Long> invocations = dummyBatchRemovalThread.getInvocations();
    List<Long> timeDifferences = new ArrayList<>();
    for (int i = 1; i < invocations.size(); i++) {
      long diff = invocations.get(i) - invocations.get(i - 1);
      // Difference should be higher than the default sleep time.
      int differenceInMilliseconds = (int) TimeUnit.NANOSECONDS.toMillis(diff);
      assertThat(differenceInMilliseconds).isGreaterThanOrEqualTo(messageSyncInterval);
      timeDifferences.add(diff);
    }

    OptionalDouble optionalDouble = timeDifferences.stream().mapToDouble(d -> d).average();
    int averageDifference =
        (int) TimeUnit.NANOSECONDS.toMillis(new Double(optionalDouble.orElse(0)).longValue());
    assertThat(averageDifference).isBetween(messageSyncInterval, messageSyncInterval + 1);
  }

  private static class DummyBatchRemovalThread extends AbstractBatchRemovalThread {
    private final List<Long> invocations = new CopyOnWriteArrayList<>();

    public List<Long> getInvocations() {
      return invocations;
    }

    public DummyBatchRemovalThread(InternalCache cache) {
      super("MockThread", mock(Logger.class), cache);
    }

    @Override
    public void distributeIfNeeded() {
      invocations.add(System.nanoTime());
    }
  }
}
