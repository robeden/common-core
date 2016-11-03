/*
 * Copyright(c) 2005, StarLight Systems
 * All rights reserved.
 */
package com.logicartisan.common.core;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;


/**
 *
 */
public class Pair<A, B> implements Externalizable {
	static final long serialVersionUID = 0L;
	
	public static <A, B> Pair<A, B> create( A one, B two ) {
		return new Pair<A, B>( one, two );
	}


	private A one;
	private B two;

	public Pair( A one, B two ) {
		this.one = one;
		this.two = two;
	}

	public Pair() {
	}


	public A getOne() {
		return one;
	}

	public void setOne( A one ) {
		this.one = one;
	}

	public B getTwo() {
		return two;
	}

	public void setTwo( B two ) {
		this.two = two;
	}


	public void set( A one, B two ) {
		this.one = one;
		this.two = two;
	}


	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder( "{" );
		buf.append( String.valueOf( one ) );
		buf.append( "," );
		buf.append( String.valueOf( two ) );
		buf.append( "}" );
		return buf.toString();
	}

	@Override
	public boolean equals( Object o ) {
		if ( this == o ) return true;
		if ( o == null || getClass() != o.getClass() ) return false;

		Pair pair = ( Pair ) o;

		if ( one != null ? !one.equals( pair.one ) : pair.one != null ) return false;
		if ( two != null ? !two.equals( pair.two ) : pair.two != null ) return false;

		return true;
	}

	@Override
	public int hashCode() {
		int result = one != null ? one.hashCode() : 0;
		result = 31 * result + ( two != null ? two.hashCode() : 0 );
		return result;
	}


	@SuppressWarnings( { "unchecked" } )
	@Override
	public void readExternal( ObjectInput in )
		throws IOException, ClassNotFoundException {

		// VERSION
		in.readByte();

		// ONE
		one = ( A ) in.readObject();

		// TWO
		two = ( B ) in.readObject();
	}

	@Override
	public void writeExternal( ObjectOutput out ) throws IOException {
		// VERSION
		out.writeByte( 0 );

		// ONE
		out.writeObject( one );

		// TWO
		out.writeObject( two );
	}
}
