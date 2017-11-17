package com.logicartisan.common.core.io;

import org.junit.Test;

import static org.junit.Assert.*;


/**
 *
 */
public class TestIOKitTest {
	@Test
	public void serializeDeserialize() throws Exception {
		assertEquals( "Foo", TestIOKit.deserialize( TestIOKit.serialize( "Foo" ) ) );
	}
}