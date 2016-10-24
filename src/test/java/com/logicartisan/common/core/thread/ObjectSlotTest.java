package com.logicartisan.common.core.thread;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;


public class ObjectSlotTest {
	@Test
	public void consumeImmediate() throws Exception {
		AtomicReference<String> value = new AtomicReference<>();

		ObjectSlot<String> slot = new ObjectSlot<>( "Hello" );

		Future<String> future = slot.consumeValue( value::set );

		assertEquals( "Hello", value.get() );

		assertTrue( future.isDone() );
		assertFalse( future.isCancelled() );
		assertFalse( future.cancel( false ) );
		assertFalse( future.cancel( true ) );
		assertEquals( "Hello", future.get() );
		assertEquals( "Hello", future.get( 1, TimeUnit.SECONDS ) );
	}

	@Test
	public void consumeDelayed() throws Exception {
		AtomicReference<String> value = new AtomicReference<>();

		ObjectSlot<String> slot = new ObjectSlot<>();

		Future<String> future = slot.consumeValue( value::set );

		assertNull( value.get() );

		assertFalse( future.isDone() );
		assertFalse( future.isCancelled() );


		ObjectSlot<String> get_result = new ObjectSlot<>();
		SharedThreadPool.INSTANCE.execute( () -> {
			try {
				get_result.set( future.get() );
			}
			catch ( Exception e ) {
				e.printStackTrace();
			}
		} );
		ObjectSlot<String> get_timeout_result = new ObjectSlot<>();
		SharedThreadPool.INSTANCE.execute( () -> {
			try {
				get_timeout_result.set( future.get( 5, TimeUnit.SECONDS ) );
			}
			catch ( Exception e ) {
				e.printStackTrace();
			}
		} );


		slot.set( "Hi" );

		assertEquals( "Hi", value.get() );

		assertTrue( future.isDone() );
		assertFalse( future.isCancelled() );

		assertEquals( "Hi", get_result.waitForValue( 1000 ) );
		assertEquals( "Hi", get_timeout_result.waitForValue( 1000 ) );
	}

	@Test
	public void consumeDelayed_onlyOnce() {
		AtomicReference<String> value_slot = new AtomicReference<>();

		ObjectSlot<String> slot = new ObjectSlot<>();

		slot.consumeValue( value_slot::set );

		slot.set( "First" );
		slot.set( "Second" );

		assertEquals( "First", value_slot.get() );
	}



	@Test
	public void consumeDelayed_canceled() throws Exception {
		ObjectSlot<String> slot = new ObjectSlot<>();

		Future<String> future =
			slot.consumeValue( value -> fail( "Shouldn't be called" ) );

		future.cancel( false );

		slot.set( "Hi" );
	}



	@Test
	public void compareWithPredicate() {
		ObjectSlot<String> slot = new ObjectSlot<>();

		assertFalse( slot.compareAndSet( ( string ) -> string != null, "BAD" ) );
		assertNull( slot.get() );

		assertTrue( slot.compareAndSet( ( string ) -> string == null, "GOOD" ) );
		assertEquals( "GOOD", slot.get() );
	}


	@Test
	public void consumeAllValues_simple() {
		ObjectSlot<String> slot = new ObjectSlot<>( "A" );

		List<Optional<String>> consumed = new ArrayList<>();
		Future<Optional<String>> future = slot.consumeAllValues( consumed::add );

		slot.set( "B" );
		slot.set( "C" );
		slot.set( null );
		slot.set( "D" );

		assertTrue( future.cancel( true ) );
		assertFalse( future.cancel( true ) );

		slot.set( "XXXXX" );

		assertEquals( "A", consumed.remove( 0 ).orElse( "Ack, it was empty" ) );
		assertEquals( "B", consumed.remove( 0 ).orElse( "Ack, it was empty" ) );
		assertEquals( "C", consumed.remove( 0 ).orElse( "Ack, it was empty" ) );
		assertEquals( "EMPTY", consumed.remove( 0 ).orElse( "EMPTY" ) );
		assertEquals( "D", consumed.remove( 0 ).orElse( "Ack, it was empty" ) );
	}



