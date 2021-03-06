/*
 * Copyright 2007-2011 the original author or authors
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */

package org.springframework.integration.redis.store;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.integration.Message;
import org.springframework.integration.store.AbstractMessageGroupStore;
import org.springframework.integration.store.MessageGroup;
import org.springframework.integration.store.MessageGroupStore;
import org.springframework.integration.store.MessageStore;
import org.springframework.integration.store.MessageStoreException;
import org.springframework.integration.store.SimpleMessageGroup;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.util.Assert;

/**
 * An implementation of both the {@link MessageStore} and {@link MessageGroupStore}
 * strategies that relies upon Redis for persistence.
 * 
 * @author Oleg Zhurakousky
 * @since 2.1
 */
public class RedisMessageStore extends AbstractMessageGroupStore implements MessageStore {

	private static final String MESSAGE_GROUPS_KEY = "MESSAGE_GROUPS";

	private static final String MARKED_PREFIX = "MARKED_";

	private static final String UNMARKED_PREFIX = "UNMARKED_";


	private final RedisTemplate<String, Object> redisTemplate;
	

	public RedisMessageStore(RedisConnectionFactory connectionFactory) {
		this.redisTemplate = new RedisTemplate<String, Object>();
		this.redisTemplate.setConnectionFactory(connectionFactory);
		this.redisTemplate.setKeySerializer(new StringRedisSerializer());
		this.redisTemplate.setValueSerializer(new JdkSerializationRedisSerializer());
	}


	public void setValueSerializer(RedisSerializer<?> valueSerializer) {
		Assert.notNull(valueSerializer, "'valueSerializer' must not be null");
		this.redisTemplate.setValueSerializer(valueSerializer);
	}

