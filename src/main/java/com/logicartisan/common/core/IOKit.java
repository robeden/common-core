/*
 * Copyright(c) 2002-2016, Rob Eden
 * All rights reserved.
 */
package com.logicartisan.common.core;

import com.logicartisan.common.core.thread.ThreadKit;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.text.MessageFormat;


/**
 * Useful static functions for dealing with I/O.
 */
public class IOKit {
	/**
	 * This can be used as a hint to {@link #isBeingDeserialized()} that the current
	 * thread is performing deserialization. Setting this to true will indicate that
	 * deserialization is being performed and will allow the method to avoid creating
	 * expensive stack traces. Care should be taken to ensure this is never set
	 * incorrectly or the results of {@link #isBeingDeserialized()} will be incorrect.
	 * If this is not used, the results will still be correct (so don't use it unless
	 * you're certain of what you're doing), but the performance will be worse.
	 */
	public static final ThreadLocal<Boolean> DESERIALIZATION_HINT =
		new ThreadLocal<Boolean>();


	private static final boolean XSTREAM_SUPPORTED;
	static {
		boolean supported = false;
		try {
			Class.forName( "com.thoughtworks.xstream.XStream" );
			supported = true;
		}
		catch( Throwable t ) {
			// ignore
		}
		XSTREAM_SUPPORTED = supported;
	}


	/**
	 * Close a Closable, dealing with nulls.
	 *
	 * @return  True if the target closed without error. If the argument was null, that
	 *          also is treated as error-free.
	 */
	public static boolean close( @Nullable Closeable closeable ) {
		if ( closeable == null ) return true;
		try {
			closeable.close();
			return true;
		}
		catch ( IOException ex ) {
			return false;
		}
	}


	/**
	 * Close an ObjectInput, dealing with nulls.
	 *
	 * @return  True if the target closed without error. If the argument was null, that
	 *          also is treated as error-free.
	 */
	public static boolean close( ObjectInput input ) {
		if ( input == null ) return true;
		try {
			input.close();
			return true;
		}
		catch ( IOException ex ) {
			return false;
		}
	}

	/**
	 * Close an ObjectOutput, dealing with nulls.
	 *
	 * @return  True if the target closed without error. If the argument was null, that
	 *          also is treated as error-free.
	 */
	public static boolean close( ObjectOutput output ) {
		if ( output == null ) return true;
		try {
			output.close();
			return true;
		}
		catch ( IOException ex ) {
			return false;
		}
	}

	/**
	 * Serialize an object and return the byte array (suitable for storage).
	 */
	public static byte[] serialize( Object obj ) throws IOException {
		ByteArrayOutputStream b_out = null;
		ObjectOutputStream o_out = null;
		try {
			b_out = new ByteArrayOutputStream();
			o_out = new ObjectOutputStream( b_out );
			o_out.writeObject( obj );
			return b_out.toByteArray();
		}
		finally {
			close( ( Closeable ) o_out );
			close( b_out );
		}
	}

	/**
	 * Serialize an object and return the byte array (suitable for storage).
	 */
	public static Object deserialize( byte[] data )
		throws IOException, ClassNotFoundException {

		Boolean previous_value = IOKit.DESERIALIZATION_HINT.get();
		IOKit.DESERIALIZATION_HINT.set( Boolean.TRUE );
		try {
			ByteArrayInputStream b_in = null;
			ObjectInputStream o_in = null;
			try {
				b_in = new ByteArrayInputStream( data );
				o_in = new ObjectInputStream( b_in );
				return o_in.readObject();
			}
			finally {
				close( ( Closeable ) o_in );
				close( b_in );
			}
		}
		finally {
			if ( previous_value == null ) IOKit.DESERIALIZATION_HINT.remove();
			else IOKit.DESERIALIZATION_HINT.set( previous_value );
		}
	}


	/**
	 * Can be called to check to see if the method was called from within a serialization
	 * call. Example:
	 * <pre>
	 * public class Foo implements Externalizable {
	 *     // FOR SERIALIZATION ONLY!!!
	 *     public Foo() {
	 *         assert IOKit.isBeingDeserialized();
	 *     }
	 * }
	 * </pre>
	 */
	public static boolean isBeingDeserialized() {
		Boolean hint = DESERIALIZATION_HINT.get();
		if ( hint != null ) return hint.booleanValue();

		// Step backwards through the stack and look for readObject() or readExternal()
		StackTraceElement[] stack = new Throwable().getStackTrace();
		for ( int i = 0; i < stack.length; i++ ) {
			StackTraceElement element = stack[ i ];

			String method_name = element.getMethodName();
			String class_name = element.getClassName();

			// ObjectInputStream
			if ( class_name.equals( "java.io.ObjectInputStream" ) ) return true;

			// XStream
			if ( XSTREAM_SUPPORTED ) {
				if ( class_name.equals( "com.thoughtworks.xstream.XStream" ) &&
					method_name.equals( "fromXML" ) ) return true;
			}


			// Externalization short-circuit
			// Rather than:
			//         foo = ( Foo ) in.readObject()
			// Doing:
			//         foo = new Foo();
			//         foo.readExternal( in );
			//
			// In this case, the relevant frame is always the third (index 2):
			//   0: IOKit.isBeingDeserialized()   (this method)
			//   1 : <init>  (constructor on object)
			//   2 : readExternal()
			//
			// Note: this assumes that this always happens directly in the readExternal()
			// method. Doing the following would throw it off:
			//    public readExternal(ObjectInput in) {
			//       readSomePortion(in);
			//    }
			//    public readSomePortion(ObjectInput in) {
			//       foo = new Foo();
			//       foo.readExternal(in);
			//    }
			// ... but I think this is a reasonable limitation.
			if ( i == 2 && method_name.equals( "readExternal" ) ) return true;
		}

		return false;
	}