	@Test
	public void set() {
		ObjectSlot<String> slot = new ObjectSlot<>();
		assertNull( slot.get() );

		slot.set( "Hi" );
		assertEquals( "Hi", slot.get() );
	}

	@Test
	public void accept() {
		ObjectSlot<String> slot = new ObjectSlot<>();
		assertNull( slot.get() );

		slot.accept( "Hi" );
		assertEquals( "Hi", slot.get() );
	}



	@Test
	public void compareAndSet_nullMatch() throws Exception {
		ObjectSlot<String> slot = new ObjectSlot<>();
		slot.compareAndSet( ( String ) null, "Hi" );
		assertEquals( "Hi", slot.get() );
	}

	@Test
	public void compareAndSet_nullMismatch() throws Exception {
		ObjectSlot<String> slot = new ObjectSlot<>();
		slot.compareAndSet( "Bye", "Hi" );
		assertNull( slot.get() );
	}

	@Test
	public void compareAndSet_nonnullMatch() throws Exception {
		ObjectSlot<String> slot = new ObjectSlot<>( "Bye" );
		slot.compareAndSet( "Bye", "Hi" );
		assertEquals( "Hi", slot.get() );
	}

	@Test
	public void compareAndSet_nonnullMismatch() throws Exception {
		ObjectSlot<String> slot = new ObjectSlot<>( "Hola" );
		slot.compareAndSet( "Bye", "Hi" );
		assertEquals( "Hola", slot.get() );
	}

	@Test
	public void compareAndSet_predicateMatch() throws Exception {
		ObjectSlot<String> slot = new ObjectSlot<>( "Bye" );
		slot.compareAndSet( value -> value.equals( "Bye" ), "Hi" );
		assertEquals( "Hi", slot.get() );
	}

	@Test
	public void compareAndSet_predicateMismatch() throws Exception {
		ObjectSlot<String> slot = new ObjectSlot<>( "Bye" );
		slot.compareAndSet( value -> value.equals( "Hola" ), "Hi" );
		assertEquals( "Bye", slot.get() );
	}



	@Test
	public void getAndSet() throws Exception {
		ObjectSlot<String> slot = new ObjectSlot<>( "Bye" );
		String previous = slot.getAndSet( "Hi" );
		assertEquals( "Bye", previous );
		assertEquals( "Hi", slot.get() );
	}



	@Test
	public void clear() throws Exception {
		ObjectSlot<String> slot = new ObjectSlot<>( "Bye" );
		slot.clear();
		assertNull( slot.get() );
	}



	@Test
	public void toString_nonnull() throws Exception {
		ObjectSlot<String> slot = new ObjectSlot<>( "Hi" );
		assertEquals( "Hi", slot.toString() );
	}

	@Test
	public void toString_null() throws Exception {
		ObjectSlot<String> slot = new ObjectSlot<>();
		assertEquals( "null", slot.toString() );
	}



	@Test
	public void waitForValue() throws Exception {
		AtomicLong value_received_time = new AtomicLong( 0 );
		AtomicReference<String> value_received = new AtomicReference<>();
		CountDownLatch value_received_latch = new CountDownLatch( 1 );

		ObjectSlot<String> slot = new ObjectSlot<>();

		SharedThreadPool.INSTANCE.execute( () -> {
			try {
				String value = slot.waitForValue();
				value_received_time.set( System.currentTimeMillis() );
				value_received.set( value );
			}
			catch ( InterruptedException e ) {
				e.printStackTrace();
			}
			finally {
				value_received_latch.countDown();
			}
		} );

		long start = System.currentTimeMillis();

		SharedThreadPool.INSTANCE.schedule( () -> slot.set( "Hi" ), 2, TimeUnit.SECONDS );

		boolean done = value_received_latch.await( 4, TimeUnit.SECONDS );
		assertTrue( done );

		assertEquals( "Hi", value_received.get() );

		long received_time = value_received_time.get();
		assertNotEquals( 0, received_time );

		long duration = received_time - start;
		assertTrue( "Received too fast: " + duration, duration >= 2000 );
		assertTrue( "Received too slow: " + duration, duration < 3000 );
	}

