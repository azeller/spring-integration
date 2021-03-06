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

package org.springframework.integration.xmpp;

/**
 * Used as keys for {@link org.springframework.integration.Message} objects
 * that handle XMPP events.
 *
 * @author Mario Gray
 * @author Josh Long
 * @author Oleg Zhurakousky
 * @since 2.0
 */
public class XmppHeaders {

	private static final String PREFIX = "xmpp_";

	public static final String CHAT = PREFIX + "chatKey";

	public static final String CHAT_TO = PREFIX + "chatTo";

	public static final String CHAT_THREAD_ID = PREFIX + "chatThreadId";

	public static final String TYPE = PREFIX + "type";

}
