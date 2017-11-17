/*
 * Copyright(c) 2002-2010, Rob Eden
 * All rights reserved.
 */

package com.logicartisan.common.core.listeners;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;


/**
 *
 */
public interface ListenerFilter<A> {
	boolean callMatchesFilter( @Nullable A attachment, Method method, Object[] args );
}
