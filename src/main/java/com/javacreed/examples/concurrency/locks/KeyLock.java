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
import net.jcip.annotations.NotThreadSafe;

/**
 * Provides locks based on a key. Only one thread is allowed to work on a given key at a given point in time. A key that
 * is being used is referred to as an active key and the lock maintains a set of active keys. This is ideal when working
 * with data and need to synchronise based on the object ids rather than the service. Say that the service is stateless
 * and can work with multiple threads at a given point in time, but you do not want to process the different objects
 * with the same id/key at the same time using a second thread. One way to do this is to serialise the service. This
 * will hinder the performance as the service will not be allowed to work on different (non-related) objects. This lock
 * addresses this problem by blocking only if the key is already processed.
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
   * An internal class used to hold information about the locks and how many times a thread hold the same lock. This
   * object is statefull and needs to be accessed and modified under the protection of locks (an that's why it is marked
   * as {@link NotThreadSafe}).
   *
   * @author Albert Attard
   */
  @NotThreadSafe
  private static class KeyLockHolder<T> {

    /** The thread holding this lock */
    private final Thread threadOwner;

    /**
     * An instance of the parent class and is used to manage the set of active keys for the thread associated with this
     * thread.
     */
    private final KeyLock<T> keyLock;

    /** The key locked by this thread */
    private final T key;

    /**
     * The number of times the thread locked the key. This is used to monitor the number of times a thread locked the
     * same key and only release the key once the thread releases this key the same number of times it locked it.
     */
    private int counter;

    /**
     * The grant associated with this thread and key. When the grant is closed, it decrements the counter and when it
     * reaches 0, it releases the lock on the key.
     */
    private final KeyLockGrant grant = new KeyLockGrant() {
      @Override
      public void close() {
        KeyLockHolder.this.release();
      }
    };

    /**
     * Creates an instance of this class for the current thread
     *
     * @param lock
     *          the parent class (which cannot be {@code null})
     * @param key
     *          the key that this thread has acquired (which can be {@code null})
     * @throws NullPointerException
     *           if the given lock thread are {@code null}
     */
    public KeyLockHolder(final KeyLock<T> lock, final T key) throws NullPointerException {
      this(lock, key, Thread.currentThread());
    }

    /**
     * Creates an instance of this class
     *
     * @param lock
     *          the parent class (which cannot be {@code null})
     * @param key
     *          the key that the given thread has acquired (which can be {@code null})
     * @param threadOwner
     *          the thread holding this key (which cannot be {@code null})
     * @throws NullPointerException
     *           if the given lock or owner thread are {@code null}
     */
    public KeyLockHolder(final KeyLock<T> lock, final T key, final Thread threadOwner) throws NullPointerException {
      this.keyLock = Objects.requireNonNull(lock);
      this.key = key;
      this.threadOwner = Objects.requireNonNull(threadOwner);
      this.counter = 0;
    }

    /**
     * Decrements the counter and returns the new, decremented value. For example, if the current counter value is 7,
     * when invoking this method, the counter value is decremented and the value of 6 is returned.
     *
     * @return the new decremented value
     */
    private int decrementAndGet() {
      return --counter;
    }

    /**
     * Returns the thread owning the lock, which value is not {@code null}
     *
     * @return the thread owning the lock
     */
    public Thread getOwner() {
      return threadOwner;
    }

    /**
     * Increments the counter and returns the grant related to this lock.
     *
     * @return grant related to this lock
     */
    public KeyLockGrant incrementAndGetGrant() {
      counter++;
      return grant;
    }

    /**
     * Releases the key. If the threads hold multiple instance of this lock, then the counter is decremented. If the
     * decremented counter reaches 0, the key associated with this lock is released.
     * <p>
     * An {@code IllegalActionKeyLockException} is thrown if the thread releases an inactive grant. Say that a thread
     * obtained a lock and released it. Then it tries to release it again. This can cause several problems and thus an
     * {@code IllegalActionKeyLockException} is thrown
     *
     * @throws IllegalActionKeyLockException
     *           if the thread releases an inactive grant
     */
    public void release() throws IllegalActionKeyLockException {
      keyLock.lock.lock();
      try {
        final KeyLockHolder<T> holder = keyLock.keysHolders.get(key);
        /* The key was already removed by the lock and not in the set of active keys */
        if (holder == null) {
          throw new IllegalActionKeyLockException("The key " + key + " was not active");
        }

        /* The thread has released the lock and now it tries to release the lock hold by another thread */
        if (holder != this) {
          throw new IllegalActionKeyLockException("The key " + key + " was not held by this thread");
        }

        /*
         * The same thread is trying to release the lock more than it acquired it. This should not be the case as the
         * code should prevent it from happening as once the count reaches 0, this is removed from the set of active
         * keys.
         */
        final int count = decrementAndGet();
        if (count < 0) {
          throw new IllegalActionKeyLockException("The key " + key + " was released more than expected");
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
   * Creates the lock handler for the given key and the current thread and returns the associated grant.
   *
   * @param key
   *          the key that this thread has acquired (which can be {@code null})
   * @return the grant obtained by the lock
   * @throws IllegalStateException
   */
  private KeyLockGrant createLockHolder(final T key) throws IllegalActionKeyLockException {
    /* Verify that the lock is held by the current thread */
    if (false == lock.isHeldByCurrentThread()) {
      throw new IllegalActionKeyLockException("Current thread does not hold the lock");
    }

    /* Add this key to the set to prevent other threads from blocking/granting on this key */
    final KeyLockHolder<T> keyLockHolder = new KeyLockHolder<>(this, key);
    keysHolders.put(key, keyLockHolder);

    /* Returns an instance of the grant which removed this key from the active set once ready */
    return keyLockHolder.incrementAndGetGrant();
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
          return keyLockHolder.incrementAndGetGrant();
        }

        changed.await();
      }

      return createLockHolder(key);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Tries to acquire a lock on the given key. If the lock is not obtained within the given time, then a
   * {@link TimeoutException} is thrown.
   *
   * @param key
   *          the key (which can be {@code null})
   * @param time
   *          the time (which needs to be positive, not less than 0)
   * @param timeUnit
   *          the time unit (which cannot be {@code null})
   * @return the grant for the given key
   * @throws InterruptedException
   *           If interrupted while waiting to obtain the lock on the given key
   * @throws TimeoutException
   *           if the lock is not acquired within the given time
   * @throws IllegalArgumentException
   *           if the given time is less than 0
   */
  public KeyLockGrant grant(final T key, final long time, final TimeUnit timeUnit)
      throws InterruptedException, TimeoutException, IllegalArgumentException {
    if (time < 0) {
      throw new IllegalArgumentException("The wait time cannot be less than 0");
    }
    Objects.requireNonNull(timeUnit, "The time unit cannot be null");

    /* The remaining wait time in nano seconds */
    long waitInNanos = timeUnit.toNanos(time);

    lock.lock();
    try {
      /* Keep waiting until the given key is removed from the set (not in set anymore) */
      for (KeyLockHolder<T> keyLockHolder; (keyLockHolder = keysHolders.get(key)) != null;) {
        /* Deal with reentrant locks */
        if (Thread.currentThread() == keyLockHolder.getOwner()) {
          return keyLockHolder.incrementAndGetGrant();
        }

        /* Wait for the signal. If the time elapsed is longer than the allowed timeout */
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
   * Returns the grant if the given key is free (not already locked by another thread) or held by the same thread,
   * otherwise {@code null}. Different from the other grant methods, this method does not wait and may return
   * {@code null}.
   *
   * @param key
   *          the key (which can be {@code null})
   * @return the grant if the given key is free (not already locked by another thread) or held by the same thread,
   *         otherwise {@code null}
   */
  public KeyLockGrant tryGrant(final T key) {
    lock.lock();
    try {
      final KeyLockHolder<T> keyLockHolder = keysHolders.get(key);
      if (keyLockHolder != null) {
        /* Deal with reentrant locks */
        if (Thread.currentThread() == keyLockHolder.getOwner()) {
          return keyLockHolder.incrementAndGetGrant();
        }

        /* The lock is already acquired by another thread, thus cannot be acquired by this thread. */
        return null;
      }

      return createLockHolder(key);
    } finally {
      lock.unlock();
    }
  }
}
