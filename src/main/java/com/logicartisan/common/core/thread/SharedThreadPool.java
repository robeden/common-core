/*
 * Copyright(c) 2002-2010, Rob Eden
 * All rights reserved.
 */
package com.logicartisan.common.core.thread;

import javax.annotation.Nonnull;
import java.util.concurrent.*;


/**
 * A general-purpose thread pool for short-lived tasks.
 */
@SuppressWarnings( { "unused", "WeakerAccess" } )
public class SharedThreadPool {
	@SuppressWarnings( "WeakerAccess" )
	public static final ScheduledExecutor INSTANCE = new SharedThreadPoolImpl();


	/**
	 * See {@link Executor#execute(Runnable)}.
	 */
	public static void execute( @Nonnull Runnable command ) {
		INSTANCE.execute( command );
	}

	/**
	 * See {@link ScheduledExecutorService#schedule(Callable, long, TimeUnit)}
	 */
	public static <V> ScheduledFuture<V> schedule( @Nonnull Callable<V> callable, long delay,
		@Nonnull TimeUnit unit ) {

		return INSTANCE.schedule( callable, delay, unit );
	}

	/**
	 * See {@link ScheduledExecutorService#schedule(Runnable, long, TimeUnit)}
	 */
	public static ScheduledFuture<?> schedule( @Nonnull Runnable command, long delay,
		@Nonnull TimeUnit unit ) {

		return INSTANCE.schedule( command, delay, unit );
	}

	/**
	 * See {@link ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)}
	 */
	public static ScheduledFuture<?> scheduleAtFixedRate( @Nonnull Runnable command,
		long initialDelay, long period, @Nonnull TimeUnit unit ) {

		return INSTANCE.scheduleAtFixedRate( command, initialDelay, period, unit );
	}

	/**
	 * See {@link ScheduledExecutorService#scheduleWithFixedDelay(Runnable, long, long, TimeUnit)}
	 */
	@SuppressWarnings( { "unchecked" } )
	public static ScheduledFuture<?> scheduleWithFixedDelay( @Nonnull Runnable command,
		long initialDelay, long delay, @Nonnull TimeUnit unit ) {

		return INSTANCE.scheduleWithFixedDelay( command, initialDelay, delay, unit );
	}
}
