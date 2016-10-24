package com.logicartisan.common.core.thread;


import javax.annotation.Nonnull;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

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

	private final Lock logic_lock = new ReentrantLock();
	private volatile boolean run_requested = false;
	private volatile boolean thread_running = false;

	/**
	 * @param executor      Executor which will run the action.
	 * @param action        Action to be run.
	 */
	public SingleThreadRepeatingTask( @Nonnull Executor executor,
		@Nonnull Runnable action ) {

		this( executor, action, Throwable::printStackTrace );
	}

	/**
	 * @param executor      Executor which will run the action.
	 * @param action        Action to be run.
	 */
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
		boolean should_run_task = false;

		logic_lock.lock();
		try {
			run_requested = true;

			if ( !thread_running ) {
				thread_running = true;
				should_run_task = true;
			}
		}
		finally {
			logic_lock.unlock();
		}

		if ( should_run_task ) {
			boolean abend = true;
			try {
				executor.execute( inner_runner );
				abend = false;
			}
			finally {
				// If something weird happens, make sure we're not stuck in a bad state.
				// This will at least ensure the task runs next time.
				if ( abend ) {
					logic_lock.lock();
					try {
						thread_running = false;
					}
					finally {
						logic_lock.unlock();
					}
				}
			}
		}
	}


	private class InnerRunner implements Runnable {
		@Override
		public void run() {
			boolean run_again = false;
			do {
				try {
					run_requested = false;

					action.run();
				}
				catch( Throwable t ) {
					error_handler.accept( t );
				}
				finally {
					logic_lock.lock();
					try {
						if ( run_requested ) {
							run_requested = false;
							run_again = true;
						}
						else {
							thread_running = false;
							run_again = false;
						}
					}
					finally {
						logic_lock.unlock();
					}
				}
			}
			while( run_again );
		}
	}



	public static void main( String[] args ) {
		AtomicInteger counter = new AtomicInteger( 0 );

		SingleThreadRepeatingTask task = new SingleThreadRepeatingTask(
			SharedThreadPool.INSTANCE, () -> {
				System.out.println( "Run: " + counter.getAndIncrement() );
			ThreadKit.sleep( 1000 );
			} );

		while( true ) {
			for( int i = 0; i < 15; i++ ) {
				task.run();
				ThreadKit.sleep( 100 );
			}
			System.out.println( "   Long sleep" );
			ThreadKit.sleep( 5000 );
		}
	}
}
