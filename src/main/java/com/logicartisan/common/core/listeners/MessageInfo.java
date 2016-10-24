/*
 * Copyright(c) 2002-2010, Rob Eden
 * All rights reserved.
 */

package com.logicartisan.common.core.listeners;

import java.lang.reflect.Method;


/**
 *
 */
class MessageInfo {
	private final Method method;
	private final Object[] args;

	MessageInfo( Method method, Object[] args ) {
		this.method = method;
		this.args = args;
	}


	public Method getMethod() {
		return method;
	}

	public Object[] getArgs() {
		return args;
	}
}
