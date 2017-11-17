/*
 * Copyright(c) 2002-2010, Rob Eden
 * All rights reserved.
 */

package com.logicartisan.common.core.listeners;

import com.logicartisan.common.core.thread.ScheduledExecutor;
import com.logicartisan.common.core.thread.ThreadKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 *
 */
class ListenerSupportImpl<T, A> implements ListenerSupport<T, A>, InvocationHandler {
	private static final Logger LOG =
		LoggerFactory.getLogger( ListenerSupportImpl.class );


	static {
		try {
			long default_delay = Long.getLong(
				"com.starlight.listeners.default_message_delay", 0L ).longValue();
			if ( default_delay > 0 ) {
				LOG.warn( "Default delay is active for ListenerSupport: {} ms",
					Long.valueOf( default_delay ) );
			}
		}
		catch( SecurityException ex ) {
			// ignore
		}
	}


	private final Executor executor;
	private final MessageDeliveryErrorHandler<T> error_handler;
	private final int max_message_backlog;
	private final T dispatcher;
	private final long delay;
	private final TimeUnit delay_unit;

	private final Map<T,ListenerDispatchControl<T,A>> listener_info_map =
		new HashMap<>();
	private final Lock listener_info_map_lock = new ReentrantLock();

	private final AtomicReference<ListenerFilter<A>> filter_reference =
		new AtomicReference<>();

	private final ListenerDispatchControl.RemoveSupport<T> remove_support =
		ListenerSupportImpl.this::remove;


	ListenerSupportImpl( Class<T> listener_class, Executor executor,
		MessageDeliveryErrorHandler<T> error_handler, int max_message_backlog,
		long delay, TimeUnit delay_unit ) {

		this.executor = executor;
		this.error_handler = error_handler;
		this.max_message_backlog = max_message_backlog;
		this.delay = delay;
		this.delay_unit = delay_unit;

		//noinspection unchecked
		dispatcher = ( T ) Proxy.newProxyInstance( listener_class.getClassLoader(),
			new Class[]{ listener_class }, this );
	}

	@Override
	public void add( T listener ) {
		add( listener, null );
	}

	@Override
	public void add( T listener, @Nullable A attachment ) {
		Objects.requireNonNull( listener );

		listener_info_map_lock.lock();
		try {
			if ( listener_info_map.containsKey( listener ) ) return;

			listener_info_map.put( listener,
				new ListenerDispatchControl<>( listener, max_message_backlog,
					error_handler, remove_support, attachment, filter_reference ) );
		}
		finally {
			listener_info_map_lock.unlock();
		}
	}

	/**
	 * Remove a listener.
	 *
	 * @return      True if this was the last listener (i.e., after removing the listener
	 *              there are none left).
	 */
	@Override
	public boolean remove( T listener ) {
		Objects.requireNonNull( listener );

		listener_info_map_lock.lock();
		try {
			listener_info_map.remove( listener );
			return listener_info_map.isEmpty();
		}
		finally {
			listener_info_map_lock.unlock();
		}
	}

	@Override
	public void removeAllListeners() {
		listener_info_map_lock.lock();
		try {
			listener_info_map.clear();
		}
		finally {
			listener_info_map_lock.unlock();
		}
	}

	@Override
	public boolean hasListeners() {
		listener_info_map_lock.lock();
		try {
			return !listener_info_map.isEmpty();
		}
		finally {
			listener_info_map_lock.unlock();
		}
	}

	@Override
	public T dispatch() {
		return dispatcher;
	}

	@Override
	public A getAttachment( T listener ) {
		listener_info_map_lock.lock();
		try {
			ListenerDispatchControl<T, A> ldc = listener_info_map.get( listener );
			if ( ldc == null ) return null;
			return ldc.getAttachment();
		}
		finally {
			listener_info_map_lock.unlock();
		}
	}

	@Override
	public void setListenerFilter( ListenerFilter<A> listener_filter ) {
		filter_reference.set( listener_filter );
	}


	public Object invoke( Object proxy, Method method, Object[] args ) throws Throwable {
		if ( method.getReturnType() == Void.TYPE ) {
			if ( delay > 0 ) {
				if ( executor instanceof ScheduledExecutor ) {
					( ( ScheduledExecutor ) executor ).schedule(
						() -> dispatchMethodInvoke( method, args ), delay, delay_unit );
				}
				else {
					// If the executor isn't a ScheduledExecutor, do a sleep on execution.
					// This allows synchronous dispatching to work as expected.
					executor.execute(
						() -> {
							ThreadKit.sleep( delay, delay_unit );
							dispatchMethodInvoke( method, args );
						} );
				}
				return null;
			}
			else dispatchMethodInvoke( method, args );

			return null;
		}

		throw new UnsupportedOperationException( "The method \"" + method.getName() +
			"\" does not return null and so may not be dispatched." );
	}


	private void dispatchMethodInvoke( Method method, Object[] args ) {
		// To make sure we don't prevent listener activity or screwiness if an event
		// causes a listener to be removed, we'll make a copy of the listeners before
		// dispatching.
		ListenerDispatchControl<T, ?>[] listener_handlers;
		listener_info_map_lock.lock();
		try {
			//noinspection unchecked
			listener_handlers =
				new ListenerDispatchControl[listener_info_map.size()];

			listener_info_map.values().toArray( listener_handlers );
		}
		finally {
			listener_info_map_lock.unlock();
		}

		// Now dispatch to listeners
		for ( ListenerDispatchControl<T, ?> listener_handler : listener_handlers ) {
			if ( listener_handler.postMessageInfoToQueue( method, args ) ) {
				boolean abend = true;
				try {
					executor.execute( listener_handler );
					abend = false;
				}
				catch( Throwable t ) {
					if ( LOG.isWarnEnabled() ) {
						LOG.warn( "Unable to start worker thread to post message: " +
							"{} (args={}", method, Arrays.toString( args ), t );
					}
				}
				finally {
					if ( abend ) listener_handler.indicateWorkerThreadStartFailure();
				}
			}
		}
	}
}