	@Test
	public void waitForValueSpecific_timeoutImmediate() throws Exception {
		ObjectSlot<String> slot = new ObjectSlot<>( "Hi" );

		long start = System.currentTimeMillis();
		String value = slot.waitForValue( 1000 );
		long duration = System.currentTimeMillis() - start;

		assertEquals( "Hi", value );
		assertTrue( "Received too slow: " + duration, duration < 500 );
	}

	@Test
	public void waitForValue_timeoutSuccess() throws Exception {
		AtomicLong value_received_time = new AtomicLong( 0 );
		AtomicReference<String> value_received = new AtomicReference<>( "NOT SET" );
		CountDownLatch value_received_latch = new CountDownLatch( 1 );

		ObjectSlot<String> slot = new ObjectSlot<>();

		SharedThreadPool.INSTANCE.execute( () -> {
			try {
				String value = slot.waitForValue( 2000 );
				value_received_time.set( System.currentTimeMillis() );
				value_received.set( value );
			}
			catch ( InterruptedException e ) {
				e.printStackTrace();
			}
			finally {
				value_received_latch.countDown();
			}
		} );

		long start = System.currentTimeMillis();

		SharedThreadPool.INSTANCE.schedule( () -> slot.set( "Hi" ), 1, TimeUnit.SECONDS );

		boolean done = value_received_latch.await( 3, TimeUnit.SECONDS );
		assertTrue( done );

		assertEquals( "Hi", value_received.get() );

		long received_time = value_received_time.get();
		assertNotEquals( 0, received_time );

		long duration = received_time - start;
		assertTrue( "Received too fast: " + duration, duration >= 1000 );
		assertTrue( "Received too slow: " + duration, duration < 2000 );
	}

	@Test
	public void waitForValue_timeoutFail() throws Exception {
		AtomicLong value_received_time = new AtomicLong( 0 );
		AtomicReference<String> value_received = new AtomicReference<>( "NOT SET" );
		CountDownLatch value_received_latch = new CountDownLatch( 1 );

		ObjectSlot<String> slot = new ObjectSlot<>();

		SharedThreadPool.INSTANCE.execute( () -> {
			try {
				String value = slot.waitForValue( 1000 );
				value_received_time.set( System.currentTimeMillis() );
				value_received.set( value );
			}
			catch ( InterruptedException e ) {
				e.printStackTrace();
			}
			finally {
				value_received_latch.countDown();
			}
		} );

		long start = System.currentTimeMillis();

		boolean done = value_received_latch.await( 3, TimeUnit.SECONDS );
		assertTrue( done );

		assertNull( value_received.get() );

		long received_time = value_received_time.get();
		assertNotEquals( 0, received_time );

		long duration = received_time - start;
		assertTrue( "Received too fast: " + duration, duration >= 1000 );
		assertTrue( "Received too slow: " + duration, duration < 2000 );
	}

	@Test
	public void waitForValueSpecific_immediateNull() throws Exception {
		ObjectSlot<String> slot = new ObjectSlot<>();

		long start = System.currentTimeMillis();
		boolean success = slot.waitForValue( null, 1000 );
		long duration = System.currentTimeMillis() - start;

		assertTrue( success );
		assertTrue( "Received too slow: " + duration, duration < 500 );
	}

