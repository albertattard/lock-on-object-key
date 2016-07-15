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
 * Indicates that the action cannot be performed with given the current lock's state. For example if a thread obtains
 * the lock once and then close it twice, the second close will throw a {@link IllegalActionKeyLockException} exception.
 *
 * @author Albert Attard
 */
public class IllegalActionKeyLockException extends RuntimeException {

  private static final long serialVersionUID = 4896934727556167510L;

  /**
   * Creates an instance of this exception
   *
   * @param message
   *          the message (which can be {@code null})
   */
  public IllegalActionKeyLockException(final String message) {
    super(message);
  }
}
