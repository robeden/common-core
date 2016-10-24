/*
 * Copyright(c) 2002-2016, Rob Eden
 * All rights reserved.
 */
package com.logicartisan.common.core.thread;


import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;


/**
 * An ObjectSlot is similar to {@link java.util.concurrent.atomic.AtomicReference} but
 * also allows addition features such as waiting for particular values.
 * <p></p>
 * WARNING: These features come at a performance price as this is a heavier-weight
 * implementation than its atomic counterpart both in terms of performance (a Lock is
 * used internally) and memory requirements (due to additional internal fields).
 */
@SuppressWarnings( "WeakerAccess" )
public class ObjectSlot<V> implements Supplier<V>, Consumer<V> {
	private final ReentrantLock lock = new ReentrantLock();
	private final Condition new_value_condition = lock.newCondition();

	private List<ConsumerFuture<V>> single_value_consumers;
	private List<ConsumerFuture<Optional<V>>> persistent_value_consumers;

	private volatile V value;


	/**
	 * Create with a null initial value.
	 */
	public ObjectSlot() {
		this( null );
	}

	/**
	 * Create with an initial value.
	 */
	public ObjectSlot( V value ) {
		this.value = value;
	}


	@Override
	public void accept( V value ) {
		set( value );
	}

	/**
	 * See {@link java.util.concurrent.atomic.AtomicReference#compareAndSet}.
	 */
	public boolean compareAndSet( V expect, V update ) {
		lock.lock();
		try {
			if ( !Objects.equals( value, expect ) ) return false;

			getAndSet_noLock( update );
			return true;
		}
		finally {
			lock.unlock();
		}
	}

	/**
	 * Similar to {@link #compareAndSet(Object, Object)}, but allows more advanced testing of the
	 * current value via a predicate.
	 */
	public boolean compareAndSet( @Nonnull Predicate<V> update_allowed_test, V update ) {
		lock.lock();
		try {
			if ( !update_allowed_test.test( value ) ) return false;

			getAndSet_noLock( update );
			return true;
		}
		finally {
			lock.unlock();
		}
	}

	/**
	 * See {@link java.util.concurrent.atomic.AtomicReference#get}.
	 */
	@Override
	public @Nullable V get() {
		lock.lock();
		try {
			return value;
		}
		finally {
			lock.unlock();
		}
	}

	/**
	 * See {@link java.util.concurrent.atomic.AtomicReference#getAndSet}.
	 */
	public @Nullable V getAndSet( @Nullable V new_value ) {
		lock.lock();
		try {
			return getAndSet_noLock( new_value );
		}
		finally {
			lock.unlock();
		}
	}

	/**
	 * See {@link java.util.concurrent.atomic.AtomicReference#set}.
	 */
	public void set( @Nullable V new_value ) {
		lock.lock();
		try {
			getAndSet_noLock( new_value );
		}
		finally {
			lock.unlock();
		}
	}


	/**
	 * Equivalent to <tt>set(null)</tt>.
	 */
	public void clear() {
		set( null );
	}

	/**
	 * Wait forever for any non-null value to be set. If a non-null value is currently set,
	 * this will return immediately.
	 *
	 * @return the value that was set or null if time expired
	 */
	public V waitForValue() throws InterruptedException {
		lock.lockInterruptibly();
		try {
			if ( value != null ) return value;

			while ( value == null ) {
				new_value_condition.await();
			}

			return value;
		}
		finally {
			lock.unlock();
		}
	}


	/**
	 * Wait for any non-null value to be set. If a non-null value is currently set, this
	 * will return immediately.
	 *
	 * @param timeout_ms The time (in milliseconds) to wait.
	 * @return the value that was set or null if time expired
	 */
	public V waitForValue( long timeout_ms ) throws InterruptedException {
		lock.lockInterruptibly();
		try {
			if ( value != null ) return value;

			long wait_remaining = timeout_ms;
			while ( value == null && wait_remaining > 0 ) {
				long start = System.currentTimeMillis();
				boolean have_new_value =
					new_value_condition.await( wait_remaining, TimeUnit.MILLISECONDS );
				wait_remaining -= ( System.currentTimeMillis() - start );

				if ( !have_new_value ) return null;
			}

			return value;
		}
		finally {
			lock.unlock();
		}
	}


