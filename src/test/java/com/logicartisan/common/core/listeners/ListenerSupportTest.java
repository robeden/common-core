package com.logicartisan.common.core.listeners;

import com.logicartisan.common.core.thread.ObjectSlot;
import com.logicartisan.common.core.thread.ThreadKit;
import org.junit.Test;
import org.mockito.Mockito;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 *
 */
public class ListenerSupportTest {
	@Test
    public void testSimpleSynchronous() {
        ListenerSupport<Runnable,?> listeners =
	        ListenerSupport.forType( Runnable.class ).build();

	    // Single listener
	    Runnable mock1 = Mockito.mock( Runnable.class );
	    listeners.add( mock1 );
	    listeners.dispatch().run();
	    verify( mock1 ).run();
	    
	    // Three listeners
	    reset( mock1 );
	    Runnable mock2 = Mockito.mock( Runnable.class );
	    Runnable mock3 = Mockito.mock( Runnable.class );
	    listeners.add( mock2 );
	    listeners.add( mock3 );
	    listeners.dispatch().run();

	    verify( mock1 ).run();
	    verify( mock2 ).run();
	    verify( mock3 ).run();
    }


	@Test
	public void testMultipleAdds() {
        ListenerSupport<Runnable,?> listeners =
	        ListenerSupport.forType( Runnable.class ).build();

	    // Single listener
	    Runnable mock1 = Mockito.mock( Runnable.class );

		// Add multiple times
	    listeners.add( mock1 );
	    listeners.add( mock1 );
	    listeners.add( mock1 );
	    listeners.add( mock1 );
	    listeners.add( mock1 );

	    listeners.dispatch().run();
	    verify( mock1 ).run();
	}


	@Test
	public void testDeliveryErrors() throws Throwable {
		final AtomicReference<Throwable> error_message = new AtomicReference<>();
		final AtomicReference<ListenerSupport> ls_slot = new AtomicReference<>();

		final ObjectSlot<Integer> error_handler_state = new ObjectSlot<>( 0 );
		final ObjectSlot<Boolean> error_handler_last_removed_called =
			new ObjectSlot<>( Boolean.FALSE );

		MessageDeliveryErrorHandler<Runnable> test_handler =
			new MessageDeliveryErrorHandler<Runnable>() {
				@Override
				public ErrorResponse deliveryError( @Nonnull Runnable listener,
					@Nonnull Throwable throwable, int overall_error_count,
					int overall_success_count, int consecutive_errors, boolean fatal ) {

					final int state = error_handler_state.get();

					ErrorResponse response;
					try {
						switch( state ) {
							case 0:
								assertEquals( 1, overall_error_count );
								assertEquals( 1, overall_success_count );
								assertEquals( 1, consecutive_errors );
								assertEquals( false, fatal );
								response = ErrorResponse.RETRY_MESSAGE;
								break;
							case 1:
								assertEquals( 2, overall_error_count );
								assertEquals( 1, overall_success_count );
								assertEquals( 2, consecutive_errors );
								assertEquals( false, fatal );
								response = ErrorResponse.REMOVE_LISTENER;
								break;
							default:
								fail( "Unexpected state: " + state );
								response = null;
								break;
						}

					}
					catch( Throwable t ) {
						error_message.set( t );
						return null;
					}

					error_handler_state.set( state + 1 );
					return response;
				}

				@Override
				public ErrorResponse excessiveBacklog(Runnable listener, int backlog_size,
					int consecutive_backlog_errors) {

					throw new AssertionError( "Unexpected call" );
				}

				@Override
				public void lastListenerRemoved() {
					try {
						assertEquals( 2, error_handler_state.get().intValue() );
						assertFalse( ls_slot.get().hasListeners() );
						error_handler_last_removed_called.set( Boolean.TRUE );
					}
					catch( Throwable t ) {
						error_message.set( t );
					}
				}
			};

        ListenerSupport<Runnable,?> listeners =
	        ListenerSupport.forType( Runnable.class )
	            .executor( Runnable::run )
	            .errorHandler( test_handler )
	            .build();
		ls_slot.set( listeners );


		final ObjectSlot<Integer> runnable_call_count =
			new ObjectSlot<>( 0 );
		Runnable test_runnable = () -> {
			int value = runnable_call_count.get();
			runnable_call_count.set( value + 1 );
			if ( value != 0 ) {
				throw new UnsupportedOperationException();
			}
		};

		listeners.add( test_runnable );


		// First call, should work
		listeners.dispatch().run();
		assertTrue( runnable_call_count.waitForValue( 1, 1000L ) );
		assertEquals( Integer.valueOf( 0 ), error_handler_state.get() );
		assertEquals( Boolean.FALSE, error_handler_last_removed_called.get() );

		// Second call, shouldn't work
		listeners.dispatch().run();
		assertTrue( error_handler_last_removed_called.waitForValue( Boolean.TRUE, 2000L ) );
		assertEquals( Integer.valueOf( 2 ), error_handler_state.get() );
		assertEquals( Integer.valueOf( 3 ), runnable_call_count.get() );

		Throwable t = error_message.get();
		if ( t != null ) throw t;
	}


//	@Ignore( "This isn't working right on TeamCity" )
	@Test
	public void testBacklogEviction() throws InterruptedException {
		final CountDownLatch complete_latch = new CountDownLatch( 1 );
		try {
			final CountDownLatch running_latch = new CountDownLatch( 1 );

			Runnable good_listener = Mockito.mock( Runnable.class );

			Runnable evil_listener = () -> {
				System.out.println( "Evil listener called" );
				running_latch.countDown();

				try {
					complete_latch.await();
				}
				catch ( InterruptedException e ) {
					// ignore
				}
			};

			//noinspection unchecked
			MessageDeliveryErrorHandler<Runnable> mock_handler =
				Mockito.mock( MessageDeliveryErrorHandler.class, Mockito.withSettings() );
			Mockito.when( mock_handler.excessiveBacklog( evil_listener, 10, 1 ) )
				.thenReturn( MessageDeliveryErrorHandler.ErrorResponse.REMOVE_LISTENER );

			ListenerSupport<Runnable,?> listeners =
				ListenerSupport.forType( Runnable.class )
				.asynchronous()
				.errorHandler( mock_handler )
				.maxBacklog( 10 )
				.build();
			listeners.add( good_listener );
			listeners.add( evil_listener );

			// First message will be run by the listener
			System.out.println( "Initial run call..." );
			listeners.dispatch().run();

			running_latch.await();      // Wait for the first message to be dispatched

			// 10 messages should fill the queue
			for( int i = 0; i < 10; i++ ) {
				// If the mock is going to slow and the test is going too fast, the
				// backlog can still be overflowed for the good listener
				if ( i != 0 ) ThreadKit.sleep( 10 );

				System.out.println( "Run loop iteration " + i + "..." );
				listeners.dispatch().run();
			}

			assertTrue( listeners.hasListeners() );

			// 12th message should evict the listener
			System.out.printf( "Final run..." );
			listeners.dispatch().run();

			verify( mock_handler, Mockito.timeout( 2000 ) )
				.excessiveBacklog( evil_listener, 10, 1 );

			verify( good_listener, Mockito.timeout( 2000 ).times( 12 ) ).run();

			listeners.remove( good_listener );

			assertFalse( listeners.hasListeners() );
		}
		finally {
			complete_latch.countDown();
		}
	}



