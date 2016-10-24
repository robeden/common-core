/*
 * Copyright(c) 2002-2010, Rob Eden
 * All rights reserved.
 */
package com.logicartisan.common.core.thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * A general-purpose thread pool for UI applications.
 */
public class SharedThreadPool implements Executor, ScheduledExecutor {
	private static final Logger LOG = LoggerFactory.getLogger( SharedThreadPool.class );

	private static final long THREAD_CACHE_TIME =
		Long.getLong( "starlight.sharedthreadpool.cache_time", 60000 ).longValue();

	public static final SharedThreadPool INSTANCE = new SharedThreadPool();

	// To handle scheduling but still use just a caching thread pool (i.e., unbounded
	// growth, etc.), a single thread pool is used for scheduling. That pool simply starts
	// a task in the main thread pool so they always return immediately.
	private final ScheduledThreadPoolExecutor schedule_executor;
	private final ExecutorService main_executor;


	// Hidden constructor

	private SharedThreadPool() {
		schedule_executor = new ScheduledThreadPoolExecutor( 1,
			new NamingThreadFactory( "SharedThreadPool Scheduler" ) );

		// No queue, no max size, cache for specified time
		//
		// This executor has two real customizations:
		//  1) The thread creator creates threads that are instances of NameStoringThread
		//  2) After execution is complete, the "restoreOriginalName" method is called
		//     on the NameStoringThread instances.
		// This allows tasks to change the name of their thread (for easier debugging)
		// and the name of the thread will automatically be reset when the test is
		// complete.
		main_executor = new ThreadPoolExecutor( 0, Integer.MAX_VALUE,
			THREAD_CACHE_TIME, TimeUnit.MILLISECONDS, new SynchronousQueue<>(),
			new NameStoringThreadNamingThreadFactory( "SharedThreadPool Worker-" ) ) {

			@Override
			protected void afterExecute( Runnable r, Throwable t ) {
				// Restore the name to the calling thread (which is the one that executed
				// the task.
				if ( Thread.currentThread() instanceof NameStoringThread ) {
					( ( NameStoringThread ) Thread.currentThread() ).restoreOriginalName();
				}

				if ( t != null ) {
					LOG.warn( "Execution of task ({}) threw uncaught exception", r, t );
				}

				super.afterExecute( r, t );
			}
		};
	}


	/**
	 * See {@link Executor#execute(Runnable)}.
	 */
	public void execute( @Nonnull Runnable command ) {
		LOG.debug( "Execute: {}", command );

		main_executor.submit( command );
	}

	/**
	 * See {@link ScheduledExecutorService#schedule(Callable, long, TimeUnit)}
	 */
	public <V> ScheduledFuture<V> schedule( Callable<V> callable, long delay,
		TimeUnit unit ) {

		if ( LOG.isDebugEnabled() ) {
			LOG.debug( "Schedule: {} at delay={} {}",
				callable, Long.valueOf( delay ), unit );
		}

		HandoffScheduledFuture<V> handoff_future = new HandoffScheduledFuture<>();
		Callable<V> wrapper =
			new HandoffCallable<>( callable, handoff_future, main_executor );

		ScheduledFuture<V> schedule_future =
			schedule_executor.schedule( wrapper, delay, unit );
		handoff_future.init( schedule_future );

		return handoff_future;
	}

	/**
	 * See {@link ScheduledExecutorService#schedule(Runnable, long, TimeUnit)}
	 */
	@SuppressWarnings( { "unchecked" } )
	public ScheduledFuture<?> schedule( Runnable command, long delay, TimeUnit unit ) {
		if ( LOG.isDebugEnabled() ) {
			LOG.debug( "Schedule: {} at delay={} {}",
				command, Long.valueOf( delay ), unit );
		}

		HandoffScheduledFuture<?> handoff_future = new HandoffScheduledFuture<Void>();
		Runnable wrapper = new HandoffRunnable( command, handoff_future, main_executor );

		ScheduledFuture schedule_future =
			schedule_executor.schedule( wrapper, delay, unit );
		handoff_future.init( schedule_future );

		return handoff_future;
	}

