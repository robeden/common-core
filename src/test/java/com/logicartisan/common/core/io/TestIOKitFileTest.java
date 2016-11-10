package com.logicartisan.common.core.io;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.annotation.Nullable;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

/**
 *
 */
@RunWith( Parameterized.class )
public class TestIOKitFileTest {
	@Parameterized.Parameters( name="{0}")
	public static Collection<Integer> dataLengths() {
		return Arrays.asList( 1, 77, 78, 79, 80, 81, 1000, 10000, 100000 );
	}


	private final byte data[];

	public TestIOKitFileTest( int length ) {
		data = new byte[ length ];
		new Random().nextBytes( data );
	}


	@Test
	public void testWithComment() throws Exception {
		innerTest( "Test of data, length: " + data.length +
			"\n ... and a newline in a comment, because they're kind of hard" );
	}

	@Test
	public void testWithoutComment() throws Exception {
		innerTest( null );
	}


	private void innerTest( @Nullable String comment ) throws Exception {
		StringWriter writer = new StringWriter();
		TestIOKit.writeTestFile( writer, comment, data );
		String string = writer.toString();
//		System.out.println( "String:" );
//		System.out.println( string );

		StringReader reader = new StringReader( string );
		byte[] new_data = TestIOKit.readTestFile( reader );

		Assert.assertArrayEquals( data, new_data );
	}
}