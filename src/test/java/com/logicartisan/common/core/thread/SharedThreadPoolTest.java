package com.logicartisan.common.core.thread;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


/**
 *
 */
public class SharedThreadPoolTest extends TestCase {
	public void testExecute() throws Exception {
		TestRunnable runner = new TestRunnable( 3000 );

		long start = System.currentTimeMillis();
		SharedThreadPool.execute( runner );

		// Should return immediately
		assertTrue( System.currentTimeMillis() - start < 1000 );

		// Should be starting immediately
		assertTrue( runner.start_indicator.await( 1500, TimeUnit.MILLISECONDS ) );

		assertTrue( runner.end_indicator.await( 4000, TimeUnit.MILLISECONDS ) );
		long duration = System.currentTimeMillis() - start;
		assertTrue( "Duration = " + duration,
			duration >= 3000 && duration < 4000 );  // complete in 3-4 seconds
		System.out.println( "Duration: " + duration + "  Thread: " +
			runner.executing_thread.get() );

		assertNotNull( runner.executing_thread.get() );
		assertNotSame( Thread.currentThread(), runner.executing_thread.get() );
	}


	public void testScheduleCallable() throws Exception {
		TestCallable<String> callable = new TestCallable<>( 3000, "Hello world" );

		long start = System.currentTimeMillis();
		final ScheduledFuture<String> future =
			SharedThreadPool.schedule( callable, 3, TimeUnit.SECONDS );

		final ObjectSlot<Long> future_return_time = new ObjectSlot<>();
		final ObjectSlot<String> future_return_value = new ObjectSlot<>();
		new Thread( () -> {
			try {
				future_return_value.set( future.get( 10, TimeUnit.SECONDS ) );
				future_return_time.set( Long.valueOf( System.currentTimeMillis() ) );
			}
			catch( Exception ex ) {
				ex.printStackTrace();
			}
		} ).start();

		assertFalse( future.isDone() );
		assertFalse( future.isCancelled() );


		// Should return immediately
		assertTrue( System.currentTimeMillis() - start < 1000 );

		assertTrue( callable.start_indicator.await( 4000, TimeUnit.MILLISECONDS ) );
		long time_to_start = System.currentTimeMillis() - start;
		assertTrue( "Time to start: " + time_to_start,
			time_to_start >= 3000 && time_to_start < 4000 );

		assertFalse( future.isDone() );
		assertFalse( future.isCancelled() );

		assertTrue( callable.end_indicator.await( 4000, TimeUnit.MILLISECONDS ) );
		long duration = System.currentTimeMillis() - start;
		assertTrue( "Duration = " + duration,
			duration >= 6000 && duration < 7000 );  // complete in 6-7 seconds

		assertEquals( "Hello world", future_return_value.waitForValue( 500 ) );
		long future_duration = future_return_time.waitForValue( 100 ).longValue() - start;
		assertTrue( "Future duration: " + future_duration + "  duration: " + duration,
			Math.abs( future_duration - duration ) < 100 );

		System.out.println( "Duration: " + duration + " (" + future_duration +
			")  Thread: " + callable.executing_thread.get() );

		assertNotNull( callable.executing_thread.get() );
		assertNotSame( Thread.currentThread(), callable.executing_thread.get() );
	}


	public void testScheduleRunnable() throws Exception {
		TestRunnable runner = new TestRunnable( 3000 );

		long start = System.currentTimeMillis();
		final ScheduledFuture<?> future =
			SharedThreadPool.schedule( runner, 3, TimeUnit.SECONDS );

		final ObjectSlot<Long> future_return_time = new ObjectSlot<>();
		new Thread( () -> {
			try {
				future.get();
				future_return_time.set( Long.valueOf( System.currentTimeMillis() ) );
			}
			catch( Exception ex ) {
				ex.printStackTrace();
			}
		} ).start();

		assertFalse( future.isDone() );
		assertFalse( future.isCancelled() );


		// Should return immediately
		assertTrue( System.currentTimeMillis() - start < 1000 );

		assertTrue( runner.start_indicator.await( 4000, TimeUnit.MILLISECONDS ) );
		long time_to_start = System.currentTimeMillis() - start;
		assertTrue( "Time to start: " + time_to_start,
			time_to_start >= 3000 && time_to_start < 4000 );

		assertFalse( future.isDone() );
		assertFalse( future.isCancelled() );

		assertTrue( runner.end_indicator.await( 4000, TimeUnit.MILLISECONDS ) );
		long duration = System.currentTimeMillis() - start;
		assertTrue( "Duration = " + duration,
			duration >= 6000 && duration < 7000 );  // complete in 6-7 seconds

		long future_duration = future_return_time.waitForValue( 100 ).longValue() - start;
		assertTrue( "Future duration: " + future_duration + "  duration: " + duration,
			Math.abs( future_duration - duration ) < 100 );

		System.out.println( "Duration: " + duration + " (" + future_duration +
			")  Thread: " + runner.executing_thread.get() );

		assertNotNull( runner.executing_thread.get() );
		assertNotSame( Thread.currentThread(), runner.executing_thread.get() );
	}


