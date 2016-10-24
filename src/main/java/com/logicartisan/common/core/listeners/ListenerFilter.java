/*
 * Copyright(c) 2002-2010, Rob Eden
 * All rights reserved.
 */

package com.logicartisan.common.core.listeners;

import java.lang.reflect.Method;


/**
 *
 */
public interface ListenerFilter<A> {
	public boolean callMatchesFilter( A attachment, Method method, Object[] args );
}
