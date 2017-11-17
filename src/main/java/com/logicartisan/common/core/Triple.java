/*
 * Copyright (c) 2009 Rob Eden.
 * All Rights Reserved.
 */
package com.logicartisan.common.core;

import javax.annotation.Nullable;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Optional;


/**
 *
 */
public class Triple<A, B, C> {
	/**
	 * Synonym for {@link Triple#Triple(Object, Object, Object)}  new Triple(one,two,three)}.
	 */
	public static <A, B, C> Triple<A, B, C> create( A one, B two, C three ) {
		return new Triple<>( one, two, three );
	}


	private final A one;
	private final B two;
	private final C three;

	@SuppressWarnings( "WeakerAccess" )
	public Triple( @Nullable A one, @Nullable B two, @Nullable C three ) {
		this.one = one;
		this.two = two;
		this.three = three;
	}


	public @Nullable A getOne() {
		return one;
	}

	public Optional<A> getOneSafe() {
		return Optional.ofNullable( one );
	}


	public @Nullable B getTwo() {
		return two;
	}

	public Optional<B> getTwoSafe() {
		return Optional.ofNullable( two );
	}


	public @Nullable C getThree() {
		return three;
	}

	public Optional<C> getThreeSafe() {
		return Optional.ofNullable( three );
	}


	@Override
	public String toString() {
		return "{" + String.valueOf( one ) +
			"," +
			String.valueOf( two ) +
			"," +
			String.valueOf( three ) +
			"}";
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
}