	public void testCancelWithInterrupt() throws Exception {
		TestCallable<String> callable =
			new TestCallable<>( 5000, "Shouldn't get here" );
		ScheduledFuture<String> temp_future =
			SharedThreadPool.schedule( callable, 1, TimeUnit.SECONDS );

		// Schedule a task and cancel immediately
		temp_future.cancel( true );
		assertFalse( callable.start_indicator.await( 2, TimeUnit.SECONDS ) );

		// Schedule a new task and cancel while running
		final ScheduledFuture<String> future =
			SharedThreadPool.schedule( callable, 1, TimeUnit.SECONDS );

		final AtomicBoolean canceled_flag = new AtomicBoolean( false );
		final CountDownLatch return_latch = new CountDownLatch( 1 );
		new Thread( () -> {
			try {
				future.get();
			}
			catch( CancellationException ex ) {
				// This is good
				canceled_flag.set( true );
			}
			catch( Exception ex ) {
				ex.printStackTrace();
			}
			finally {
				return_latch.countDown();
			}
		} ).start();

		ThreadKit.sleep( 1200 );

		long time_before_cancel = System.currentTimeMillis();
		future.cancel( true );

		return_latch.await( 10, TimeUnit.SECONDS );
		long duration = System.currentTimeMillis() - time_before_cancel;

		assertTrue( "Duration from cancel to return: " + duration,
			duration < 1000 );
		assertTrue( canceled_flag.get() );
	}


	public void testFixedRate() throws Exception {
		TestRunnable runner = new TestRunnable( 500 );
		ScheduledFuture<?> future = SharedThreadPool.scheduleAtFixedRate(
			runner,
			1, 1, TimeUnit.SECONDS );
		long start_time = System.currentTimeMillis();

		ThreadKit.sleep( 3500 );    // Wait 6 seconds (plus a bit of slop)

		future.cancel( false );

		ThreadKit.sleep( 2000 );    // wait 2 more seconds

		assertEquals( "Run times: " + runner.run_times, 3, runner.run_times.size() );

		// First time should have been around 1 second after start
		long time = runner.run_times.get( 0 ) - start_time;
		assertTrue( "First run time: " + time, time > 900 && time < 1100 );

		// First time should have been around 1 second after start
		time = runner.run_times.get( 1 ) - start_time;
		assertTrue( "Second run time: " + time, time > 1900 && time < 2100 );

		// First time should have been around 1 second after start
		time = runner.run_times.get( 2 ) - start_time;
		assertTrue( "Third run time: " + time, time > 2900 && time < 3100 );
	}

	// Task takes longer than the scheduled time to complete
	public void testFixedRateOverlap() throws Exception {
		TestRunnable runner = new TestRunnable( 1000 );
		ScheduledFuture<?> future = SharedThreadPool.scheduleAtFixedRate( runner,
			300, 300, TimeUnit.MILLISECONDS );  // Will run at: 600, 900, 1200, 1500
		try {
			assertEquals( 0, runner.run_counter.get() );

			ThreadKit.sleep( 400 );                         // +400
			assertEquals( 1, runner.run_counter.get() );

			ThreadKit.sleep( 300 );                         // +700
			assertEquals( 1, runner.run_counter.get() );

			ThreadKit.sleep( 250 );                         // +950
			assertEquals( 1, runner.run_counter.get() );

			ThreadKit.sleep( 200 );                         // +1150
			assertEquals( 1, runner.run_counter.get() );

			ThreadKit.sleep( 250 );                         // +1400
			assertEquals( 1, runner.run_counter.get() );

			ThreadKit.sleep( 200 );                         // +1600
			assertEquals( 2, runner.run_counter.get() );
		}
		finally {
			if ( future != null ) future.cancel( true );
		}
	}