	/**
	 * Wait for a particular value to be set. If the value is already set, this will return
	 * immediately.
	 *
	 * @param value      The value to wait for.
	 * @param timeout_ms The time (in milliseconds) to wait.
	 * @return True if the value was set or falue if the time expired.
	 */
	public boolean waitForValue( V value, long timeout_ms )
		throws InterruptedException {

		lock.lockInterruptibly();
		try {
			long wait_remaining = timeout_ms;
			while ( !Objects.equals( this.value, value ) && wait_remaining > 0 ) {
				long start = System.currentTimeMillis();
				boolean have_new_value =
					new_value_condition.await( wait_remaining, TimeUnit.MILLISECONDS );
				wait_remaining -= ( System.currentTimeMillis() - start );

				if ( !have_new_value ) return false;
			}

			return Objects.equals( this.value, value );
		}
		finally {
			lock.unlock();
		}
	}


	/**
	 * Wait for <strong>anything but</strong> a particular value to be set. If something
	 * else is already set, this will return immediately.
	 *
	 * @param value      The value to wait for.
	 * @param timeout_ms The time (in milliseconds) to wait.
	 * @return The new value, or <tt>value</tt> (object equality may be used for
	 *         comparison) if the timeout was reached.
	 */
	public V waitForDifferentValue( V value, long timeout_ms )
		throws InterruptedException {

		lock.lockInterruptibly();
		try {
			long wait_remaining = timeout_ms;
			while ( Objects.equals( this.value, value ) && wait_remaining > 0 ) {
				long start = System.currentTimeMillis();
				boolean have_new_value =
					new_value_condition.await( wait_remaining, TimeUnit.MILLISECONDS );
				wait_remaining -= ( System.currentTimeMillis() - start );

				if ( !have_new_value ) return value;
			}

			V new_value = this.value;
			if ( Objects.equals( new_value, value ) ) return value;
			else return new_value;
		}
		finally {
			lock.unlock();
		}
	}



	/**
	 * Provide a consumer that will accept a non-null value when set. If the value is
	 * currently non-null, it will be immediately consumed. Otherwise, the consumer will
	 * be called whenever a non-null value is set.
	 *
	 * @param value_consumer        Consumer that will process the value.
	 *
	 * @return                      A future that allows cancelling the consumer, checking
	 *                              the status of the operation and obtaining the value
	 *                              once set.
	 *
	 * @see #consumeAllValues(Consumer)
	 */
	public Future<V> consumeValue( @Nonnull Consumer<V> value_consumer ) {
		lock.lock();
		try {
			return handleValueConsumer( value_consumer,
				() -> {
					assert lock.isHeldByCurrentThread();

					if ( single_value_consumers == null ) {
						single_value_consumers = new ArrayList<>();
					}
					return single_value_consumers;
				},
				value,
				false,          // NOT persistent
				lock,
				( future ) -> {
					assert lock.isHeldByCurrentThread();

					if ( single_value_consumers == null ) return false;

					boolean removed = single_value_consumers.remove( future );
					if ( single_value_consumers.isEmpty() ) {
						single_value_consumers = null;
					}
					return removed;
				} );
		}
		finally {
			lock.unlock();
		}
	}


	/**
	 * Registers a consumer that will be passed all values set in the slot. When
	 * added, it will immediately be called with the current value and then all
	 * subsequent values until it is removed via cancellation of the future.
	 *
	 * @return      A Future whose value will indicate the last value set and allow
	 *              removal of the consumer.
	 *
	 * @see #consumeValue(Consumer)
	 */
	public @Nonnull Future<Optional<V>> consumeAllValues(
		@Nonnull Consumer<Optional<V>> value_consumer ) {

		lock.lock();
		try {
			return handleValueConsumer( value_consumer,
				() -> {
					if ( persistent_value_consumers == null ) {
						persistent_value_consumers = new ArrayList<>();
					}
					return persistent_value_consumers;
				},
				Optional.ofNullable( value ),
				true,           // persistent
				lock,
				( future ) -> {
					assert lock.isHeldByCurrentThread();

					if ( persistent_value_consumers == null ) return false;

					boolean removed = persistent_value_consumers.remove( future );
					if ( persistent_value_consumers.isEmpty() ) {
						persistent_value_consumers = null;
					}
					return removed;
				} );
		}
		finally {
			lock.unlock();
		}
	}


