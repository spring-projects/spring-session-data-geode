/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session.data.gemfire;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.gemfire.config.annotation.CacheServerApplication;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Integration tests testing the addition/removal of HTTP Session Attributes
 * and the proper persistence of the HTTP Session state in a Pivotal GemFire cache
 * across a client/server topology.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.springframework.context.ConfigurableApplicationContext
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @see org.apache.geode.cache.Cache
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.cache.client.ClientCache
 * @since 1.3.1
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(
	classes = ClientServerHttpSessionAttributesDeltaIntegrationTests.SpringSessionDataGemFireClientConfiguration.class
)
public class ClientServerHttpSessionAttributesDeltaIntegrationTests extends AbstractGemFireIntegrationTests {

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 1;

	@BeforeClass
	public static void startGemFireServer() throws IOException {
		startGemFireServer(SpringSessionDataGemFireServerConfiguration.class);
	}

	@Test
	public void sessionDeltaOperationsAreCorrect() {

		Session session = save(touch(createSession()));

		assertThat(session).isNotNull();
		assertThat(session.getId()).isNotEmpty();
		assertThat(session.isExpired()).isFalse();
		assertThat(session.getAttributeNames()).isEmpty();

		session.setAttribute("attrOne", 1);
		session.setAttribute("attrTwo", 2);

		save(touch(session));

		Session loadedSession = get(session.getId());

		assertThat(loadedSession).isNotNull();
		assertThat(loadedSession).isNotSameAs(session);
		assertThat(loadedSession.getId()).isEqualTo(session.getId());
		assertThat(loadedSession.isExpired()).isFalse();
		assertThat(loadedSession.<Integer>getAttribute("attrOne")).isEqualTo(1);
		assertThat(loadedSession.<Integer>getAttribute("attrTwo")).isEqualTo(2);

		loadedSession.removeAttribute("attrTwo");

		assertThat(loadedSession.getAttributeNames()).containsOnly("attrOne");
		assertThat(loadedSession.getAttributeNames()).doesNotContain("attrTwo");

		save(touch(loadedSession));

		Session reloadedSession = get(loadedSession.getId());

		assertThat(reloadedSession).isNotNull();
		assertThat(reloadedSession).isNotSameAs(loadedSession);
		assertThat(reloadedSession.isExpired()).isFalse();
		assertThat(reloadedSession.getId()).isEqualTo(loadedSession.getId());
		assertThat(reloadedSession.getAttributeNames()).containsOnly("attrOne");
		assertThat(reloadedSession.getAttributeNames()).doesNotContain("attrTwo");
		assertThat(reloadedSession.<Integer>getAttribute("attrOne")).isEqualTo(1);
	}

	@ClientCacheApplication(
		logLevel = "error",
		subscriptionEnabled = true
	)
	@EnableGemFireHttpSession(
		poolName = "DEFAULT",
		sessionSerializerBeanName = GemFireHttpSessionConfiguration.SESSION_DATA_SERIALIZER_BEAN_NAME
	)
	static class SpringSessionDataGemFireClientConfiguration { }

	@CacheServerApplication(
		name = "ClientServerHttpSessionAttributesDeltaIntegrationTests",
		logLevel = "error"
	)
	@EnableGemFireHttpSession(
		maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS,
		sessionSerializerBeanName = GemFireHttpSessionConfiguration.SESSION_DATA_SERIALIZER_BEAN_NAME
	)
	static class SpringSessionDataGemFireServerConfiguration {

		public static void main(String[] args) {

			AnnotationConfigApplicationContext applicationContext =
				new AnnotationConfigApplicationContext(SpringSessionDataGemFireServerConfiguration.class);

			applicationContext.registerShutdownHook();
		}
	}
}
