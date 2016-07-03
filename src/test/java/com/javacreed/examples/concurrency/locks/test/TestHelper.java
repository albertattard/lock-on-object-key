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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.javacreed.examples.concurrency.locks.KeyLock;
import com.javacreed.examples.concurrency.locks.KeyLockGrant;

/**
 *
 * @author Albert Attard
 *
 * @param <T>
 */
public class TestHelper<T> {

  /** */
  private static final Logger LOGGER = LoggerFactory.getLogger(TestHelper.class);

  /** */
  private final AtomicInteger maxSize = new AtomicInteger();

  /** */
  private final Set<T> usedKeys = new HashSet<>();

  /**
   *
   * @return
   */
  public int getMaxSize() {
    return maxSize.intValue();
  }

  /**
   *
   * @param lock
   * @param key
   * @param delay
   * @throws InterruptedException
   */
  public void test(final KeyLock<T> lock, final T key, final long delay) throws InterruptedException {
    TestHelper.LOGGER.debug("Testing lock with key {}", key);
    try (KeyLockGrant gant = lock.grant(key)) {
      TestHelper.LOGGER.debug("Grant obtained for key.  Verify uniqueness");
      synchronized (usedKeys) {
        if (false == usedKeys.add(key)) {
          Assert.fail("Key is already used");
        }
      }

      TestHelper.LOGGER.debug("Sleeping for {} milliseconds (simulating work)", delay);
      Thread.sleep(delay);

      TestHelper.LOGGER.debug("Calculating the number of active keys");
      synchronized (usedKeys) {
        final int size = usedKeys.size();
        if (maxSize.intValue() < size) {
          maxSize.set(size);
        }

        if (false == usedKeys.remove(key)) {
          Assert.fail("Key was already removed");
        }
      }
    }
    TestHelper.LOGGER.debug("Ready");
  }
}
