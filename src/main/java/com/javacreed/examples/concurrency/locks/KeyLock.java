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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import net.jcip.annotations.GuardedBy;

/**
 * Provides locks based on a key. Only one thread is allowed to work on a given key at a given point in time. A key that
 * is being used is referred to as an active key and the lock maintains a set of active keys. This is ideal when working
 * with data and need to synchronise based on the object ids rather than the service. Say that the service is stateless
 * and can work with multiple threads at a given point in time, but you do not want to process the different objects
 * with the same id/key at the same time using a second thread. One way to do this is to serialise the service. This
 * will hinder the performance as the service will not be allowed to work on different (non-related) objects. This lock
 * solves this problem by blocking only if the key is already processed.
 * <p>
 * Consider the following example
 *
 * <pre>
 *   final KeyLock<Integer> lock = ...;
 *   final int key = 7;
 *   try (KeyLockGrant grant = lock.grant(key)) {
 *     // do something with the object
 *   }
 * </pre>
 *
 * The lock will only proceed if, and only if, key value {@code 7} is not already locked by another thread. If that is
 * the case (locked by another thread), then the {@link #grant(Object)} method will block until the grant is released.
 * This guarantees that one key is only used by one thread at a given point in time.
 * <p>
 * Note that this lock is reentrant, that is, the same thread can obtain a grant for the same key as many times it
 * needs. It is imperative that each obtained grant is closed so that the key is released when all grants are released.
 * Failing to do so will keep the key locked forever.
 *
 * @author Albert Attard
 *
 * @param <T>
 *          the object's key type
 */
public class KeyLock<T> {

  /**
   *
   * @author Albert Attard
   */
  private static class KeyLockHolder<T> {

    /** */
    private final Thread threadOwner;

    /** */
    private final KeyLock<T> keyLock;

    /** */
    private final T key;

    /** */
    private int counter;

    /**
     *
     */
    private final KeyLockGrant grant = new KeyLockGrant() {
      @Override
      public void close() {
        KeyLockHolder.this.release();
      }
    };

    /**
     *
     * @param lock
     * @param key
     * @throws NullPointerException
     */
    public KeyLockHolder(final KeyLock<T> lock, final T key) throws NullPointerException {
      this(lock, key, Thread.currentThread());
    }

    /**
     *
     * @param lock
     * @param key
     * @param threadOwner
     * @throws NullPointerException
     */
    public KeyLockHolder(final KeyLock<T> lock, final T key, final Thread threadOwner) throws NullPointerException {
      this.keyLock = Objects.requireNonNull(lock);
      this.key = key;
      this.threadOwner = Objects.requireNonNull(threadOwner);
      this.counter = 0;
    }

    /**
     *
     * @return
     */
    public int decrementAndGet() {
      return --counter;
    }

    /**
     *
     * @return
     */
    public Thread getOwner() {
      return threadOwner;
    }

    /**
     *
     * @return
     */
    public KeyLockGrant incrementAndGetLock() {
      counter++;
      return grant;
    }

    /**
     *
     * @throws IllegalStateException
     */
    public void release() throws IllegalStateException {
      keyLock.lock.lock();
      try {
        final KeyLockHolder<T> holder = keyLock.keysHolders.get(key);
        /* This should not be possible, but better check and fail */
        if (holder == null) {
          throw new IllegalStateException("The key " + key + " was not active");
        }

        /* This should not be possible, but better check and fail */
        if (holder != this) {
          throw new IllegalStateException("The key " + key + " was not held by this thread");
        }

        /**/
        final int count = decrementAndGet();
        if (count < 0) {
          throw new IllegalStateException("The key " + key + " was released more than expected");
        }

        /* Remove the key from the map once it reaches 0 (the thread owning it is done with it) */
        if (count == 0) {
          keyLock.keysHolders.remove(key);
          keyLock.changed.signalAll();
        }
      } finally {
        keyLock.lock.unlock();
      }
    }
  }

  /** The lock used to control access */
  private final ReentrantLock lock = new ReentrantLock();

  /** Used to notify that the active keys have changed */
  private final Condition changed = lock.newCondition();

  /** The set of active keys. This is used to control what keys are being currently used and what not. */
  @GuardedBy("lock")
  private final Map<T, KeyLockHolder<T>> keysHolders = new HashMap<>();

