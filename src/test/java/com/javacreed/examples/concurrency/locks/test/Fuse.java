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

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;

import net.jcip.annotations.ThreadSafe;

/**
 * Acts as a fuse for testing and ascertains that a fuse is only burned once. If the same fuse instance is burned twice,
 * the second attempt will fail with an assertion error.
 *
 * @author Albert Attard
 */
@ThreadSafe
public class Fuse {

  /** The fuse state (burned or not). */
  private final AtomicBoolean burned = new AtomicBoolean(false);

  /**
   * Asserts that the fuse is not yet burned and it burns it. An assertion error is thrown if the fuse is already
   * burned.
   *
   * @see #assertAndBurn(String)
   */
  public void assertAndBurn() {
    assertAndBurn("The fuse was already burned");
  }

  /**
   * Asserts that the fuse is not yet burned and it burns it. An assertion error is thrown if the fuse is already
   * burned.
   *
   * @param message
   *          the identifying message for the {@link AssertionError} (which can be {@code null})
   *
   * @see #assertAndBurn()
   */
  public void assertAndBurn(final String message) {
    if (false == burned.compareAndSet(false, true)) {
      Assert.fail(message);
    }
  }

  @Override
  public String toString() {
    return "Fuse " + (burned.get() ? "" : "not ") + "burned";
  }
}
