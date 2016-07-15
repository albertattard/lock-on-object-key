/*
 * #%L
 * Lock On Object Key
 * %%
 * Copyright (C) 2012 - 2016 Java Creed
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.javacreed.examples.concurrency.locks.test;

import java.util.concurrent.CyclicBarrier;

/**
 * Provides an algorithm that starts executing the {@link #doStartTogether(int)} method from all thread together. This
 * can be used to simulate a race condition.
 *
 * @author Albert Attard
 */
public abstract class StartTogetherConcurrentTestDriver extends BasicConcurrentTestDriver {

  /**
   * Used to control the flow and makes sure that all threads start executing the {@link #doStartTogether(int)} together
   */
  private final CyclicBarrier cyclicBarrier;

  /**
   *
   * @param size
   * @throws IllegalArgumentException
   */
  public StartTogetherConcurrentTestDriver(final int size) throws IllegalArgumentException {
    super(size);
    cyclicBarrier = new CyclicBarrier(size);
  }

  /**
   * Optional method that is invoked before waiting for all threads to be ready. If this method fails, the test is
   * aborted and the {@link #doStartTogether(int)} is never invoked.
   *
   * @throws Throwable
   *           if an error occurs
   */
  protected void doBeforeWait(final int index) throws Throwable {}

  @Override
  protected void doRunInThread(final int index) throws Throwable {
    /* Execute the do doBeforeWait(index) method capture any errors */
    Throwable error = null;
    try {
      doBeforeWait(index);
    } catch (final Throwable e) {
      error = e;
    }

    /* Wait for the other threads to read this point */
    cyclicBarrier.await();

    /* If this thread did not fail, then execute the doStartTogether(index) method */
    if (error != null) {
      throw error;
    }
    doStartTogether(index);
  }

  /**
   * This method is invoked by all threads at the same time and is ideal to simulate race conditions (as best as it can
   * be done).
   *
   * @param index
   *          the order number which is between 0 (inclusive) and the {@code size} (exclusive). The {@code size} is
   *          defined by the constructor {@link #StartTogetherConcurrentTestDriver(int)}
   * @throws Throwable
   *           if an error occurs while testing
   */
  protected abstract void doStartTogether(int index) throws Throwable;
}
