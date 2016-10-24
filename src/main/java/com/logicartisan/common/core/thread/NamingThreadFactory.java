/*
 * Copyright(c) 2002-2010, Rob Eden
 * All rights reserved.
 */
package com.logicartisan.common.core.thread;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * A {@link ThreadFactory} that creates a thread with a name prefix and an incrementing
 * counter.
 */
public class NamingThreadFactory implements ThreadFactory {
	private static final AtomicInteger ID_COUNTER = new AtomicInteger( 0 );

	private final String name_prefix;
	private final boolean daemon;


	public NamingThreadFactory( String name_prefix ) {
		this( name_prefix, true );
	}

	public NamingThreadFactory( String name_prefix, boolean daemon ) {
		this.name_prefix = name_prefix;
		this.daemon = daemon;
	}


	public final Thread newThread( Runnable r ) {
		Thread t = createThread( r, name_prefix + ID_COUNTER.getAndIncrement() );
		t.setDaemon( daemon );
		return t;
	}


	protected Thread createThread( Runnable r, String name ) {
		return new Thread( r, name );
	}
}
