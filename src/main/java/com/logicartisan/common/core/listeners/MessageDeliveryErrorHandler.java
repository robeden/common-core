/*
 * Copyright(c) 2002-2010, Rob Eden
 * All rights reserved.
 */

package com.logicartisan.common.core.listeners;

import javax.annotation.Nonnull;


/**
 * Interface to deal with errors when dispatching messages to listeners.
 *
 * @see ListenerSupport
 */
public interface MessageDeliveryErrorHandler<T> {
	/**
	 * Things we can do.
	 */
	enum ErrorResponse {
		REMOVE_LISTENER,
		DROP_MESSAGE,
		RETRY_MESSAGE
	}


	/**
	 * Called when an error occurs when delivering a message.
	 *
	 * @param listener              The listener to which we were attempting to deliver a
	 *                              message when the error occurred.
	 * @param throwable             The error encountered.
	 * @param overall_error_count   The total number of errors (including this one) ever
	 *                              encountered for this listener.
	 * @param overall_success_count The total number of successful message deliveries for
	 *                              this listener.
	 * @param consecutive_errors    The number of consecutive errors (including this one)
	 *                              encountered for this listener.
	 * @param fatal                 Set to true when an error occurs that indicates that
	 *                              delivery will never succeed.
	 * @return The response to the errors; will not be null.
	 */
	ErrorResponse deliveryError( @Nonnull T listener, @Nonnull Throwable throwable,
		int overall_error_count, int overall_success_count,
		int consecutive_errors, boolean fatal );

	/**
	 * This is called if a listener has too many messages queued for delivery.
	 *
	 * @param listener              The listener to which we were attempting to deliver a
	 *                              message when the error occurred.
	 * @param backlog_size          The current size of the backlog.
	 * @param consecutive_backlog_errors    The number of times a message has failed
	 *                              submission to the queue (including this one) without
	 *                              a successful submission.
	 *
	 * @return The response to the errors; will not be null. RETRY_MESSAGE is not
	 *         recommended as it has the potential to cause significant performance
	 *         problems for the caller in the event that the listener is stuck and no
	 *         messages are being delivered.
	 */
	default ErrorResponse excessiveBacklog( @Nonnull T listener, int backlog_size,
		int consecutive_backlog_errors ) {

		return ErrorResponse.RETRY_MESSAGE;
	}


	/**
	 * Called when/if the last listener is removed.
	 *
	 * Note that there is no locking, so there is no guarantee about the CURRENT number of
	 * listeners. It is guaranteed that the last listener was removed at one point.
	 */
	default void lastListenerRemoved() {}
}
