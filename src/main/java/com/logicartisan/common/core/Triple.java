/*
 * Copyright (c) 2009 Rob Eden.
 * All Rights Reserved.
 */
package com.logicartisan.common.core;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;


/**
 *
 */
public class Triple<A, B, C> implements Externalizable {
	static final long serialVersionUID = 0L;

	public static <A, B, C> Triple<A, B, C> create( A one, B two, C three ) {
		return new Triple<A, B, C>( one, two, three );
	}


	private A one;
	private B two;
	private C three;


	/**
	 * @deprecated Triple will be made immutable in 1.2.
	 */
	@Deprecated
	public Triple() {}

	public Triple( A one, B two, C three ) {
		this.one = one;
		this.two = two;
		this.three = three;
	}


	public A getOne() {
		return one;
	}

	/**
	 * @deprecated Triple will be made immutable in 1.2.
	 */
	@Deprecated
	public void setOne( A one ) {
		this.one = one;
	}


	public B getTwo() {
		return two;
	}

	/**
	 * @deprecated Triple will be made immutable in 1.2.
	 */
	@Deprecated
	public void setTwo( B two ) {
		this.two = two;
	}


	public C getThree() {
		return three;
	}

	/**
	 * @deprecated Triple will be made immutable in 1.2.
	 */
	@Deprecated
	public void setThree( C three ) {
		this.three = three;
	}


	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder( "{" );
		buf.append( String.valueOf( one ) );
		buf.append( "," );
		buf.append( String.valueOf( two ) );
		buf.append( "," );
		buf.append( String.valueOf( three ) );
		buf.append( "}" );
		return buf.toString();
	}


	@Override
	public boolean equals( Object o ) {
		if ( this == o ) return true;
		if ( o == null || getClass() != o.getClass() ) return false;

		Triple triple = ( Triple ) o;

		if ( one != null ? !one.equals( triple.one ) : triple.one != null ) return false;
		if ( three != null ? !three.equals( triple.three ) : triple.three != null )
			return false;
		if ( two != null ? !two.equals( triple.two ) : triple.two != null ) return false;

		return true;
	}

	@Override
	public int hashCode() {
		int result = one != null ? one.hashCode() : 0;
		result = 31 * result + ( two != null ? two.hashCode() : 0 );
		result = 31 * result + ( three != null ? three.hashCode() : 0 );
		return result;
	}


	@Override
	public void readExternal( ObjectInput in )
		throws IOException, ClassNotFoundException {

		// VERSION
		in.readByte();

		// ONE
		one = ( A ) in.readObject();

		// TWO
		two = ( B ) in.readObject();

		// THREE
		three = ( C ) in.readObject();
	}

	@Override
	public void writeExternal( ObjectOutput out ) throws IOException {
		// VERSION
		out.writeByte( 0 );

		// ONE
		out.writeObject( one );

		// TWO
		out.writeObject( two );

		// THREE
		out.writeObject( three );
	}
}
