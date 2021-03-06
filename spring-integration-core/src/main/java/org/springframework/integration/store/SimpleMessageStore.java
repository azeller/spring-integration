/*
 * Copyright 2002-2009 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.springframework.integration.store;

import java.util.HashSet;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.integration.Message;
import org.springframework.integration.MessagingException;
import org.springframework.integration.util.UpperBound;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.util.Assert;

/**
 * Map-based implementation of {@link MessageStore} and {@link MessageGroupStore}. Enforces a maximum capacity for the
 * store.
 * 
 * @author Iwein Fuld
 * @author Mark Fisher
 * @author Dave Syer
 * 
 * @since 2.0
 */
@ManagedResource
public class SimpleMessageStore extends AbstractMessageGroupStore implements MessageStore, MessageGroupStore {

	private final ConcurrentMap<UUID, Message<?>> idToMessage;

	private final ConcurrentMap<Object, SimpleMessageGroup> groupIdToMessageGroup;

	private final UpperBound individualUpperBound;

	private final UpperBound groupUpperBound;

	/**
	 * Creates a SimpleMessageStore with a maximum size limited by the given capacity, or unlimited size if the given
	 * capacity is less than 1. The capacities are applied independently to messages stored via
	 * {@link #addMessage(Message)} and to those stored via {@link #addMessageToGroup(Object, Message)}. In both cases
	 * the capacity applies to the number of messages that can be stored, and once that limit is reached attempting to
	 * store another will result in an exception.
	 */
	public SimpleMessageStore(int individualCapacity, int groupCapacity) {
		this.idToMessage = new ConcurrentHashMap<UUID, Message<?>>();
		this.groupIdToMessageGroup = new ConcurrentHashMap<Object, SimpleMessageGroup>();
		this.individualUpperBound = new UpperBound(individualCapacity);
		this.groupUpperBound = new UpperBound(groupCapacity);
	}

	/**
	 * Creates a SimpleMessageStore with the same capacity for individual and grouped messages.
	 */
	public SimpleMessageStore(int capacity) {
		this(capacity, capacity);
	}

	/**
	 * Creates a SimpleMessageStore with unlimited capacity
	 */
	public SimpleMessageStore() {
		this(0);
	}

	@ManagedAttribute
	public long getMessageCount() {
		return idToMessage.size();
	}

	public <T> Message<T> addMessage(Message<T> message) {
		if (!individualUpperBound.tryAcquire(0)) {
			throw new MessagingException(this.getClass().getSimpleName()
					+ " was out of capacity at, try constructing it with a larger capacity.");
		}
		this.idToMessage.put(message.getHeaders().getId(), message);
		return message;
	}

	public Message<?> getMessage(UUID key) {
		return (key != null) ? this.idToMessage.get(key) : null;
	}

	public Message<?> removeMessage(UUID key) {
		if (key != null) {
			individualUpperBound.release();
			return this.idToMessage.remove(key);
		}
		else
			return null;
	}

	public MessageGroup getMessageGroup(Object groupId) {
		Assert.notNull(groupId, "'groupId' must not be null");
		SimpleMessageGroup group = groupIdToMessageGroup.get(groupId);
		if (group == null) {
			return new SimpleMessageGroup(groupId);
		}
		return new SimpleMessageGroup(group);
	}

	public MessageGroup addMessageToGroup(Object groupId, Message<?> message) {
		if (!groupUpperBound.tryAcquire(0)) {
			throw new MessagingException(this.getClass().getSimpleName()
					+ " was out of capacity at, try constructing it with a larger capacity.");
		}
		SimpleMessageGroup group = getMessageGroupInternal(groupId);
		group.add(message);
		return group;
	}

	public MessageGroup markMessageGroup(MessageGroup group) {
		Object groupId = group.getGroupId();
		SimpleMessageGroup internal = getMessageGroupInternal(groupId);
		internal.markAll();
		return internal;
	}

	public void removeMessageGroup(Object groupId) {
		if (!groupIdToMessageGroup.containsKey(groupId)) {
			return;
		}
		groupUpperBound.release(groupIdToMessageGroup.get(groupId).size());
		groupIdToMessageGroup.remove(groupId);
	}

	public MessageGroup removeMessageFromGroup(Object key, Message<?> messageToRemove) {
		SimpleMessageGroup group = getMessageGroupInternal(key);
		group.remove(messageToRemove);
		return group;
	}

	public MessageGroup markMessageFromGroup(Object key, Message<?> messageToMark) {
		SimpleMessageGroup group = getMessageGroupInternal(key);
		group.mark(messageToMark);
		return group;
	}

	@Override
	public Iterator<MessageGroup> iterator() {
		return new HashSet<MessageGroup>(groupIdToMessageGroup.values()).iterator();
	}

	private SimpleMessageGroup getMessageGroupInternal(Object groupId) {
		if (!groupIdToMessageGroup.containsKey(groupId)) {
			groupIdToMessageGroup.putIfAbsent(groupId, new SimpleMessageGroup(groupId));
		}
		return groupIdToMessageGroup.get(groupId);
	}

}