	public void testFixedDelay() throws Exception {
		TestRunnable runner = new TestRunnable( 500 );
		ScheduledFuture<?> future = SharedThreadPool.scheduleWithFixedDelay(
			runner, 1, 1, TimeUnit.SECONDS );
		final long start_time = System.currentTimeMillis();

		ThreadKit.sleep( 3500 );    // Wait 3.5 seconds

		future.cancel( false );

		ThreadKit.sleep( 2000 );    // wait 2 more seconds

		// Morph the times so they're relative to the start time (for easier viewing)
		runner.run_times.replaceAll( time -> time - start_time );
		assertEquals( "Run times: " + runner.run_times, 2, runner.run_times.size() );

		// First time should have been around 1 second after start
		long time = runner.run_times.get( 0 );
		assertTrue( "First run time: " + time, time > 900 && time < 1100 );

		// First time should have been around 2.5 seconds after start
		time = runner.run_times.get( 1 );
		assertTrue( "Second run time: " + time, time > 2400 && time < 2600 );
	}


	public void testNameRestoration() throws Exception {
		final AtomicReference<String> original_name = new AtomicReference<>();
		final AtomicReference<Thread> original_thread = new AtomicReference<>();

		final CountDownLatch latch1 = new CountDownLatch( 1 );

		SharedThreadPool.execute( () -> {
			// Store the thread and original name
			original_thread.set( Thread.currentThread() );
			original_name.set( Thread.currentThread().getName() );

			Thread.currentThread().setName( "THIS NAME SHOULD BE RESET" );

			latch1.countDown();
		} );

		// Wait for the task to finish
		assertTrue( "Timed out waiting for completion",
			latch1.await( 3, TimeUnit.SECONDS ) );

		final AtomicReference<String> new_name = new AtomicReference<>();
		final AtomicReference<Thread> new_thread = new AtomicReference<>();

		final CountDownLatch latch2 = new CountDownLatch( 1 );

		ThreadKit.sleep( 1000 );

		SharedThreadPool.execute( () -> {
			new_thread.set( Thread.currentThread() );
			new_name.set( Thread.currentThread().getName() );

			latch2.countDown();
		} );

		// Wait for the task to finish
		assertTrue( "Timed out waiting for completion",
			latch2.await( 3, TimeUnit.SECONDS ) );

		assertSame( original_thread.get(), new_thread.get() );
		assertEquals( original_name.get(), new_name.get() );
	}



	public void testCancelFromRunnable() throws Exception {
		AtomicInteger counter = new AtomicInteger( 0 );
		AtomicReference<ScheduledFuture<?>> future_slot = new AtomicReference<>();

		Runnable runnable = () -> {
			counter.incrementAndGet();

			ScheduledFuture<?> future = future_slot.get();
			if ( future == null ) {
				System.err.println( "FUTURE WAS NULL" );
				fail( "Future shouldn't have been null" );
			}

			future.cancel( false );
		};

		ScheduledFuture<?> future = SharedThreadPool.scheduleWithFixedDelay(
			runnable, 2, 2, TimeUnit.SECONDS );
		future_slot.set( future );

		ThreadKit.sleep( 5, TimeUnit.SECONDS );

		assertEquals( 1, counter.intValue() );
	}
	


	static class TestRunnable implements Runnable {
		final CountDownLatch start_indicator;
		final CountDownLatch end_indicator;

		final AtomicReference<Thread> executing_thread = new AtomicReference<>();

		final List<Long> run_times = new ArrayList<>();
		final AtomicInteger run_counter = new AtomicInteger( 0 );

		private final long sleep;

		TestRunnable( long sleep ) {
			this.start_indicator = new CountDownLatch( 1 );
			this.sleep = sleep;
			this.end_indicator = new CountDownLatch( 1 );
		}

		@Override public void run() {
			executing_thread.set( Thread.currentThread() );
			run_times.add( System.currentTimeMillis() );
			run_counter.incrementAndGet();
			start_indicator.countDown();

			if ( sleep > 0 ) ThreadKit.sleep( sleep );

			end_indicator.countDown();
		}
	}

	static class TestCallable<V> implements Callable<V> {
		final CountDownLatch start_indicator;
		final CountDownLatch end_indicator;

		final AtomicReference<Thread> executing_thread = new AtomicReference<>();

		volatile boolean was_interrupted = false;

		private final long sleep;
		private final V to_return;

		TestCallable( long sleep, V to_return ) {
			this.start_indicator = new CountDownLatch( 1 );
			this.sleep = sleep;
			this.end_indicator = new CountDownLatch( 1 );
			this.to_return = to_return;
		}

		@Override public V call() {
			executing_thread.set( Thread.currentThread() );
			start_indicator.countDown();

			if ( sleep > 0 ) was_interrupted = !ThreadKit.sleep( sleep );

			end_indicator.countDown();

			return to_return;
		}
	}
}