	@Test
	public void waitForValueSpecific_immediateNonnull() throws Exception {
		ObjectSlot<String> slot = new ObjectSlot<>( "Hi" );

		long start = System.currentTimeMillis();
		boolean success = slot.waitForValue( "Hi", 1000 );
		long duration = System.currentTimeMillis() - start;

		assertTrue( success );
		assertTrue( "Received too slow: " + duration, duration < 500 );
	}

	@Test
	public void waitForValueSpecific_wait() throws Exception {
		AtomicLong value_received_time = new AtomicLong( 0 );
		AtomicBoolean success = new AtomicBoolean( false );
		CountDownLatch value_received_latch = new CountDownLatch( 1 );

		ObjectSlot<String> slot = new ObjectSlot<>();

		SharedThreadPool.INSTANCE.execute( () -> {
			try {
				success.set(
					slot.waitForValue( "Bye", 3000 ) );

				value_received_time.set( System.currentTimeMillis() );
			}
			catch ( InterruptedException e ) {
				e.printStackTrace();
			}
			finally {
				value_received_latch.countDown();
			}
		} );

		long start = System.currentTimeMillis();

		SharedThreadPool.INSTANCE.schedule( () -> slot.set( "Hi" ), 1, TimeUnit.SECONDS );
		SharedThreadPool.INSTANCE.schedule( () -> slot.set( "Bye" ), 2, TimeUnit.SECONDS );

		boolean done = value_received_latch.await( 4, TimeUnit.SECONDS );
		assertTrue( done );

		assertTrue( success.get() );

		long received_time = value_received_time.get();
		assertNotEquals( 0, received_time );

		long duration = received_time - start;
		assertTrue( "Received too fast: " + duration, duration >= 2000 );
		assertTrue( "Received too slow: " + duration, duration < 3000 );
	}

	@Test
	public void waitForValueSpecific_timeout() throws Exception {
		AtomicLong released_time = new AtomicLong( 0 );
		AtomicReference<Boolean> success = new AtomicReference<>();
		CountDownLatch value_received_latch = new CountDownLatch( 1 );

		ObjectSlot<String> slot = new ObjectSlot<>();

		SharedThreadPool.INSTANCE.execute( () -> {
			try {
				success.set( Boolean.valueOf(
					slot.waitForValue( "Bye", 3000 ) ) );
				released_time.set( System.currentTimeMillis() );
			}
			catch ( InterruptedException e ) {
				e.printStackTrace();
			}
			finally {
				value_received_latch.countDown();
			}
		} );

		long start = System.currentTimeMillis();

		SharedThreadPool.INSTANCE.schedule( () -> slot.set( "Hi" ), 1, TimeUnit.SECONDS );
		SharedThreadPool.INSTANCE.schedule( () -> slot.set( "Hola" ), 2, TimeUnit.SECONDS );

		boolean done = value_received_latch.await( 4, TimeUnit.SECONDS );
		assertTrue( done );

		assertEquals( Boolean.FALSE, success.get() );

		long received_time = released_time.get();
		assertNotEquals( 0, received_time );

		long duration = received_time - start;
		assertTrue( "Received too fast: " + duration, duration >= 3000 );
		assertTrue( "Received too slow: " + duration, duration < 4000 );
	}




	@Test
	public void waitForDifferentValueSpecific_immediateNull() throws Exception {
		ObjectSlot<String> slot = new ObjectSlot<>();

		long start = System.currentTimeMillis();
		String value = slot.waitForDifferentValue( "Hi", 1000 );
		long duration = System.currentTimeMillis() - start;

		assertNull( value );
		assertTrue( "Received too slow: " + duration, duration < 500 );
	}

	@Test
	public void waitForDifferentValueSpecific_immediateNonnull() throws Exception {
		ObjectSlot<String> slot = new ObjectSlot<>( "Hi" );

		long start = System.currentTimeMillis();
		String value = slot.waitForDifferentValue( "Bye", 1000 );
		long duration = System.currentTimeMillis() - start;

		assertEquals( "Hi", value );
		assertTrue( "Received too slow: " + duration, duration < 500 );
	}

