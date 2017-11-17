/*
 * Copyright(c) 2005, StarLight Systems
 * All rights reserved.
 */
package com.logicartisan.common.core;

import javax.annotation.Nullable;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Optional;


/**
 * An immutable pair of objects.
 */
public class Pair<A, B> {
	/**
	 * Synonym for {@link Pair#Pair(Object, Object) new Pair(one,two)}.
	 */
	public static <A, B> Pair<A, B> create( A one, B two ) {
		return new Pair<>( one, two );
	}


	private final A one;
	private final B two;

	@SuppressWarnings( "WeakerAccess" )
	public Pair( @Nullable A one, @Nullable B two ) {
		this.one = one;
		this.two = two;
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


	@Override
	public String toString() {
		return "{" + String.valueOf( one ) +
			"," +
			String.valueOf( two ) +
			"}";
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
}
