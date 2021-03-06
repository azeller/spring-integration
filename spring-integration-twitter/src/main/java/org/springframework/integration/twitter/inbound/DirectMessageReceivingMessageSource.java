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

package org.springframework.integration.twitter.inbound;

import java.util.List;

import org.springframework.integration.twitter.core.Tweet;
import org.springframework.integration.twitter.core.TwitterOperations;

/**
 * This class handles support for receiving DMs (direct messages) using Twitter.
 *
 * @author Josh Long
 * @author Oleg Zhurakousky
 * @author Mark Fisher
 * @since 2.0
 */
public class DirectMessageReceivingMessageSource extends AbstractTwitterMessageSource<Tweet> {

	public DirectMessageReceivingMessageSource(TwitterOperations twitter) {
		super(twitter);
	}


	@Override
	public String getComponentType() {
		return "twitter:dm-inbound-channel-adapter";  
	}

	@Override
	protected List<Tweet> pollForTweets(long sinceId) {
		return (sinceId > 0)
				? this.getTwitterOperations().getDirectMessages(sinceId)
				: this.getTwitterOperations().getDirectMessages(); 
	}

}
