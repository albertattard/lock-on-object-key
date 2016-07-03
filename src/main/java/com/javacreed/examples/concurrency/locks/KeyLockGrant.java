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

/**
 * The grant returned by the {@link KeyLock#grant(Object)} method after the thread is granted access. It is imperative
 * to close the grant when done as otherwise the key to which is bound will stay locked forever.
 *
 * @author Albert Attard
 */
public abstract class KeyLockGrant implements AutoCloseable {

  /**
   * Release the key
   */
  @Override
  public abstract void close();
}