	private static <T> Future<T> handleValueConsumer( @Nonnull Consumer<T> consumer,
		@Nonnull Supplier<List<ConsumerFuture<T>>> list_supplier,
		@Nullable T current_value, boolean persistent, @Nonnull Lock lock,
		@Nonnull Predicate<ConsumerFuture<T>> cancel_handler ) {

		requireNonNull( consumer );
		requireNonNull( list_supplier );

		if ( !persistent && current_value != null ) {
			consumer.accept( current_value );
			return new ImmediateFuture<>( current_value );
		}

		List<ConsumerFuture<T>> list = list_supplier.get();

		ConsumerFuture<T> future = new ConsumerFuture<>( consumer, lock, cancel_handler );
		list.add( future );

		if ( persistent ) {
			// Will always be non-null
			requireNonNull( current_value );

			future.setValue( current_value );
		}

		return future;
	}



	@Override
	public String toString() {
		return String.valueOf( get() );
	}



	private V getAndSet_noLock( @Nullable V new_value ) {
		assert lock.isHeldByCurrentThread();

		V old_value = value;

		value = new_value;
		new_value_condition.signalAll();

		processConsumers( new_value );

		return old_value;
	}


	private void processConsumers( @Nullable V value ) {
		assert lock.isHeldByCurrentThread();

		// Single value consumers don't accept null and are removed after processing
		if ( value != null && single_value_consumers != null ) {
			for( int i = single_value_consumers.size() - 1; i >= 0; i-- ) {
				ConsumerFuture<V> future = single_value_consumers.remove( i );
				future.setValue( value );
			}
		}

		// Persistent consumers DO take null (w/ Optional) and stick around
		if ( persistent_value_consumers != null ) {
			for( ConsumerFuture<Optional<V>> future : persistent_value_consumers ) {
				future.setValue( Optional.ofNullable( value ) );
			}
		}
	}


	private static class ImmediateFuture<V> implements Future<V> {
		private final V value;

		ImmediateFuture( V value ) {
			this.value = value;
		}

		@Override
		public boolean cancel( boolean mayInterruptIfRunning ) {
			return false;
		}

		@Override
		public boolean isCancelled() {
			return false;
		}

		@Override
		public boolean isDone() {
			return true;
		}

		@Override
		public V get() {
			return value;
		}

		@Override
		public V get( long timeout, @Nonnull TimeUnit unit ) {
			return value;
		}
	}

	private static class ConsumerFuture<FV> implements Future<FV> {
		private final Consumer<FV> consumer;
		private final Lock lock;
		private final Predicate<ConsumerFuture<FV>> cancel_handler;

		private final ObjectSlot<FV> inner_slot = new ObjectSlot<>();

		private volatile boolean cancelled = false;


		ConsumerFuture( @Nonnull Consumer<FV> consumer, @Nonnull Lock lock,
			@Nonnull Predicate<ConsumerFuture<FV>> cancel_handler ) {

			this.consumer = requireNonNull( consumer );
			this.lock = requireNonNull( lock );
			this.cancel_handler = requireNonNull( cancel_handler );
		}


		void setValue( @Nonnull FV value ) {
			inner_slot.set( value );
			consumer.accept( value );
		}


		@Override
		public boolean cancel( boolean mayInterruptIfRunning ) {
			lock.lock();
			try {
				cancelled = true;
				return cancel_handler.test( ConsumerFuture.this );
			}
			finally {
				lock.unlock();
			}
		}

		@Override
		public boolean isCancelled() {
			return cancelled;
		}

		@Override
		public boolean isDone() {
			return inner_slot.get() != null;
		}

		@Override
		public FV get() throws InterruptedException, ExecutionException {
			return inner_slot.waitForValue();
		}

		@Override
		public FV get( long timeout, @Nonnull TimeUnit unit )
			throws InterruptedException, ExecutionException, TimeoutException {

			return inner_slot.waitForValue( unit.toMillis( timeout ) );
		}
	}
}