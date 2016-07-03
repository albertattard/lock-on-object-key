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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines a basic algorithm that can execute tasks within different threads.
 *
 * @author Albert Attard
 *
 */
public abstract class BasicConcurrentTestDriver {

  private static final Logger LOGGER = LoggerFactory.getLogger(BasicConcurrentTestDriver.class);

  /** The number of threads to be used */
  private final int size;

  /** Ensures that the method {@link #runTest()} is executed only once */
  private final Fuse fuse = new Fuse();

  /**
   * Creates an instance of this concurrent driver test class and defines the number of threads to be used.
   *
   * @param size
   *          the number of threads (which must be greater than 0)
   * @throws IllegalArgumentException
   *           if the given {@code size} is less than 1
   */
  public BasicConcurrentTestDriver(final int size) throws IllegalArgumentException {
    if (size < 1) {
      throw new IllegalArgumentException("The number of threads cannot be less than 1");
    }

    this.size = size;
  }

  /**
   * This method is invoked by each thread created by {@link #runTest()} method.
   *
   * @param index
   *          the order number which is between 0 (inclusive) and the {@link #size} (exclusive). The {@link #size} is
   *          defined by the constructor {@link #BasicConcurrentTestDriver(int)}
   * @throws Throwable
   *           if an error occurs
   * @see #runTest()
   */
  protected abstract void doRunInThread(int index) throws Throwable;

  public int getSize() {
    return size;
  }

  /**
   * Executes the {@link #doRunInThread(int)} method from {@link #size} different threads. This method is intended to be
   * executed only once.
   *
   * @throws Throwable
   *           if an error occurs while executing any of the threads
   *
   * @see #doRunInThread(int)
   */
  public final void runTest() throws Throwable {
    fuse.assertAndBurn("The runTest() method can only be invoked once (per instance)");

    /* Will contain the first error thrown from which any of the threads */
    final AtomicReference<Throwable> exception = new AtomicReference<>();

    /* Create the threads */
    final List<Thread> threads = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      final int index = i;
      final Thread thread = new Thread(new Runnable() {
        @Override
        public void run() {
          try {
            doRunInThread(index);
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            exception.compareAndSet(null, e);
          } catch (final Throwable e) {
            exception.compareAndSet(null, e);
          }
        }
      }, "TEST-THREAD-" + i);
      thread.start();
      threads.add(thread);
    }

    /* Wait for all threads to finish */
    for (final Thread thread : threads) {
      /*
       * There is a mysteries issue with this class, where a thread (the first thread) seems to be interrupted while
       * waiting for it to stop (through the join() method). Invoking the thread's getName() method sorts this issue
       * out.
       */
      BasicConcurrentTestDriver.LOGGER.trace("Waiting for thread {} to die", thread.getName());
      thread.join();
    }

    /* Fail if an error occurred */
    if (exception.get() != null) {
      throw exception.get();
    }
  }
}
