/*
 * Copyright (c) 2012 Rob Eden.
 * All Rights Reserved.
 */

package com.logicartisan.common.core;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Random;


/**
 *
 */
public class IOKitTest extends TestCase {
	public void testChannelCopy() throws Exception {
		// Create some random data
		byte[] data = new byte[ 1024 * 1024 ];  // 1M
		Random rand = new Random();
		rand.nextBytes( data );


		// Try once with null provided buffer
		ByteArrayInputStream in = new ByteArrayInputStream( data );
		ReadableByteChannel source = Channels.newChannel( in );

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		WritableByteChannel sink = Channels.newChannel( out );
		
		IOKit.copy( source, sink, null );

		assertTrue( Arrays.equals( data, out.toByteArray() ) );


		// Try once with non-null provided buffer
		in = new ByteArrayInputStream( data );
		source = Channels.newChannel( in );

		out = new ByteArrayOutputStream();
		sink = Channels.newChannel( out );

		IOKit.copy( source, sink, ByteBuffer.allocate( 1024 * 128 ) );

		assertTrue( Arrays.equals( data, out.toByteArray() ) );
	}
}