	/**
	 * See {@link ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)}
	 */
	@SuppressWarnings( { "unchecked" } )
	public ScheduledFuture<?> scheduleAtFixedRate( Runnable command, long initialDelay,
		long period, TimeUnit unit ) {

		if ( LOG.isDebugEnabled() ) {
			LOG.debug( "Schedule at fixed rate: {} at initialDelay={} period={} {}",
				command, Long.valueOf( initialDelay ),
				Long.valueOf( period ), unit );
		}

		HandoffScheduledFuture<?> handoff_future = new HandoffScheduledFuture<Void>();
		Runnable wrapper = new PreventConcurrentRunHandoffRunnable( command,
			handoff_future, main_executor );

		ScheduledFuture schedule_future =
			schedule_executor.scheduleAtFixedRate( wrapper, initialDelay, period, unit  );
		handoff_future.init( schedule_future );

		return handoff_future;
	}

	/**
	 * See {@link ScheduledExecutorService#scheduleWithFixedDelay(Runnable, long, long, TimeUnit)}
	 */
	@SuppressWarnings( { "unchecked" } )
	public ScheduledFuture<?> scheduleWithFixedDelay( Runnable command, long initialDelay,
		long delay, TimeUnit unit ) {

		if ( LOG.isDebugEnabled() ) {
			LOG.debug( "Schedule with fixed delay: {} at initialDelay={} delay={} {}",
				command, Long.valueOf( initialDelay ),
				Long.valueOf( delay ), unit );
		}

		// NOTE: since the scheduled execution service is disconnected from the actual
		//       running of the task, this method is a bit trickier to implement. When the
		//       task is finished running, it needs to be re-scheduled for one-time
		//       execution.

		RescheduleWrapperRunnable reschedule_wrapper =
			new RescheduleWrapperRunnable( command, delay, unit, schedule_executor );

		HandoffScheduledFuture<?> handoff_future = new HandoffScheduledFuture<Void>();
		HandoffRunnable wrapper = new HandoffRunnable( reschedule_wrapper, handoff_future,
			main_executor );
		reschedule_wrapper.initHandoffRunnable( wrapper, handoff_future );

		ScheduledFuture schedule_future =
			schedule_executor.schedule( wrapper, initialDelay, unit );
		handoff_future.init( schedule_future );

		return handoff_future;
	}


	private static class RescheduleWrapperRunnable implements Runnable {
		private final Runnable delegate;
		private final long delay;
		private final TimeUnit unit;
		private final ScheduledThreadPoolExecutor schedule_executor;

		private HandoffRunnable handoff_runnable;
		private HandoffScheduledFuture<?> handoff_future;

		RescheduleWrapperRunnable( Runnable delegate, long delay, TimeUnit unit,
			ScheduledThreadPoolExecutor schedule_executor ) {

			this.delegate = delegate;
			this.delay = delay;
			this.unit = unit;
			this.schedule_executor = schedule_executor;
		}

		void initHandoffRunnable( HandoffRunnable handoff_runnable,
			HandoffScheduledFuture<?> handoff_future ) {

			this.handoff_runnable = handoff_runnable;
			this.handoff_future = handoff_future;
		}

		@Override public void run() {
			delegate.run();

			// Don't reschedule if cancelled
			if ( handoff_future.isCancelled() ) return;

			ScheduledFuture future =
				schedule_executor.schedule( handoff_runnable, delay, unit );
			//noinspection unchecked
			handoff_future.init( future );
		}
	}


	private static class HandoffRunnable implements Runnable {
		protected final Runnable delegate;
		private final HandoffScheduledFuture<?> handoff_future;
		private final ExecutorService main_executor;

		HandoffRunnable( Runnable delegate, HandoffScheduledFuture<?> handoff_future,
			ExecutorService main_executor ) {

			this.delegate = delegate;
			this.handoff_future = handoff_future;
			this.main_executor = main_executor;
		}

		@SuppressWarnings( { "unchecked" } )
		@Override
		public void run() {
			Future inner_future = main_executor.submit( delegate );
			handoff_future.setDirectExecutionDelegate( inner_future );
		}
	}


	private static class HandoffCallable<V> implements Callable<V> {
		private final Callable<V> delegate;
		private final HandoffScheduledFuture<V> handoff_future;
		private final ExecutorService main_executor;

		HandoffCallable( Callable<V> delegate, HandoffScheduledFuture<V> handoff_future,
			ExecutorService main_executor ) {

			this.delegate = delegate;
			this.handoff_future = handoff_future;
			this.main_executor = main_executor;
		}