  /**
   *
   * @param key
   * @return
   * @throws IllegalStateException
   */
  private KeyLockGrant createLockHolder(final T key) throws IllegalStateException {
    if (false == lock.isHeldByCurrentThread()) {
      throw new IllegalStateException("Current thread does not hold the lock");
    }

    /**/
    final KeyLockHolder<T> keyLockHolder = new KeyLockHolder<>(this, key);

    /* Add this key to the set to prevent other threads from blocking/granting on this key */
    keysHolders.put(key, keyLockHolder);

    /* Returns an instance of the grant which removed this key from the active set once ready */
    return keyLockHolder.incrementAndGetLock();
  }

  /**
   * Returns a new set of the current active keys. Note that a new object is returned every time this method is invoked.
   * Modifying the returned object will not affect the lock's active keys. Furthermore, any updates to the lock's active
   * keys will not be reflected in the returned set.
   *
   * @return a new set of the current active keys
   */
  public Set<T> getActiveKeys() {
    return new HashSet<>(keysHolders.keySet());
  }

  /**
   * Acquires a lock on the given key, blocks if the given key is already active until this key is released. The
   * returned grant needs to be closed once ready so that the given key is released and removed from the set of active
   * keys.
   * <p>
   * It is imperative to close the returned grant as shown next as otherwise no other threads will be able to work with
   * this key.
   *
   * <pre>
   *   final KeyLock<Integer> lock = ...;
   *   final int key = 7;
   *   try (KeyLockGrant grant = lock.grant(key)) {
   *     // do something with the object
   *   }
   * </pre>
   *
   * @param key
   *          the key (which can be {@code null})
   * @return the grant for the given key
   * @throws InterruptedException
   *           if interrupted while waiting to the active key to be released
   */
  public KeyLockGrant grant(final T key) throws InterruptedException {
    lock.lock();
    try {
      /* Keep waiting until the given key is removed from the set (not in set anymore) */
      for (KeyLockHolder<T> keyLockHolder; (keyLockHolder = keysHolders.get(key)) != null;) {
        /* Deal with reentrant locks */
        if (Thread.currentThread() == keyLockHolder.getOwner()) {
          return keyLockHolder.incrementAndGetLock();
        }

        changed.await();
      }

      return createLockHolder(key);
    } finally {
      lock.unlock();
    }
  }

  /**
   *
   * @param key
   * @param time
   * @param timeUnit
   * @return
   * @throws InterruptedException
   * @throws TimeoutException
   * @throws IllegalArgumentException
   */
  public KeyLockGrant grant(final T key, final long time, final TimeUnit timeUnit)
      throws InterruptedException, TimeoutException, IllegalArgumentException {
    if (time < 0) {
      throw new IllegalArgumentException("The wait time cannot be less than 0");
    }
    Objects.requireNonNull(timeUnit, "The time unit cannot be null");

    lock.lock();

    /* */
    long waitInNanos = timeUnit.toNanos(time);

    try {
      /* Keep waiting until the given key is removed from the set (not in set anymore) */
      for (KeyLockHolder<T> keyLockHolder; (keyLockHolder = keysHolders.get(key)) != null;) {
        /* Deal with reentrant locks */
        if (Thread.currentThread() == keyLockHolder.getOwner()) {
          return keyLockHolder.incrementAndGetLock();
        }

        waitInNanos = changed.awaitNanos(waitInNanos);
        if (waitInNanos <= 0) {
          throw new TimeoutException("Failed to acquire lock in time");
        }
      }

      return createLockHolder(key);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Returns {@code true} if the given key is currently action, {@code false} otherwise.
   *
   * @param key
   *          the key to be verified (which may be {@code null})
   * @return {@code true} if the given key is currently action, {@code false} otherwise
   */
  public boolean isActiveKey(final T key) {
    lock.lock();
    try {
      return keysHolders.containsKey(key);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Returns {@code true} if no active keys are found, {@code false} otherwise
   *
   * @return {@code true} if no active keys are found, {@code false} otherwise
   *
   */
  public boolean isEmpty() {
    lock.lock();
    try {
      return keysHolders.isEmpty();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Returns the number of active keys which value will always be positive
   *
   * @return the number of active keys which value will always be positive
   */
  public int size() {
    lock.lock();
    try {
      return keysHolders.size();
    } finally {
      lock.unlock();
    }
  }

  /**
   *
   * @param key
   * @return
   */
  public KeyLockGrant tryGrant(final T key) {
    lock.lock();
    try {
      if (keysHolders.containsKey(key)) {
        return null;
      }

      return createLockHolder(key);
    } finally {
      lock.unlock();
    }
  }
}
