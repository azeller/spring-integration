/*
 * Copyright 2002-2008 the original author or authors.
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

package org.springframework.integration.mail.config;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Properties;

import org.junit.Test;

import org.springframework.beans.DirectFieldAccessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.mail.MailSendingMessageHandler;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.mail.MailSender;

/**
 * @author Mark Fisher
 */
public class MailOutboundChannelAdapterParserTests {

	@Test
	public void adapterWithMailSenderReference() {
		ApplicationContext context = new ClassPathXmlApplicationContext(
				"mailOutboundChannelAdapterParserTests.xml", this.getClass());
		Object adapter = context.getBean("adapterWithMailSenderReference.adapter");
		MailSendingMessageHandler handler = (MailSendingMessageHandler)
				new DirectFieldAccessor(adapter).getPropertyValue("handler");
		DirectFieldAccessor fieldAccessor = new DirectFieldAccessor(handler);
		MailSender mailSender = (MailSender) fieldAccessor.getPropertyValue("mailSender");
		assertNotNull(mailSender);
		assertEquals(23, fieldAccessor.getPropertyValue("order"));
	}

	@Test
	public void adapterWithHostProperty() {
		ApplicationContext context = new ClassPathXmlApplicationContext(
				"mailOutboundChannelAdapterParserTests.xml", this.getClass());
		Object adapter = context.getBean("adapterWithHostProperty.adapter");
		MailSendingMessageHandler handler = (MailSendingMessageHandler)
				new DirectFieldAccessor(adapter).getPropertyValue("handler");
		DirectFieldAccessor fieldAccessor = new DirectFieldAccessor(handler);
		MailSender mailSender = (MailSender) fieldAccessor.getPropertyValue("mailSender");
		assertNotNull(mailSender);
	}
	
	@Test
	public void adapterWithJavaMailProperties() {
		ApplicationContext context = new ClassPathXmlApplicationContext(
				"MailOutboundWithJavamailProperties-context.xml", this.getClass());
		Object adapter = context.getBean("adapterWithHostProperty.adapter");
		MailSendingMessageHandler handler = (MailSendingMessageHandler)
				new DirectFieldAccessor(adapter).getPropertyValue("handler");
		DirectFieldAccessor fieldAccessor = new DirectFieldAccessor(handler);
		MailSender mailSender = (MailSender) fieldAccessor.getPropertyValue("mailSender");
		assertNotNull(mailSender);
		Properties javaMailProperties = (Properties) TestUtils.getPropertyValue(mailSender, "javaMailProperties");
		assertEquals(7, javaMailProperties.size());
		assertNotNull(javaMailProperties);
		assertEquals("true", javaMailProperties.get("mail.smtps.auth"));
	}

}