		@Override
		public V call() throws Exception {
			Future<V> inner_future = main_executor.submit( delegate );
			handoff_future.setDirectExecutionDelegate( inner_future );

			// Return immediately, this value doesn't matter
			return null;
		}
	}


	private static class HandoffScheduledFuture<V> implements ScheduledFuture<V> {
		private ScheduledFuture<V> scheduled_delegate;
		private ObjectSlot<Future<V>> direct_execution_delegate;

		HandoffScheduledFuture() {
			this.direct_execution_delegate = new ObjectSlot<>();
		}

		void init( ScheduledFuture<V> scheduled_delegate ) {
			ScheduledFuture<V> previous_delegate = this.scheduled_delegate;
			this.scheduled_delegate = scheduled_delegate;

			// Make sure the previous task wasn't already canceled.
			if ( previous_delegate != null && previous_delegate.isCancelled() ) {
				scheduled_delegate.cancel( false );
			}
		}

		void setDirectExecutionDelegate( Future<V> direct_execution_delegate ) {
			this.direct_execution_delegate.set( direct_execution_delegate );
		}


		@Override
		public long getDelay( @Nonnull TimeUnit unit ) {
			return scheduled_delegate.getDelay( unit );
		}

		@Override
		public int compareTo( @Nonnull Delayed o ) {
			return scheduled_delegate.compareTo( o );
		}

		@Override
		public boolean cancel( boolean mayInterruptIfRunning ) {
			boolean canceled = scheduled_delegate.cancel( mayInterruptIfRunning );

			Future<V> direct_future = direct_execution_delegate.get();
			if ( direct_future != null ) {
				canceled |= direct_future.cancel( mayInterruptIfRunning );
			}
			return canceled;
		}

		@Override
		public boolean isCancelled() {
			Future<V> direct_future = direct_execution_delegate.get();
			if ( direct_future == null ) return scheduled_delegate.isCancelled();
			else return direct_future.isCancelled();
		}

		@Override
		public boolean isDone() {
			Future<V> direct_future = direct_execution_delegate.get();
			return direct_future != null && direct_future.isDone();
		}

		@Override
		public V get() throws InterruptedException, ExecutionException {
			Future<V> direct_future = direct_execution_delegate.waitForValue();
			return direct_future.get();
		}

		@Override
		public V get( long timeout, @Nonnull TimeUnit unit )
			throws InterruptedException, ExecutionException, TimeoutException {

			long start = System.nanoTime();
			Future<V> direct_future =
				direct_execution_delegate.waitForValue( unit.toMillis( timeout  ) );

			long spent = Math.min( 0, System.nanoTime() - start );
			long remaining = unit.toNanos( timeout ) - spent;

			if ( remaining <= 0 ) return direct_future.get( 0, unit );
			else return direct_future.get( remaining, TimeUnit.NANOSECONDS );
		}
	}


	private class NameStoringThread extends Thread {
		private final String original_name;

		public NameStoringThread( Runnable target, String name ) {
			super( target, name );

			this.original_name = name;
		}


		public void restoreOriginalName() {
			setName( original_name );
		}
	}


	private class NameStoringThreadNamingThreadFactory extends NamingThreadFactory {
		public NameStoringThreadNamingThreadFactory( String name_prefix ) {
			super( name_prefix );
		}

		@Override
		protected Thread createThread( Runnable r, String name ) {
			return new NameStoringThread( r, name );
		}
	}


	private class PreventConcurrentRunHandoffRunnable extends HandoffRunnable {
		PreventConcurrentRunHandoffRunnable( Runnable delegate,
			HandoffScheduledFuture<?> handoff_future, ExecutorService main_executor ) {

			super( new TrackingRunnableWrapper( delegate ), handoff_future,
				main_executor );
		}

		@Override
		public void run() {
			TrackingRunnableWrapper wrapper = ( TrackingRunnableWrapper ) delegate;

			boolean currently_running = wrapper.running.get();
			if ( currently_running ) return;

			super.run();
		}
	}


	private class TrackingRunnableWrapper implements Runnable {
		final AtomicBoolean running = new AtomicBoolean( false );

		private final Runnable delegate;

		TrackingRunnableWrapper( Runnable delegate ) {
			this.delegate = delegate;
		}

		@Override
		public void run() {
			running.set( true );
			try {
				delegate.run();
			}
			finally {
				running.set( false );
			}
		}
	}
}
