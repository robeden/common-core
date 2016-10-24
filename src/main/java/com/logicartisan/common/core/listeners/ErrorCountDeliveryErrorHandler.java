/*
 * Copyright(c) 2002-2010, Rob Eden
 * All rights reserved.
 */

package com.logicartisan.common.core.listeners;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Simple error handler that removes listeners after a given number of consecutive errors
 * (or when fatal error occur).
 */
public class ErrorCountDeliveryErrorHandler<T> implements MessageDeliveryErrorHandler<T> {
	private static final boolean ERROR_DEBUG =
		System.getProperty( "starlight.listeners.print_dispatch_errors" ) != null;

	private static final Logger LOG =
		LoggerFactory.getLogger( ErrorCountDeliveryErrorHandler.class );

	private final int max_error_count;
	private final boolean retry_message;


	/**
	 * @param max_error_count The max errors before a listener is dropped.
	 */
	public ErrorCountDeliveryErrorHandler( int max_error_count ) {
		this( max_error_count, true );
	}

	/**
	 * @param max_error_count The max errors before a listener is dropped.
	 * @param retry_message   If true, messages that cause an error will be retried.
	 *                        Otherwise they will be dropped.
	 */
	public ErrorCountDeliveryErrorHandler( int max_error_count, boolean retry_message ) {
		this.max_error_count = max_error_count;
		this.retry_message = retry_message;
	}


	public ErrorResponse deliveryError( T listener, Throwable throwable,
		int overall_error_count, int overall_success_count, int consecutive_errors,
		boolean fatal ) {

		// Special-case AssertionError logging so it's seen during unit tests
		if ( throwable instanceof AssertionError ) {
			LOG.error( "Listener delivery error (fatal:{} consecutive_errors:{} " +
				"max_error_count:{}", Boolean.valueOf( fatal ),
				Integer.valueOf( consecutive_errors ), Integer.valueOf( max_error_count ),
				throwable );
		}
		else if ( LOG.isDebugEnabled() ) {
			LOG.debug( "Listener delivery error (fatal:{} consecutive_errors:{} " +
				"max_error_count:{}", Boolean.valueOf( fatal ),
				Integer.valueOf( consecutive_errors ), Integer.valueOf( max_error_count ),
				throwable );
		}
		if ( ERROR_DEBUG ) {
			synchronized ( System.err ) {
				System.err.println( "Listener delivery error (fatal: " + fatal +
					"  consecutive_errors: " + consecutive_errors +
					"  max_error_count: " + max_error_count + "  error: " + throwable );
				throwable.printStackTrace( System.err );
			}
		}

		if ( fatal || consecutive_errors >= max_error_count ) {
			LOG.info( "Listener being removed due to dispatch errors: {}", listener,
				throwable );
			return ErrorResponse.REMOVE_LISTENER;
		}
		else {
			if ( retry_message ) return ErrorResponse.RETRY_MESSAGE;
			else return ErrorResponse.DROP_MESSAGE;
		}
	}

	@Override
	public ErrorResponse excessiveBacklog( T listener, int backlog_size,
		int consecutive_backlog_errors ) {

		return ErrorResponse.REMOVE_LISTENER;
	}

	@Override
	public void lastListenerRemoved() {}


	private boolean isOrCausedBy( Throwable t, Class<? extends Throwable> ex_class ) {
		while ( t != null ) {
			if ( ex_class.isAssignableFrom( t.getClass() ) ) return true;
			t = t.getCause();
		}

		return false;
	}
}