	public Message<?> getMessage(final UUID id) {
		Assert.notNull(id, "'id' must not be null");
		if (this.redisTemplate.hasKey(id.toString())) {
			BoundValueOperations<String, Object> ops = redisTemplate.boundValueOps(id.toString());	
			Object result = ops.get();
			Assert.isInstanceOf(Message.class, result, "Return value is not an instace of Message");
			return (Message<?>) result;
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public <T> Message<T> addMessage(Message<T> message) {
		Assert.notNull(message, "'message' must not be null");
		BoundValueOperations<String, Object> ops = redisTemplate.boundValueOps(message.getHeaders().getId().toString());
		try {
			ops.set(message);
		}
		catch (SerializationException e) {
			throw new MessageStoreException(message, "If relying on the default RedisSerializer (JdkSerializationRedisSerializer) " +
					"the Message must be Serializable. Either make it Serializable or provide your own implementation of " +
					"RedisSerializer via 'setValueSerializer(..)'", e);
		}
		Object result = ops.get();
		Assert.isInstanceOf(Message.class, result, "Return value is not an instace of Message");
		return (Message<T>) result;
	}

	public Message<?> removeMessage(UUID id) {
		Assert.notNull(id, "'id' must not be null");
		Message<?> message = this.getMessage(id);
		if (message != null) {
			this.redisTemplate.delete(id.toString());
		}
		return message;
	}

	@ManagedAttribute
	public long getMessageCount() {
		return redisTemplate.execute(new RedisCallback<Long>() {
			public Long doInRedis(RedisConnection connection) throws DataAccessException {
				return connection.dbSize();
			}
		});
	}


	// MESSAGE GROUP methods

	/**
	 * Will create a new instance of SimpleMessageGroup initializing it with
	 * data collected from the Redis Message Store.
	 */
	public MessageGroup getMessageGroup(Object groupId) {
		Assert.notNull(groupId, "'groupId' must not be null");
		long timestamp = System.currentTimeMillis();
		Collection<Message<?>> unmarkedMessages = this.buildMessageList(this.redisTemplate.boundListOps(UNMARKED_PREFIX + groupId));
		Collection<Message<?>> markedMessages = this.buildMessageList(this.redisTemplate.boundListOps(MARKED_PREFIX + groupId));
		this.doCreateMessageGroupIfNecessary(groupId);
		return new SimpleMessageGroup(unmarkedMessages, markedMessages, groupId, timestamp);
	}

	/**
	 * Add a Message to the group with the provided group ID. 
	 */
	public MessageGroup addMessageToGroup(Object groupId, Message<?> message) {
		Assert.notNull(groupId, "'groupId' must not be null");
		Assert.notNull(message, "'message' must not be null");
		synchronized (groupId) {
			this.doAddMessageToGroup(message, groupId);
			this.addMessage(message);
			return this.getMessageGroup(groupId);
		}
	}

	/**
	 * Mark all messages in the provided group. 
	 */
	public MessageGroup markMessageGroup(MessageGroup group) {
		Assert.notNull(group, "'group' must not be null");
		Object groupId = group.getGroupId();
		synchronized (groupId) {
			this.doMarkMessageGroup(groupId);
			return this.getMessageGroup(groupId);
		}
	}

	/**
	 * Remove a Message from the group with the provided group ID. 
	 */
	public MessageGroup removeMessageFromGroup(Object groupId, Message<?> messageToRemove) {
		Assert.notNull(groupId, "'groupId' must not be null");
		Assert.notNull(messageToRemove, "'messageToRemove' must not be null");
		UUID messageId = messageToRemove.getHeaders().getId();
		synchronized (groupId) {
			this.doRemoveMessageFromGroup(groupId, messageId);
			this.removeMessage(messageId);
			return this.getMessageGroup(groupId);
		}
	}

	/**
	 * Mark the given Message within the group corresponding to the provided group ID. 
	 */
	public MessageGroup markMessageFromGroup(Object groupId, Message<?> messageToMark) {
		Assert.notNull(groupId, "'groupId' must not be null");
		Assert.notNull(messageToMark, "'messageToMark' must not be null");
		String messageIdAsString = messageToMark.getHeaders().getId().toString();
		synchronized (groupId) {
			this.doMarkMessageFromGroup(messageIdAsString, groupId);
			return this.getMessageGroup(groupId);
		}
	}

	/**
	 * Remove the MessageGroup with the provided group ID. 
	 */
	public void removeMessageGroup(Object groupId) {
		Assert.notNull(groupId, "'groupId' must not be null");
		synchronized (groupId) {
			this.doRemoveMessageGroup(groupId);
		}
	}

	@Override
	public Iterator<MessageGroup> iterator() {
		BoundSetOperations<String, Object> mGroupsOps = this.redisTemplate.boundSetOps(MESSAGE_GROUPS_KEY);
		Set<Object> messageGroupIds = mGroupsOps.members();
		List<MessageGroup> messageGroups = new ArrayList<MessageGroup>();
		for (Object messageGroupId : messageGroupIds) {
			messageGroups.add(this.getMessageGroup(messageGroupId));
		}
		return messageGroups.iterator();
	}

	private Collection<Message<?>> buildMessageList(BoundListOperations<String, Object> messageGroupOps) {
		List<Message<?>> messages = new LinkedList<Message<?>>();
		if (messageGroupOps.size() == 0) {
			return Collections.emptyList();
		}
		List<Object> messageIds = messageGroupOps.range(0, messageGroupOps.size() - 1);
		for (Object messageId : messageIds) {
			Message<?> message = this.getMessage(UUID.fromString(messageId.toString()));
			if (message != null) {
				messages.add((Message<?>) message);
			}
		}
		return messages;
	}

	/* candidates for future abstract methods */

	private void doCreateMessageGroupIfNecessary(Object groupId) {
		BoundSetOperations<String, Object> messageGroupsOps = this.redisTemplate.boundSetOps(MESSAGE_GROUPS_KEY);
		if (!messageGroupsOps.members().contains(groupId)) {
			messageGroupsOps.add(groupId);
		}
	}

	private void doAddMessageToGroup(Message<?> message, Object groupId) {
		String messageId = message.getHeaders().getId().toString();
		BoundListOperations<String, Object> unmarkedOps = this.redisTemplate.boundListOps(UNMARKED_PREFIX + groupId);
		unmarkedOps.rightPush(messageId);
	}

	private void doMarkMessageGroup(Object groupId) {
		BoundListOperations<String, Object> unmarkedOps = this.redisTemplate.boundListOps(UNMARKED_PREFIX + groupId);
		unmarkedOps.rename(MARKED_PREFIX + groupId);
	}

	private void doRemoveMessageFromGroup(Object groupId, UUID messageId) {
		BoundListOperations<String, Object> unmarkedOps = this.redisTemplate.boundListOps(UNMARKED_PREFIX + groupId);
		BoundListOperations<String, Object> markedOps = this.redisTemplate.boundListOps(MARKED_PREFIX + groupId);
		unmarkedOps.remove(0, messageId.toString());
		markedOps.remove(0, messageId.toString());
	}

	private void doMarkMessageFromGroup(String messageIdAsString, Object groupId) {
		BoundListOperations<String, Object> unmarkedOps = this.redisTemplate.boundListOps(UNMARKED_PREFIX + groupId);
		if (unmarkedOps.size() > 0) {
			List<Object> messageIds = unmarkedOps.range(0, unmarkedOps.size() - 1);
			int objectIndex = messageIds.indexOf(messageIdAsString);
			if (objectIndex > -1) {
				BoundListOperations<String, Object> markedOps = this.redisTemplate.boundListOps(MARKED_PREFIX + groupId);
				markedOps.rightPush(messageIdAsString);
				unmarkedOps.remove(0, messageIdAsString);
			}
		}
	}

	private void doRemoveMessageGroup(Object groupId) {
		BoundListOperations<String, Object> unmarkedOps = this.redisTemplate.boundListOps(UNMARKED_PREFIX + groupId);
		if (unmarkedOps.size() > 0) {
			List<Object> messageIds = unmarkedOps.range(0, unmarkedOps.size() - 1);
			for (Object messageId : messageIds) {
				this.removeMessage(UUID.fromString(messageId.toString()));
			}
			this.redisTemplate.delete(UNMARKED_PREFIX + groupId);
		}
		BoundListOperations<String, Object> markedOps = this.redisTemplate.boundListOps(MARKED_PREFIX + groupId);
		if (markedOps.size() > 0) {
			List<Object> messageIds =  markedOps.range(0, markedOps.size() - 1);
			for (Object messageId : messageIds) {
				this.removeMessage(UUID.fromString(messageId.toString()));
			}
			this.redisTemplate.delete(MARKED_PREFIX + groupId);
		}
		BoundSetOperations<String, Object> messageGroupsOps = this.redisTemplate.boundSetOps(MESSAGE_GROUPS_KEY);
		messageGroupsOps.remove(groupId);
	}

}
