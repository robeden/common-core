/*
 * Copyright(c) 2002-2010, Rob Eden
 * All rights reserved.
 */

package com.logicartisan.common.core.listeners;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;


/**
 * This interface provides management and message dispatching to a set of listeners.
 * Instances are obtained via {@link ListenerSupportFactory}.
 * <p></p>
 * There are two kinds of operations on a ListenerSupport object: listener management and
 * message dispatching. Listener management operations are all done directly on the
 * ListenerSupport object using methods such as {@link #add} and {@link #remove}. Message
 * dispatching is done via the {@link #dispatch} method. An interface matching the
 * listener interface will be return and the methods should simply be called directly an
 * they will be sent to all listeners. Note: only methods returning void will be usable
 * via this mechanism.
 * <p></p>
 * The following is an example usage:
 * <pre>
 *   class MyClass {
 *      private final ListenerSupport&lt;PropertyChangeListener&gt; listeners =
 *         ListenerSupportFactory.create( PropertyChangeListener.class );
 *
 *      private String foo;
 *
 *      public void setFoo( String foo ) {
 *         String old_foo = this.foo;
 *         this.foo = foo;
 *         listeners.dispatch().propertyChanged(
 *            new PropertyChangeEvent( this, "foo", old_foo, foo ) );
 *      }
 *
 *      public String getFoo() {
 *         return foo;
 *      }
 *
 *
 *      public void addPropertyChangeListener( PropertyChangeListener listener ) {
 *         listeners.addListener( listener );
 *      }
 *
 *      public void removePropertyChangeListener( PropertyChangeListener listener ) {
 *         listeners.removeListener( listener );
 *      }
 *   }
 * </pre>
 *
 * @see ListenerSupportFactory
 */
public interface ListenerSupport<T, A> {
	/**
	 * Add a new listener.
	 */
	public void add( T listener );

	/**
	 * Add a new listener with the given attachment.
	 */
	public void add( T listener, A attachment );

	/**
	 * Remove a listener.
	 *
	 * @return      True if this was the last listener (i.e., after removing the listener
	 *              there are none left).
	 */
	public boolean remove( T listener );

	/**
	 * Remove all listeners.
	 */
	public void removeAllListeners();

	/**
	 * Indicate whether or not there are listeners registered.
	 */
	public boolean hasListeners();


	/**
	 * Returns a listener interface which should be called to dispatch a message to the
	 * listeners. It's intended that this method should be chained. For example:
	 * <pre>
	 *   ListenerSupport&lt;PropertyChangeListener&gt; support = ...;
	 *   support.dispatch().propertyChanged( "foo", "old_value", "new_value" );
	 * </pre>
	 * <p></p>
	 * This method will never return null and will not throw exceptions.
	 */
	public T dispatch();


	/**
	 * Return the attachment for a listener.
	 */
	public A getAttachment( T listener );


	/**
	 * Set the filter that will control dispatching of events to listeners based on
	 * attachments.
	 */
	public void setListenerFilter( ListenerFilter<A> filter );



	public static <L,A> Builder<L,A> forType( Class<L> listener_class ) {
		return new Builder<>( listener_class );
	}


	public static class Builder<L,A> {
		private final Class<L> listener_class;

		private Executor executor;
		private MessageDeliveryErrorHandler<L> error_handler;
		private int max_message_backlog = -1;

		private long delay = 0;
		private TimeUnit delay_unit = TimeUnit.MILLISECONDS;



		Builder( Class<L> listener_class ) {
			if ( !listener_class.isInterface() ) {
				throw new IllegalArgumentException( "Class must be an interface" );
			}

			// NOTE: A warning about this system property being set is printed in
			//       ListenerSupportImpl.
			try {
				delay = Long.getLong(
					"com.starlight.listeners.default_message_delay", 0L ).longValue();
			}
			catch( SecurityException ex ) {
				// ignore
			}

			this.listener_class = listener_class;
		}


		public Builder<L,A> asynchronous() {
			return executor( ListenerSupportImpl.SHARED_NOTIFICATION_POOL );
		}

		public Builder<L,A> executor( Executor executor ) {
			assert executor != null : "Executor has already been set: " + this.executor;

			this.executor = executor;
			return this;
		}


		public Builder<L,A> errorHandler( MessageDeliveryErrorHandler<L> handler ) {
			this.error_handler = handler;
			return this;
		}

		public Builder<L,A> maxBacklog( int max_message_backlog ) {
			this.max_message_backlog = max_message_backlog;
			return this;
		}

		public Builder<L,A> delay( long delay, TimeUnit unit ) {
			this.delay = delay;
			this.delay_unit = unit;
			return this;
		}


		public ListenerSupport<L,A> build() {
			if ( executor == null ) {
				executor = Runnable::run;
			}

			if ( error_handler == null ) {
				error_handler = new ErrorCountDeliveryErrorHandler<>( 3 );
			}

			if ( max_message_backlog < 0 ) {
				try {
					max_message_backlog = Integer.getInteger(
						"com.starlight.listeners.default_max_message_backlog", 100 )
						.intValue();
				}
				// Handle denial of access to System property
				catch( SecurityException ex ) {
					max_message_backlog = 100;
				}
			}

			return new ListenerSupportImpl<>( listener_class, executor,
				error_handler, max_message_backlog, delay, delay_unit );
		}
	}
}
