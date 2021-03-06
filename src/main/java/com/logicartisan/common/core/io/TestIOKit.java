package com.logicartisan.common.core.io;


import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.util.function.Consumer;

/**
 * IO functions geared toward testing.
 */
@SuppressWarnings( { "WeakerAccess", "unused" } )
public class TestIOKit {
	private static final int COLUMNS = 80;


	/**
	 * Write hex-encoded data to a file, optionally with a comment header.
	 *
	 * @see #readTestFile(Reader)
	 */
	public static void writeTestFile( Writer writer, @Nullable String comment,
		byte[] data ) throws IOException {

		try( PrintWriter out = new PrintWriter( writer ) ) {
			if ( comment != null ) {
				out.println( "#" );
				chop( comment, COLUMNS - 2, line -> {
					out.print( "# " );
					out.println( line );
				} );
				out.println( "#" );
			}

			StringBuilder buf = new StringBuilder();
			for( byte bite : data ) {
				int k = bite & 0xff;
				buf.append( Integer.toHexString( k >>> 4 ) );
				buf.append( Integer.toHexString( k & 0xf ) );
			}
			chop( buf.toString(), COLUMNS, out::println );
		}
	}

	/**
	 * Read hex-encoded data from a file.
	 *
	 * @see #writeTestFile(Writer, String, byte[])
	 */
	public static byte[] readTestFile( @Nonnull Reader reader ) throws IOException {
		StringBuilder buf = new StringBuilder();

		try( BufferedReader in = new BufferedReader( reader ) ) {
			String line;
			while ( ( line = in.readLine() ) != null ) {
				line = line.trim();

				if ( line.isEmpty() ) continue;
				if ( line.startsWith( "#" ) ) continue;

				buf.append( line );
			}
		}

		char[] chars = new char[ buf.length() ];
		buf.getChars( 0, buf.length(), chars, 0 );

		byte[] data = new byte[ buf.length() / 2 ];
		int d = 0;
		for( int i = 0; i < chars.length; i += 2 ) {
			data[ d ] = ( byte )
				( Integer.parseUnsignedInt( new String( chars, i, 2 ), 16 ) & 0xff );
			d++;
		}

		return data;
	}



	/**
	 * Serialize an object and return the byte array (suitable for storage).
	 */
	public static byte[] serialize( Object obj ) throws IOException {
		try ( ByteArrayOutputStream bout = new ByteArrayOutputStream();
			ObjectOutputStream out = new ObjectOutputStream( bout ) ) {

			out.writeObject( obj );
			return bout.toByteArray();
		}
	}

	/**
	 * Serialize an object and return the byte array (suitable for storage).
	 */
	public static Object deserialize( byte[] data )
		throws IOException, ClassNotFoundException {

		try ( ByteArrayInputStream bin = new ByteArrayInputStream( data );
			ObjectInputStream in = new ObjectInputStream( bin ) ) {

			return in.readObject();
		}
	}



	private static void chop( @Nonnull String string, int length,
		@Nonnull Consumer<String> handler ) {

		String remaining = string;
		while( remaining.length() > ( length ) ) {
			handler.accept( remaining.substring( 0, length ) );
			remaining = remaining.substring( length );
		}
		if ( !remaining.isEmpty() ) {
			handler.accept( remaining );
		}
	}
}