	/**
	 * Copy the contents of one stream to another. Note this will NOT close the streams.
	 *
	 * @return      The number of bytes copied.
	 */
	public static long copy( InputStream input, OutputStream output ) throws IOException {
		byte[] buffer = new byte[10240];

		long total_written = 0;

		int read;
		while ( ( read = input.read( buffer ) ) >= 0 ) {
			if ( read == 0 ) {
				ThreadKit.sleep( 50 );
				continue;
			}

			output.write( buffer, 0, read );

			total_written += read;
		}

		return total_written;
	}

	/**
	 * Copy the contents of one stream to another. Note this will NOT close the streams.
	 *
	 * @return      The number of bytes copied.
	 */
	public static long copy( Reader input, Writer output ) throws IOException {
		char[] buffer = new char[10240];

		long total_written = 0;

		int read;
		while ( ( read = input.read( buffer ) ) >= 0 ) {
			if ( read == 0 ) {
				ThreadKit.sleep( 50 );
				continue;
			}

			output.write( buffer, 0, read );

			total_written += read;
		}

		return total_written;
	}

	/**
	 * Copy from one (blocking) channel to another. This method will technically work
	 * for non-blocking channels, but would be <strong>VERY</strong> inefficient and
	 * so is strongly discouraged.
	 * 
	 * @param input     The input channel, from which data will be read.
	 * @param output    The output channel, to which data will be written.
	 * @param buffer    The buffer to use for the copy, if provided. This may be null, in
	 *                  which case a temporary (64k) one will be created.
	 *
	 * @return      The number of bytes copied.
	 */
	public static long copy( ReadableByteChannel input, WritableByteChannel output,
		ByteBuffer buffer ) throws IOException {

		if ( buffer == null ) buffer = ByteBuffer.allocate( 1 << 16 );  // 64k
		else buffer.clear();

		long total_written = 0;

		int read;
		while( ( read = input.read( buffer ) ) != -1 ) {
			if ( read != 0 ) {
				buffer.flip();

				while ( buffer.hasRemaining() ) {
					output.write( buffer );
				}

				total_written += read;
			}
			
			buffer.clear();
		}

		return total_written;
	}


	/**
	 * Read a password from the system Console if available or System.in if it isn't.
	 *
	 * @param prompt        The String using MessageFormat syntax with the prompt that
	 *                      should be displayed. This is useful because a warning will
	 *                      be displayed if the password will be echoed to the terminal
	 *                      (if a Console was not available). The following is an example:
	 *                      "Enter password{0}: ". In this case, the following would be
	 *                      displayed if the password would be echoed:
	 *                      "Enter password (warning: password will be visible): ". If
	 *                      null is specified, no prompt will be displayed.
	 */
	public static char[] readPasswordFromConsole( String prompt ) throws IOException {
		Console console = System.console();
		if ( console != null ) {
			if ( prompt != null ) {
				System.out.print( MessageFormat.format( prompt, "" ) );
			}

			return console.readPassword();
		}

		if ( prompt != null ) {
			System.out.print(
				MessageFormat.format( prompt, " (warning: password will be visible)" ) );
		}

		BufferedReader in = new BufferedReader( new InputStreamReader( System.in ) );
		try {
			String line = in.readLine();
			if ( line == null ) return null;
			else return line.toCharArray();
		}
		catch( Exception ex ) {
			return null;
		}
		// NOTE: Don't close!
	}

	/**
	 * Read a line of text from the system Console if available or System.in if it isn't.
	 *
	 * @param prompt        The String using MessageFormat syntax with the prompt that
	 *                      should be displayed. If null is specified, no prompt will be
	 *                      displayed.
	 */
	public static String readFromConsole( String prompt ) throws IOException {
		if ( prompt != null ) System.out.print( prompt );

		Console console = System.console();
		if ( console != null ) {
			return console.readLine();
		}

		BufferedReader in = new BufferedReader( new InputStreamReader( System.in ) );
		try {
			String line = in.readLine();
			if ( line == null ) return null;
			else return line;
		}
		catch( IOException ex ) {
			throw ex;
		}
		catch( Exception ex ) {
			throw new IOException( ex );
		}
		// NOTE: Don't close!
	}


	/**
	 * Deletes a file or directory, recursively deleting all files in the directory
	 * first (if applicable). Use with caution (this is "rm -rf")!
	 */
	public static void deleteRecursive( File file ) throws IOException {
		if ( !Files.exists( file.toPath(), LinkOption.NOFOLLOW_LINKS ) ) return;

		if ( file.isDirectory() ) {
			File[] files = file.listFiles();
			if ( files != null ) {
				for( File child : files ) {
					deleteRecursive( child );
				}
			}
		}

		Files.delete( file.toPath() );
	}
}
