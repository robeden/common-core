package com.logicartisan.common.core.thread;


import javax.annotation.Nonnull;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;


/**
 * This class provides support for actions that should happen in a single thread
 * repeatedly. In addition, the action may be requested from any other thread any
 * number of times but multiple requests will be coalesced at run time. The logic is
 * as follows:<ul>
 *     <li>If the action is requested and isn't currently running, a thread is started and
 *     runs the action.</li>
 *     <li>If the action is requested and is currently running, the thread currently
 *     running will run again immediately after completing the current run.</li>
 *     <li>If the action is requested and there is already request queued, no changes
 *     occur (i.e., the requests are coalesced into a single request).</li>
 * </ul>
 */
public class SingleThreadRepeatingTask implements Runnable {


	private final Executor executor;
	private final Runnable action;
	private final Consumer<Throwable> error_handler;

	private final Runnable inner_runner = new InnerRunner();

	private enum State {
		/** No requests, no processor (thread) running. */
		IDLE,
		/** No requests, processor (thread) is running. */
		PROCESSOR_RUNNING,
		/** Request(s) pending, processor (thread) is running. */
		PROCESSOR_RUNNING_REQUEST_PENDING
	}
	private final AtomicReference<State> state = new AtomicReference<>( State.IDLE );


	/**
	 * @param executor      Executor which will run the action.
	 * @param action        Action to be run.
	 */
	@SuppressWarnings( "WeakerAccess" )
	public SingleThreadRepeatingTask( @Nonnull Executor executor,
		@Nonnull Runnable action ) {

		this( executor, action, Throwable::printStackTrace );
	}

	/**
	 * @param executor      Executor which will run the action.
	 * @param action        Action to be run.
	 */
	@SuppressWarnings( "WeakerAccess" )
	public SingleThreadRepeatingTask( @Nonnull Executor executor,
		@Nonnull Runnable action,
		@Nonnull Consumer<Throwable> error_handler ) {

		this.executor = requireNonNull( executor );
		this.action = requireNonNull( action );
		this.error_handler = requireNonNull( error_handler );
	}


	/**
	 * Request the action to be run.
	 */
	@Override
	public void run() {
		State old_state = state.getAndSet( State.PROCESSOR_RUNNING_REQUEST_PENDING );
		if ( old_state == State.IDLE ) {
			boolean abend = true;
			try {
				executor.execute( inner_runner );
				abend = false;
			}
			finally {
				// If something weird happens, make sure we're not stuck in a bad state.
				// This will at least ensure the task runs next time.
				if ( abend ) {
					state.set( State.IDLE );
				}
			}
		}
	}


	private class InnerRunner implements Runnable {
		@Override
		public void run() {
			UnaryOperator<State> state_updater = ( state_before_update ) -> {
				switch ( state_before_update ) {
					default:
						assert false: "Unknown state type: " + state_before_update;
					case IDLE:
					case PROCESSOR_RUNNING:
						return State.IDLE;

					case PROCESSOR_RUNNING_REQUEST_PENDING:
						return State.PROCESSOR_RUNNING;

				}
			};

			while( state.getAndUpdate( state_updater ) ==
				State.PROCESSOR_RUNNING_REQUEST_PENDING ) {

				try {
					action.run();
				}
				catch( Throwable t ) {
					error_handler.accept( t );
				}
			}
		}
	}
}
