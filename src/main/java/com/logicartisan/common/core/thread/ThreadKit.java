/*
 * Copyright(c) 2002-2010, Rob Eden
 * All rights reserved.
 */
package com.logicartisan.common.core.thread;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Supplier;


/**
 * Useful static functions for dealing with threads.
 */
public class ThreadKit {
	private ThreadKit() {}



	/**
	 * @return      True if the sleep completed successful or false if the thread was
	 *              interrupted.
	 */
	public static boolean sleep( long time_ms ) {
		return sleep( time_ms, TimeUnit.MILLISECONDS );
	}

	/**
	 * @return      True if the sleep completed successful or false if the thread was
	 *              interrupted.
	 */
	public static boolean sleep( long time, @Nonnull TimeUnit unit ) {
		try {
			long millis = unit.toMillis( time );
			Thread.sleep( millis );
			return true;
		}
		catch ( InterruptedException ex ) {
			return false;
		}
	}



	/**
	 * Perform the given action in a lock.
	 */
	public static void inLock( @Nonnull Lock lock, @Nonnull Runnable action ) {
		lock.lock();
		try {
			action.run();
		}
		finally {
			lock.unlock();
		}
	}

	/**
	 * Perform the given action in a lock and return the result.
	 */
	public static <T> T inLock( @Nonnull Lock lock, @Nonnull Supplier<T> supplier ) {
		lock.lock();
		try {
			return supplier.get();
		}
		finally {
			lock.unlock();
		}
	}



	/**
	 * Perform the given action in a read lock.
	 */
	public static void inReadLock( @Nonnull ReadWriteLock lock,
		@Nonnull Runnable action ) {

		inLock( lock.readLock(), action );
	}

	/**
	 * Perform the given action in a read lock and return the result.
	 */
	public static <T> T inReadLock( @Nonnull ReadWriteLock lock,
		@Nonnull Supplier<T> supplier ) {

		return inLock( lock.readLock(), supplier );
	}


	/**
	 * Perform the given action in a write lock.
	 */
	public static void inWriteLock( @Nonnull ReadWriteLock lock,
		@Nonnull Runnable action ) {

		inLock( lock.writeLock(), action );
	}

	/**
	 * Perform the given action in a write lock and return the result.
	 */
	public static <T> T inWriteLock( @Nonnull ReadWriteLock lock,
		@Nonnull Supplier<T> supplier ) {

		return inLock( lock.writeLock(), supplier );
	}
}
