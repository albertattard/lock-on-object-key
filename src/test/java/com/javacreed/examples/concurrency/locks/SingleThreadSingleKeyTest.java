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

/**
 *
 * @author Albert Attard
 */
public class SingleThreadSingleKeyTest {

  /**
   *
   * @throws Throwable
   */
  @Test(timeout = 10000)
  public void test() throws Throwable {
    final KeyLock<Integer> lock = new KeyLock<>();
    Assert.assertTrue(lock.isEmpty());
    Assert.assertEquals(0, lock.size());

    final KeyLockGrant grant = lock.grant(1);
    grant.close();

    /* There should be no grants pending at the end of the test */
    Assert.assertTrue(lock.isEmpty());
    Assert.assertEquals(0, lock.size());
  }
}
