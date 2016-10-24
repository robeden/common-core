/*
 * Copyright(c) 2002-2010, Rob Eden
 * All rights reserved.
 */

package com.logicartisan.common.core.listeners;

import java.util.concurrent.Executor;


/**
 * This class creates instances of {@link ListenerSupport} for managing listeners and
 * dispatching events to them.
 * <p></p>
 * There are various options available when creating a ListenerSupport object, but the
 * main decision is whether to dispatch messages synchronously (blocking until all
 * messages have been delivered) or asynchronously (returning immediately and delivering
 * messages in the background): <ul> <li>Synchronous is appropriate when all listeners
 * must have consistent and correct state when the message is received and need to
 * interact with the dispatcher to get more information. For example, a
 * PropertyChangeListener that will act on an object when it achieves a particular value
 * for a property.</li>
 * <li>Asynchronous is more appropriate when state is not particularly important, an event
 * contains all the information listeners need, or if calls can be remote or otherwise
 * costly. It is important to note that with asynchronous delivery the same message can
 * arrive at listeners at different times, however message order to individual listeners
 * is always preserved.</li> </ul> Typically it is "safer" to use synchronous delivery if
 * consistency could be a concern, but asynchronous will generally perform much better.
 */
public final class ListenerSupportFactory {
	// Hidden constructor

	private ListenerSupportFactory() {
	}


	/**
	 * Creates a simple ListenerSupport object that either dispatches messages
	 * synchronously (blocking until all messages have been delivered) or asynchronously
	 * (returning immediately and delivering messages in the background). See the class
	 * documentation for more discussion of the options.
	 *
	 * @param listener_class The class of the listeners.
	 * @param asynchronous   Whether or not messages will be dispatched asynchronously
	 *                       (see  the class documentation for more information).
	 *                       Asynchronous delivery is done using a VM-wide shared
	 *                       thread pool.
	 *
	 * @deprecated Use {@link ListenerSupport#forType(Class)}
	 */
	@Deprecated
	public static <T,A> ListenerSupport<T,A> create(
		Class<T> listener_class, boolean asynchronous ) {

		ListenerSupport.Builder<T,A> builder = ListenerSupport.forType( listener_class );
		if ( asynchronous ) builder = builder.asynchronous();
		return builder.build();
	}


	/**
	 * Creates a simple ListenerSupport object that either dispatches messages
	 * synchronously (blocking until all messages have been delivered) or asynchronously
	 * (returning immediately and delivering messages in the background). See the class
	 * documentation for more discussion of the options.
	 *
	 * @param listener_class The class of the listeners.
	 * @param asynchronous   Whether or not messages will be dispatched asynchronously
	 *                       (see  the class documentation for more information).
	 *                       Asynchronous delivery is done using a VM-wide shared
	 *                       thread pool.
	 *
	 * @deprecated Use {@link ListenerSupport#forType(Class)}
	 */
	@Deprecated
	public static <T,A> ListenerSupport<T,A> create(
		Class<T> listener_class, boolean asynchronous,
		MessageDeliveryErrorHandler<T> error_handler, int max_message_backlog ) {

		ListenerSupport.Builder<T,A> builder = ListenerSupport.forType( listener_class );
		if ( asynchronous ) builder = builder.asynchronous();
		return builder.errorHandler( error_handler )
			.maxBacklog( max_message_backlog )
			.build();
	}


	/**
	 * Creates a ListenerSupport object that uses the given Executor to dispatch messages.
	 *
	 * @deprecated Use {@link ListenerSupport#forType(Class)}
	 */
	@Deprecated
	public static <T> ListenerSupport<T,?> create( Class<T> listener_class,
		Executor executor ) {

		return ListenerSupport.forType( listener_class )
			.executor( executor )
			.build();
	}


	/**
	 * Creates a ListenerSupport object that uses the given Executor to dispatch messages.
	 *
	 * @deprecated Use {@link ListenerSupport#forType(Class)}
	 */
	@Deprecated
	public static <T,A> ListenerSupport<T,A> create( Class<T> listener_class,
		Executor executor, MessageDeliveryErrorHandler<T> error_handler ) {

		return ListenerSupport.<T,A>forType( listener_class )
			.executor( executor )
			.errorHandler( error_handler )
			.build();
	}


	/**
	 * Creates a ListenerSupport object that uses the given Executor to dispatch messages.
	 *
	 * @deprecated Use {@link ListenerSupport#forType(Class)}
	 */
	@Deprecated
	public static <T,A> ListenerSupport<T,A> create( Class<T> listener_class,
		Executor executor, MessageDeliveryErrorHandler<T> error_handler,
		int max_message_backlog ) {

		return ListenerSupport.<T,A>forType( listener_class )
			.executor( executor )
			.errorHandler( error_handler )
			.maxBacklog( max_message_backlog )
			.build();
	}
}
