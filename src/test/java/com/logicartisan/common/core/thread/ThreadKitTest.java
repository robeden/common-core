package com.logicartisan.common.core.thread;

import org.junit.Test;
import org.mockito.Mockito;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

import static org.junit.Assert.*;


/**
 *
 */
@SuppressWarnings( "Duplicates" )
public class ThreadKitTest {

	@Test
	public void testInLock_runnable() throws Exception {
		TestLock lock = new TestLock();

		assertFalse( lock.locked );
		ThreadKit.inLock( lock, () -> {
			assertTrue( lock.locked );
		} );
		assertFalse( lock.locked );
	}

	@Test
	public void testInLock_supplier() throws Exception {
		TestLock lock = new TestLock();


		assertFalse( lock.locked );
		String value = ThreadKit.inLock( lock, () -> {
			assertTrue( lock.locked );
			return "GOOD";
		} );
		assertFalse( lock.locked );
		assertEquals( "GOOD", value );
	}



	@Test
	public void testInReadLock_runnable() throws Exception {
		TestLock lock = new TestLock();
		ReadWriteLock rw_lock = Mockito.mock( ReadWriteLock.class );

		Mockito.when( rw_lock.readLock() ).thenReturn( lock );

		assertFalse( lock.locked );
		ThreadKit.inReadLock( rw_lock, () -> {
			assertTrue( lock.locked );
		} );
		assertFalse( lock.locked );
	}

	@Test
	public void testInReadLock_supplier() throws Exception {
		TestLock lock = new TestLock();
		ReadWriteLock rw_lock = Mockito.mock( ReadWriteLock.class );

		Mockito.when( rw_lock.readLock() ).thenReturn( lock );

		assertFalse( lock.locked );
		assertEquals( "GOOD", ThreadKit.inReadLock( rw_lock, () -> {
			assertTrue( lock.locked );
			return "GOOD";
		} ) );
		assertFalse( lock.locked );
	}



	@Test
	public void testInWriteLock_runnable() throws Exception {
		TestLock lock = new TestLock();
		ReadWriteLock rw_lock = Mockito.mock( ReadWriteLock.class );

		Mockito.when( rw_lock.writeLock() ).thenReturn( lock );

		assertFalse( lock.locked );
		ThreadKit.inWriteLock( rw_lock, () -> {
			assertTrue( lock.locked );
		} );
		assertFalse( lock.locked );
	}

	@Test
	public void testInWriteLock_supplier() throws Exception {
		TestLock lock = new TestLock();
		ReadWriteLock rw_lock = Mockito.mock( ReadWriteLock.class );

		Mockito.when( rw_lock.writeLock() ).thenReturn( lock );

		assertFalse( lock.locked );
		assertEquals( "GOOD", ThreadKit.inWriteLock( rw_lock, () -> {
			assertTrue( lock.locked );
			return "GOOD";
		} ) );
		assertFalse( lock.locked );
	}



	private static class TestLock implements Lock {
		volatile boolean locked = false;



		@Override
		public void lock() {
			locked = true;
		}

		@Override
		public void unlock() {
			locked = false;
		}



		@Override public void lockInterruptibly() throws InterruptedException {
			throw new UnsupportedOperationException();
		}

		@Override public boolean tryLock() {
			throw new UnsupportedOperationException();
		}

		@Override public boolean tryLock( long time, @Nonnull TimeUnit unit )
			throws InterruptedException {
			throw new UnsupportedOperationException();
		}

		@Override public @Nonnull
		Condition newCondition() {
			throw new UnsupportedOperationException();
		}
	}
}