	@Test
	public void testDelayAsync() {
		ListenerSupport<Runnable,?> listeners = ListenerSupport.forType( Runnable.class )
			.asynchronous()
			.delay( 5, TimeUnit.SECONDS )
			.build();

		List<Long> call_times = Collections.synchronizedList( new ArrayList<>() );
		listeners.add( () -> call_times.add( System.currentTimeMillis() ) );

		long call1_time = System.currentTimeMillis();
		listeners.dispatch().run(); // Lands at T + 5

		ThreadKit.sleep( 2, TimeUnit.SECONDS );         // T + 2
		assertEquals( "Expected empty: " + call_times, 0, call_times.size() );
		long call2_time = System.currentTimeMillis();
		listeners.dispatch().run(); // Lands at T + 7


		ThreadKit.sleep( 1, TimeUnit.SECONDS );         // T + 3
		assertEquals( "Expected empty: " + call_times, 0, call_times.size() );
		long call3_time = System.currentTimeMillis();
		listeners.dispatch().run(); // Lands at T + 8


		ThreadKit.sleep( 3, TimeUnit.SECONDS );         // T + 6
		assertEquals( "Expected one time: " + call_times, 1, call_times.size() );
		long call4_time = System.currentTimeMillis();
		listeners.dispatch().run(); // Lands at T + 11

		ThreadKit.sleep( 4, TimeUnit.SECONDS );         // T + 10
		assertEquals( "Expected three times: " + call_times, 3, call_times.size() );

		ThreadKit.sleep( 1, TimeUnit.SECONDS );
		for( int i = 0; i < 10; i++ ) {
			if ( call_times.size() == 4 ) break;
			ThreadKit.sleep( 200 );
		}
		assertEquals( "Expected four times: " + call_times, 4, call_times.size() );


		assertTimeRange( call_times.get( 0 ), call1_time + 5000, call1_time + 6000 );
		assertTimeRange( call_times.get( 1 ), call2_time + 5000, call2_time + 6000 );
		assertTimeRange( call_times.get( 2 ), call3_time + 5000, call3_time + 6000 );
		assertTimeRange( call_times.get( 3 ), call4_time + 5000, call4_time + 6000 );
	}

	@Test
	public void testDelaySync() {
		ListenerSupport<Runnable,?> listeners = ListenerSupport.forType( Runnable.class )
			.delay( 5, TimeUnit.SECONDS )
			.build();

		List<Long> call_times = Collections.synchronizedList( new ArrayList<>() );
		listeners.add( () -> call_times.add( System.currentTimeMillis() ) );

		long call1_time = System.currentTimeMillis();
		listeners.dispatch().run();
		assertEquals( "Expected one time: " + call_times, 1, call_times.size() );

		long call2_time = System.currentTimeMillis();
		assertTimeRange( call2_time, call1_time + 5000, call1_time + 6000 );
		listeners.dispatch().run();
		assertEquals( "Expected two times: " + call_times, 2, call_times.size() );


		assertTimeRange( call_times.get( 0 ), call1_time + 5000, call1_time + 6000 );
		assertTimeRange( call_times.get( 1 ), call2_time + 5000, call2_time + 6000 );
	}


	private void assertTimeRange( long time, long greater_than, long less_than ) {
		assertTrue( time + " is outside of time range: " + greater_than + " - " +
			less_than, time >= greater_than && time <= less_than );
	}
}