	@Test
	public void waitForDifferentValueSpecific_immediateNonnull2() throws Exception {
		ObjectSlot<String> slot = new ObjectSlot<>( "Hi" );

		long start = System.currentTimeMillis();
		String value = slot.waitForDifferentValue( null, 1000 );
		long duration = System.currentTimeMillis() - start;

		assertEquals( "Hi", value );
		assertTrue( "Received too slow: " + duration, duration < 500 );
	}

	@Test
	public void waitForDifferentValueSpecific_wait() throws Exception {
		AtomicLong value_received_time = new AtomicLong( 0 );
		AtomicReference<String> value_received = new AtomicReference<>();
		CountDownLatch value_received_latch = new CountDownLatch( 1 );

		ObjectSlot<String> slot = new ObjectSlot<>( "Hi" );

		SharedThreadPool.INSTANCE.execute( () -> {
			try {
				value_received.set(
					slot.waitForDifferentValue( "Hi", 2000 ) );

				value_received_time.set( System.currentTimeMillis() );
			}
			catch ( InterruptedException e ) {
				e.printStackTrace();
			}
			finally {
				value_received_latch.countDown();
			}
		} );

		long start = System.currentTimeMillis();

		SharedThreadPool.INSTANCE.schedule( () -> slot.set( "Bye" ), 1, TimeUnit.SECONDS );

		boolean done = value_received_latch.await( 3, TimeUnit.SECONDS );
		assertTrue( done );

		assertEquals( "Bye", value_received.get() );

		long received_time = value_received_time.get();
		assertNotEquals( 0, received_time );

		long duration = received_time - start;
		assertTrue( "Received too fast: " + duration, duration >= 1000 );
		assertTrue( "Received too slow: " + duration, duration < 2000 );
	}

	@Test
	public void waitForDifferentValueSpecific_timeout() throws Exception {
		AtomicLong released_time = new AtomicLong( 0 );
		AtomicReference<String> value_received = new AtomicReference<>();
		CountDownLatch value_received_latch = new CountDownLatch( 1 );

		ObjectSlot<String> slot = new ObjectSlot<>( "Hi" );

		SharedThreadPool.INSTANCE.execute( () -> {
			try {
				value_received.set(
					slot.waitForDifferentValue( "Hi", 2000 ) );
				released_time.set( System.currentTimeMillis() );
			}
			catch ( InterruptedException e ) {
				e.printStackTrace();
			}
			finally {
				value_received_latch.countDown();
			}
		} );

		long start = System.currentTimeMillis();

		SharedThreadPool.INSTANCE.schedule( () -> slot.set( "Hi" ), 1, TimeUnit.SECONDS );

		boolean done = value_received_latch.await( 4, TimeUnit.SECONDS );
		assertTrue( done );

		assertEquals( "Hi", value_received.get() );

		long received_time = released_time.get();
		assertNotEquals( 0, received_time );

		long duration = received_time - start;
		assertTrue( "Received too fast: " + duration, duration >= 2000 );
		assertTrue( "Received too slow: " + duration, duration < 3000 );
	}



	@Test
	public void consumer() throws Exception {
		// Ok, this test exists just because I can. No real reason other than that.
		List<ObjectSlot<String>> slots = new ArrayList<>( 100 );
		for( int i = 0; i < 100; i++ ) {
			ObjectSlot<String> slot = new ObjectSlot<>();
			slots.add( slot );

			if ( i != 0 ) {
				slots.get( i - 1 ).consumeValue( slot );
			}
		}

		slots.get( 0 ).set( "Pass the message" );

		assertEquals( "Pass the message", slots.get( slots.size() - 1 ).get() );

		slots.forEach( slot -> assertEquals( "Pass the message", slot.get() ) );
	}
}