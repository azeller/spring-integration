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

package org.springframework.integration.config.annotation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.springframework.integration.test.util.TestUtils.getPropertyValue;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.aggregator.ReleaseStrategyAdapter;
import org.springframework.integration.aggregator.CorrelatingMessageHandler;
import org.springframework.integration.aggregator.CorrelationStrategyAdapter;
import org.springframework.integration.aggregator.SequenceSizeReleaseStrategy;
import org.springframework.integration.channel.NullChannel;
import org.springframework.integration.context.BeanFactoryChannelResolver;
import org.springframework.integration.core.ChannelResolver;
import org.springframework.integration.endpoint.EventDrivenConsumer;
import org.springframework.integration.message.MessageHandler;
import org.springframework.integration.test.util.TestUtils;

/**
 * @author Marius Bogoevici
 * @author Mark Fisher
 */
public class AggregatorAnnotationTests {

	@Test
	public void testAnnotationWithDefaultSettings() {
		ApplicationContext context = new ClassPathXmlApplicationContext(
				new String[] { "classpath:/org/springframework/integration/config/annotation/testAnnotatedAggregator.xml" });
		final String endpointName = "endpointWithDefaultAnnotation";
		MessageHandler aggregator = this.getAggregator(context, endpointName);
		assertTrue(getPropertyValue(aggregator, "releaseStrategy") instanceof SequenceSizeReleaseStrategy);
		assertNull(getPropertyValue(aggregator, "outputChannel"));
		assertTrue(getPropertyValue(aggregator, "discardChannel") instanceof NullChannel);
		assertEquals(CorrelatingMessageHandler.DEFAULT_SEND_TIMEOUT,
				getPropertyValue(aggregator, "messagingTemplate.sendTimeout"));
		assertEquals(false, getPropertyValue(aggregator, "sendPartialResultOnExpiry"));
	}

	@Test
	public void testAnnotationWithCustomSettings() {
		ApplicationContext context = new ClassPathXmlApplicationContext(
				new String[] { "classpath:/org/springframework/integration/config/annotation/testAnnotatedAggregator.xml" });
		final String endpointName = "endpointWithCustomizedAnnotation";
		MessageHandler aggregator = this.getAggregator(context, endpointName);
		assertTrue(getPropertyValue(aggregator, "releaseStrategy")
				instanceof SequenceSizeReleaseStrategy);
		ChannelResolver channelResolver = new BeanFactoryChannelResolver(context);
		assertEquals(channelResolver.resolveChannelName("outputChannel"),
				getPropertyValue(aggregator, "outputChannel"));
		assertEquals(channelResolver.resolveChannelName("discardChannel"),
				getPropertyValue(aggregator, "discardChannel"));
		assertEquals(98765432l, getPropertyValue(aggregator, "messagingTemplate.sendTimeout"));
		assertEquals(true, getPropertyValue(aggregator, "sendPartialResultOnExpiry"));
	}

	@Test
	public void testAnnotationWithCustomReleaseStrategy() throws Exception {
		ApplicationContext context = new ClassPathXmlApplicationContext(
				new String[] { "classpath:/org/springframework/integration/config/annotation/testAnnotatedAggregator.xml" });
		final String endpointName = "endpointWithDefaultAnnotationAndCustomReleaseStrategy";
		MessageHandler aggregator = this.getAggregator(context, endpointName);
		Object ReleaseStrategy = getPropertyValue(aggregator, "releaseStrategy");
		Assert.assertTrue(ReleaseStrategy instanceof ReleaseStrategyAdapter);
		ReleaseStrategyAdapter ReleaseStrategyAdapter = (ReleaseStrategyAdapter) ReleaseStrategy;
		DirectFieldAccessor invokerAccessor = new DirectFieldAccessor(
				new DirectFieldAccessor(ReleaseStrategyAdapter).getPropertyValue("invoker"));
		Object targetObject = invokerAccessor.getPropertyValue("object");
		assertSame(context.getBean(endpointName), targetObject);
		Method completionCheckerMethod = (Method) invokerAccessor.getPropertyValue("method");
		assertEquals("completionChecker", completionCheckerMethod.getName());
	}

    @Test
    public void testAnnotationWithCustomCorrelationStrategy() throws Exception {
        ApplicationContext context = new ClassPathXmlApplicationContext(
                new String[] { "classpath:/org/springframework/integration/config/annotation/testAnnotatedAggregator.xml" });
        final String endpointName = "endpointWithCorrelationStrategy";
        MessageHandler aggregator = this.getAggregator(context, endpointName);
        Object correlationStrategy = getPropertyValue(aggregator, "correlationStrategy");
        Assert.assertTrue(correlationStrategy instanceof CorrelationStrategyAdapter);
        CorrelationStrategyAdapter ReleaseStrategyAdapter = (CorrelationStrategyAdapter) correlationStrategy;
        DirectFieldAccessor processorAccessor = new DirectFieldAccessor(
                new DirectFieldAccessor(ReleaseStrategyAdapter).getPropertyValue("processor"));
        Object targetObject = processorAccessor.getPropertyValue("targetObject");
        assertSame(context.getBean(endpointName), targetObject);
        Map<?, ?> handlerMethods = (Map<?, ?>) processorAccessor.getPropertyValue("handlerMethods");
        assertEquals(1, handlerMethods.size());
        DirectFieldAccessor handlerMethodAccessor = new DirectFieldAccessor(handlerMethods.values().iterator().next());
        Method completionCheckerMethod = (Method) handlerMethodAccessor.getPropertyValue("method");
		assertEquals("correlate", completionCheckerMethod.getName());                
    }



	private MessageHandler getAggregator(ApplicationContext context, final String endpointName) {
		EventDrivenConsumer endpoint = (EventDrivenConsumer) context.getBean(
				endpointName + ".aggregatingMethod.aggregator");
		return TestUtils.getPropertyValue(endpoint, "handler", MessageHandler.class);
	}

}