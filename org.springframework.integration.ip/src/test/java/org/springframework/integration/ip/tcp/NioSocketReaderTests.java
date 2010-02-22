/*
 * Copyright 2002-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.integration.ip.tcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;

/**
 * @author Gary Russell
 *
 */
public class NioSocketReaderTests {

	private CountDownLatch latch = new CountDownLatch(1);
	/**
	 * Test method for {@link org.springframework.integration.ip.tcp.NioSocketReader}.
	 */
	@Test
	public void testReadLength() throws Exception {
		ServerSocketChannel server = ServerSocketChannel.open();
		server.configureBlocking(false);
		int port = 23456;
		server.socket().bind(new InetSocketAddress(port));
		final Selector selector = Selector.open();
		server.register(selector, SelectionKey.OP_ACCEPT);
		
		// Fire up the sender.
		Utils.testSendLength(port, latch);
		
		if(selector.select(10000) <= 0) {
			fail("Socket failed to connect");
		}
		Set<SelectionKey> keys = selector.selectedKeys();
		Iterator<SelectionKey> iterator = keys.iterator();
		SocketChannel channel = null;
		while (iterator.hasNext()) {
			SelectionKey key = iterator.next();
			iterator.remove();
			if (key.isAcceptable()) {
				channel = server.accept();
				channel.configureBlocking(false);
				channel.register(selector, SelectionKey.OP_READ);
			}
			else {
				fail("Unexpected key: " + key);
			}
		}
		NioSocketReader reader = new NioSocketReader(channel);
		int count = 0;
		while(selector.select(1000) > 0) {
			keys = selector.selectedKeys();
			iterator = keys.iterator();
			while (iterator.hasNext()) {
				SelectionKey key = iterator.next();
				iterator.remove();
				if (key.isReadable()) {
					assertEquals(channel, key.channel());
					if (reader.assembleData()) {
						assertEquals("Data", Utils.TEST_STRING + Utils.TEST_STRING, 
						         new String(reader.getAssembledData()));
						count++;
					}
					latch.countDown();
				}
				else {
					fail("Unexpected key: " + key);
				}
			}
		}
		assertEquals("Did not receive data", 2, count);
	}

	@Test
	public void testFragmented() throws Exception {
		ServerSocketChannel server = ServerSocketChannel.open();
		server.configureBlocking(false);
		int port = 23457;
		server.socket().bind(new InetSocketAddress(port));
		final Selector selector = Selector.open();
		server.register(selector, SelectionKey.OP_ACCEPT);
		
		// Fire up the sender.
		Utils.testSendFragmented(port);
		
		if(selector.select(10000) <= 0) {
			fail("Socket failed to connect");
		}
		Set<SelectionKey> keys = selector.selectedKeys();
		Iterator<SelectionKey> iterator = keys.iterator();
		SocketChannel channel = null;
		while (iterator.hasNext()) {
			SelectionKey key = iterator.next();
			iterator.remove();
			if (key.isAcceptable()) {
				channel = server.accept();
				channel.configureBlocking(false);
				channel.register(selector, SelectionKey.OP_READ);
			}
			else {
				fail("Unexpected key: " + key);
			}
		}
		NioSocketReader reader = new NioSocketReader(channel);
		boolean done = false;
		while(selector.select(1000) > 0) {
			keys = selector.selectedKeys();
			iterator = keys.iterator();
			while (iterator.hasNext()) {
				SelectionKey key = iterator.next();
				iterator.remove();
				if (key.isReadable()) {
					assertEquals(channel, key.channel());
					if (reader.assembleData()) {
						assertEquals("Data", "xx", 
						         new String(reader.getAssembledData()));
						done = true;
					}
					latch.countDown();
				}
				else {
					fail("Unexpected key: " + key);
				}
			}
		}
		assertTrue("Did not receive data", done);
	}
	
	/**
	 * Test method for {@link org.springframework.integration.ip.tcp.NioSocketReader}.
	 */
	@Test
	public void testReadStxEtx() throws Exception {
		ServerSocketChannel server = ServerSocketChannel.open();
		server.configureBlocking(false);
		int port = 23458;
		server.socket().bind(new InetSocketAddress(port));
		final Selector selector = Selector.open();
		server.register(selector, SelectionKey.OP_ACCEPT);
		
		// Fire up the sender.
		Utils.testSendStxEtx(port, latch);
		
		if(selector.select(10000) <= 0) {
			fail("Socket failed to connect");
		}
		Set<SelectionKey> keys = selector.selectedKeys();
		Iterator<SelectionKey> iterator = keys.iterator();
		SocketChannel channel = null;
		while (iterator.hasNext()) {
			SelectionKey key = iterator.next();
			iterator.remove();
			if (key.isAcceptable()) {
				channel = server.accept();
				channel.configureBlocking(false);
				channel.register(selector, SelectionKey.OP_READ);
			}
			else {
				fail("Unexpected key: " + key);
			}
		}
		NioSocketReader reader = new NioSocketReader(channel);
		reader.setMessageFormat(MessageFormats.FORMAT_STX_ETX);
		int count = 0;
		while(selector.select(1000) > 0) {
			keys = selector.selectedKeys();
			iterator = keys.iterator();
			while (iterator.hasNext()) {
				SelectionKey key = iterator.next();
				iterator.remove();
				if (key.isReadable()) {
					assertEquals(channel, key.channel());
					if (reader.assembleData()) {
						assertEquals("Data", Utils.TEST_STRING + Utils.TEST_STRING, 
						         new String(reader.getAssembledData()));
						count++;
					}
					latch.countDown();
				}
				else {
					fail("Unexpected key: " + key);
				}
			}
		}
		assertEquals("Did not receive data", 2, count);
	}

	/**
	 * Test method for {@link org.springframework.integration.ip.tcp.NioSocketReader}.
	 */
	@Test
	public void testReadCrLf() throws Exception {
		ServerSocketChannel server = ServerSocketChannel.open();
		server.configureBlocking(false);
		int port = 23459;
		server.socket().bind(new InetSocketAddress(port));
		final Selector selector = Selector.open();
		server.register(selector, SelectionKey.OP_ACCEPT);
		
		// Fire up the sender.
		Utils.testSendCrLf(port, latch);
		
		if(selector.select(10000) <= 0) {
			fail("Socket failed to connect");
		}
		Set<SelectionKey> keys = selector.selectedKeys();
		Iterator<SelectionKey> iterator = keys.iterator();
		SocketChannel channel = null;
		while (iterator.hasNext()) {
			SelectionKey key = iterator.next();
			iterator.remove();
			if (key.isAcceptable()) {
				channel = server.accept();
				channel.configureBlocking(false);
				channel.register(selector, SelectionKey.OP_READ);
			}
			else {
				fail("Unexpected key: " + key);
			}
		}
		NioSocketReader reader = new NioSocketReader(channel);
		reader.setMessageFormat(MessageFormats.FORMAT_CRLF);
		int count = 0;
		while(selector.select(1000) > 0) {
			keys = selector.selectedKeys();
			iterator = keys.iterator();
			while (iterator.hasNext()) {
				SelectionKey key = iterator.next();
				iterator.remove();
				if (key.isReadable()) {
					assertEquals(channel, key.channel());
					if (reader.assembleData()) {
						assertEquals("Data", Utils.TEST_STRING + Utils.TEST_STRING, 
						         new String(reader.getAssembledData()));
						count++;
					}
					latch.countDown();
				}
				else {
					fail("Unexpected key: " + key);
				}
			}
		}
		assertEquals("Did not receive data", 2, count);
	}

}