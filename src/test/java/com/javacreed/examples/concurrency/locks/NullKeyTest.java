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
package com.javacreed.examples.concurrency.locks;

import org.junit.Assert;
import org.junit.Test;

import com.javacreed.examples.concurrency.locks.test.StartTogetherConcurrentTestDriver;
import com.javacreed.examples.concurrency.locks.test.TestHelper;

/**
 *
 * @author Albert Attard
 */
public class NullKeyTest {

  /**
   *
   * @throws Throwable
   */
  @Test(timeout = 10000)
  public void test() throws Throwable {
    final KeyLock<Integer> lock = new KeyLock<>();
    Assert.assertTrue(lock.isEmpty());
    Assert.assertEquals(0, lock.size());

    final TestHelper<Integer> helper = new TestHelper<>();

    final StartTogetherConcurrentTestDriver driver = new StartTogetherConcurrentTestDriver(5) {
      @Override
      protected void doStartTogether(final int index) throws Throwable {
        helper.test(lock, null, 1000);
      }
    };
    driver.runTest();

    /*
     * This only works with low numbers, because with large numbers grants start to be released before all grants are
     * obtained.
     */
    Assert.assertEquals(1, helper.getMaxSize());

    /* There should be no grants pending at the end of the test */
    Assert.assertTrue(lock.isEmpty());
    Assert.assertEquals(0, lock.size());
  }
}
