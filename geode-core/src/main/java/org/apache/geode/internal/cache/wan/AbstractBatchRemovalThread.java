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

import org.apache.logging.log4j.Logger;

import org.apache.geode.CancelException;
import org.apache.geode.SystemFailure;
import org.apache.geode.annotations.internal.MutableForTesting;
import org.apache.geode.internal.cache.InternalCache;

/**
 * Background thread used by primary queues to distribute remove operations to secondary
 * queues once event(s) have been removed from the primary.
 */
public abstract class AbstractBatchRemovalThread extends Thread {
  // The default frequency (in milliseconds) at which a message will be sent by the primary to all
  // the secondary nodes to remove the events which have already been dispatched from the queue.
  public static final int DEFAULT_MESSAGE_SYNC_INTERVAL = 10;

  protected final Logger logger;
  protected final InternalCache cache;
  private volatile boolean shutdown = false;

  @MutableForTesting
  protected static volatile int messageSyncInterval = DEFAULT_MESSAGE_SYNC_INTERVAL;

  /**
   * Creates and initializes the thread
   *
   * @param logger the logger to use.
   * @param internalCache the {@link InternalCache}
   */
  public AbstractBatchRemovalThread(String threadName, Logger logger, InternalCache internalCache) {
    super(threadName);
    this.setDaemon(true);
    this.logger = logger;
    this.cache = internalCache;
  }

  private boolean checkCancelled() {
    if (shutdown) {
      return true;
    }

    return cache.getCancelCriterion().isCancelInProgress();
  }

  /**
   * Execute the actual distribution of the removal logic, only when there were events removed
   * from the queue since the last time we checked.
   */
  public abstract void distributeIfNeeded() throws InterruptedException;

  @Override
  public void run() {
    try {
      for (;;) {
        try {
          // be somewhat tolerant of failures
          if (checkCancelled()) {
            break;
          }

          boolean interrupted = Thread.interrupted();
          try {
            synchronized (this) {
              this.wait(messageSyncInterval);
            }
          } catch (InterruptedException e) {
            interrupted = true;
            if (checkCancelled()) {
              break;
            }

            break; // desperation...we must be trying to shut down...?
          } finally {
            // Not particularly important since we're exiting the thread,
            // but following the pattern is still good practice...
            if (interrupted)
              Thread.currentThread().interrupt();
          }

          // Distribute the remove operation, if events were removed from the queue.
          distributeIfNeeded();
        } catch (CancelException e) {
          // be somewhat tolerant of failures
          if (logger.isDebugEnabled()) {
            logger.debug("BatchRemovalThread is exiting due to cancellation");
          }
          break;
        } catch (VirtualMachineError err) {
          SystemFailure.initiateFailure(err);
          // If this ever returns, rethrow the error.
          // We're poisoned now, so don't let this thread continue.
          throw err;
        } catch (Throwable t) {
          Error err;
          if (t instanceof Error && SystemFailure.isJVMFailureError(err = (Error) t)) {
            SystemFailure.initiateFailure(err);
            // If this ever returns, rethrow the error.
            // We're poisoned now, so don't let this thread continue.
            throw err;
          }
          // Whenever you catch Error or Throwable, you must also check for fatal JVM error (see
          // above). However, there is _still_ a possibility that you are dealing with a cascading
          // error condition, so you also need to check to see if the JVM is still usable.
          SystemFailure.checkFailure();
          if (checkCancelled()) {
            break;
          }
          if (logger.isDebugEnabled()) {
            logger.debug("BatchRemovalThread: ignoring exception", t);
          }
        }
      } // for
    } catch (CancelException e) {
      // ensure exit message is printed
      if (logger.isDebugEnabled()) {
        logger.debug("BatchRemovalThread exiting due to cancellation: " + e);
      }
    } finally {
      logger.info("The QueueRemovalThread is done.");
    }
  }

  /**
   * shutdown this thread and the caller thread will join this thread
   */
  public void shutdown() {
    this.shutdown = true;
    this.interrupt();
    boolean interrupted = Thread.interrupted();

    try {
      this.join(15 * 1000);
    } catch (InterruptedException e) {
      interrupted = true;
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }

    if (this.isAlive()) {
      logger.warn("QueueRemovalThread ignored cancellation");
    }
  }
}
