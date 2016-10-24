/*
 * Copyright(c) 2002-2010, Rob Eden
 * All rights reserved.
 */

package com.logicartisan.common.core.listeners;

import com.logicartisan.common.core.thread.ThreadKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.logicartisan.common.core.listeners.MessageDeliveryErrorHandler.ErrorResponse.DROP_MESSAGE;
import static com.logicartisan.common.core.listeners.MessageDeliveryErrorHandler.ErrorResponse.REMOVE_LISTENER;


/**
 * This is both a container class with information for a specific listener and the
 * implementation to dispatch messages to the listener. It's both just to prevent
 * excessive object allocation.
 * <p/>
 * This is mainly an internal class used by ListenerSupport and should generally not be
 * used directly, but it can be useful in certain circumstances for using the same
 * dispatch logic as ListenerSupport.
 */
class ListenerDispatchControl<T, A> implements Runnable {
	private static final Logger LOG =
		LoggerFactory.getLogger( ListenerDispatchControl.class );


	private final T listener;
	private final A attachment;
	private final AtomicReference<ListenerFilter<A>> filter_reference;
	private final MessageDeliveryErrorHandler<T> error_handler;
	private final RemoveSupport<T> remove_support;

	// This indicates when there is a thread actively processing the queue. This is
	// needed when a message is put in the queue in order to know whether a new
	// thread should be started. This should be set before a task is run in the executor
	// and will be unset by the task.
	private final AtomicBoolean thread_processing_queue = new AtomicBoolean( false );

	private final Queue<MessageInfo> message_queue;

	// This is set to true if we receive an IllegalAccessException while trying to call
	// a method on a listener. See note there for more information.
	private boolean need_special_access = false;

	private int overall_error_count = 0;
	private int overall_success_count = 0;
	private int consecutive_error_count = 0;

	private int consecutive_backlog_errors = 0;



	ListenerDispatchControl(T listener, int max_backlog_size,
        MessageDeliveryErrorHandler<T> error_handler, RemoveSupport<T> remove_support,
        A attachment, AtomicReference<ListenerFilter<A>> filter_reference) {

		this.listener = listener;
		this.error_handler = error_handler;
		this.remove_support = remove_support;
		this.attachment = attachment;
		this.filter_reference = filter_reference;

		message_queue = new LinkedBlockingQueue<>( max_backlog_size );
	}


	/**
	 * Post the message info (method & args) to the working queue and determine if a new
	 * worker needs to be run to handle the message.
	 *
	 * @return true if a new worker needs to be run to handler the message.
	 */
	boolean postMessageInfoToQueue( Method method, Object... args ) {
		// If a filter exists for the listener, check it first.
		ListenerFilter<A> filter = filter_reference.get();
		if ( filter != null ) {
			if ( !filter.callMatchesFilter( attachment, method, args ) ) return false;
		}

		while( true ) {     // retry loop
			boolean added = message_queue.offer( new MessageInfo( method, args ) );

			if ( added ) {
				consecutive_backlog_errors = 0;
				break;
			}
			else {
				consecutive_backlog_errors++;
				MessageDeliveryErrorHandler.ErrorResponse response =
					error_handler.excessiveBacklog( listener, message_queue.size(),
						consecutive_backlog_errors );

				if ( response == null ) response = REMOVE_LISTENER;

				LOG.debug( "message_queue for listener ({}) has exceeded its capacity. " +
					"Response will be: {}", listener, response );

				switch (response) {
					case REMOVE_LISTENER:
						handleListenerRemove();
						return false;

					case DROP_MESSAGE:
						return false;

					case RETRY_MESSAGE:
						ThreadKit.sleep( 10 );
						continue;

					default:
						assert false : "Unknown response: " + response;
						return false;       // drop message
				}
			}
		}

		return thread_processing_queue.compareAndSet( false, true );
	}

	void indicateWorkerThreadStartFailure() {
		thread_processing_queue.set( false );
	}



	/**
	 * Process any messages currently in the queue and return.
	 */
	public void run() {
		boolean run_loop = true;
		while( run_loop ) {
			run_loop = false;

			try {
				inner_run();
			}
			finally {
				// Now indicate that we're done processing messages
				thread_processing_queue.set( false );

				// However, before we truly exit, see if the queue is still empty
				if ( !message_queue.isEmpty() ) {
					// If it's not empty, see if a thread has been started. If one hasn't,
					// we'll indicate that we're working on it and restart the cycle.
					if ( thread_processing_queue.compareAndSet( false, true ) ) run_loop = true;
				}
			}
		}
	}

	private void inner_run() {
		MessageInfo info = null;
		boolean retry_message = false;

		while ( retry_message || ( info = message_queue.poll() ) != null ) {
			assert !retry_message || info != null : "Told to retry null message";

			// Clear the retry flag
			retry_message = false;

			final Method method = info.getMethod();
			final Object[] args = info.getArgs();


			// Allow us to have access to stuff we might not otherwise be able to get
			// to. We need this if there's a package-private listener class we don't
			// have access to.
			// We don't enable this by default to avoid all the checks for security
			// managers and security checks if one is installed. Instead it's enabled
			// if we see we need to.
			if ( need_special_access ) {
				method.setAccessible( true );
			}


			Throwable error;
			boolean error_is_fatal = false;
			try {
				method.invoke( listener, args );

				overall_success_count++;
				consecutive_error_count = 0;

				// Successful delivery, continue!
				continue;
			}
			catch ( IllegalAccessException e ) {
				if ( need_special_access ) {
					// We tried to enable access and it didn't work. Count this as an
					// error we won't recover from.
					error = e;
					error_is_fatal = true;
				}
				else {
					// See comment above. Enable access to the class
					need_special_access = true;

					// Retry the message with special access enabled
					retry_message = true;

					continue;
				}
			}
			catch ( InvocationTargetException ex ) {
				error = ex.getTargetException();
			}
			catch( AssertionError er ) {
				throw er;
			}
			catch ( Throwable t ) {
				error = t;
			}

			assert error != null;

			overall_error_count++;
			consecutive_error_count++;

			MessageDeliveryErrorHandler.ErrorResponse response;
			try {
				response = error_handler.deliveryError( listener, error,
					overall_error_count, overall_success_count, consecutive_error_count,
					error_is_fatal );
			}
			catch( Throwable t ) {
				LOG.warn( "Exception thrown while dispatching error notification " +
					"to MessageDeliveryErrorHandler. The message will be dropped.",
					t );
				response = DROP_MESSAGE;
			}

			switch ( response ) {
				case DROP_MESSAGE:
					retry_message = false;
					break;

				case RETRY_MESSAGE:
					retry_message = true;
					ThreadKit.sleep( 1000 );
					break;

				case REMOVE_LISTENER:
					handleListenerRemove();
					return;

				default:
					assert false : "Unknown response type";
			}
		}
	}

	private void handleListenerRemove() {
		// Remove the listener, clear the queue
		boolean was_last_listener = remove_support.removeListener( listener );
		message_queue.clear();

		if ( was_last_listener ) {
			error_handler.lastListenerRemoved();
		}
	}


	final A getAttachment() {
		return attachment;
	}


	public interface RemoveSupport<T> {
		/**
		 * Return true if this is the last listener.
		 */
		boolean removeListener( T listener );
	}
}
