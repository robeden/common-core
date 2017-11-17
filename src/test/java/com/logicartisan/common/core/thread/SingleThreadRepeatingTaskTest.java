package com.logicartisan.common.core.thread;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;


/**
 *
 */
public class SingleThreadRepeatingTaskTest {
	@Test
	public void testCoalesce() {
		AtomicInteger run_count = new AtomicInteger( 0 );

		SingleThreadRepeatingTask task = new SingleThreadRepeatingTask(
			SharedThreadPool.INSTANCE,
			() -> {
				run_count.incrementAndGet();
				ThreadKit.sleep( 1000 );
			}
		);

		task.run();

		ThreadKit.sleep( 200 );

		task.run();
		task.run();
		task.run();
		task.run();
		task.run();
		task.run();
		task.run();

		ThreadKit.sleep( 1000 );

		assertEquals( 2, run_count.get() );
	}


	@Test
	public void testThreadHammer() throws Exception {
		AtomicInteger counter = new AtomicInteger( 0 );
		SingleThreadRepeatingTask task = new SingleThreadRepeatingTask(
//			Runnable::run,
			SharedThreadPool.INSTANCE,
			() -> {
				counter.incrementAndGet();
				System.out.println( "inc" );
				ThreadKit.sleep( 20 );
			} );

		CountDownLatch latch = new CountDownLatch( 3 );
		Runnable hammer_thread = () -> {
			for( int i = 0; i < 1000; i++ ) {
				task.run();
				ThreadKit.sleep( 1 );
			}
			latch.countDown();
			System.out.println( "Hammer thread exit" );
		};

		SharedThreadPool.execute( hammer_thread );
		SharedThreadPool.execute( hammer_thread );
		SharedThreadPool.execute( hammer_thread );

		latch.await();

		ThreadKit.sleep( 500 );

		int before = counter.get();

		task.run();

		ThreadKit.sleep( 100 );

		assertEquals( before + 1, counter.get() );
	}
}