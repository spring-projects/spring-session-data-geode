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

package org.springframework.session.data.gemfire.serialization.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Serializable;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.geode.cache.client.ClientCache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.gemfire.config.annotation.CacheServerApplication;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Integration test testing the serialization of a {@link Session} object containing application domain object types
 * de/serialized using Java Serialization.
 *
 * @author John Blum
 * @see org.apache.geode.cache.client.ClientCache
 * @see org.springframework.context.annotation.AnnotationConfigApplicationContext
 * @see org.springframework.data.gemfire.config.annotation.CacheServerApplication
 * @see org.springframework.data.gemfire.config.annotation.ClientCacheApplication
 * @see org.springframework.session.Session
 * @see org.springframework.session.SessionRepository
 * @see org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @since 2.1.3
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(
	classes = SessionSerializationWithDataSerializationDeltasAndJavaSerializationIntegrationTests.GemFireClientConfiguration.class
)
@SuppressWarnings("unused")
public class SessionSerializationWithDataSerializationDeltasAndJavaSerializationIntegrationTests
		extends AbstractGemFireIntegrationTests{

	private static final String GEMFIRE_LOG_LEVEL = "error";
	private static final String SESSIONS_REGION_NAME = "Sessions";

	@BeforeClass
	public static void startGemFireServer() throws IOException {
		startGemFireServer(GemFireServerConfiguration.class);
	}

	@Autowired
	private ClientCache clientCache;

	@Autowired
	private SessionRepository<Session> sessionRepository;

	@Before
	public void assertPdxNotConfigured() {

		assertThat(this.clientCache).isNotNull();
		assertThat(this.clientCache.getPdxSerializer()).isNull();
	}

	@Test
	public void serializesSessionStateCorrectly() {

		Session session = this.sessionRepository.createSession();

		assertThat(session).isNotNull();
		assertThat(session.getId()).isNotEmpty();
		assertThat(session.isExpired()).isFalse();
		assertThat(session.getAttributeNames()).isEmpty();

		Customer jonDoe = Customer.newCustomer("Jon Doe");

		session.setAttribute("jonDoe", jonDoe);

		assertThat(session.getAttributeNames()).containsOnly("jonDoe");
		assertThat(session.<Customer>getAttribute("jonDoe")).isEqualTo(jonDoe);

		this.sessionRepository.save(session);

		Customer janeDoe = Customer.newCustomer("Jane Doe");

		session.setAttribute("janeDoe", janeDoe);

		assertThat(session.getAttributeNames()).containsOnly("jonDoe", "janeDoe");
		assertThat(session.<Customer>getAttribute("jonDoe")).isEqualTo(jonDoe);
		assertThat(session.<Customer>getAttribute("janeDoe")).isEqualTo(janeDoe);

		this.sessionRepository.save(session);

		Session loadedSession = this.sessionRepository.findById(session.getId());

		assertThat(loadedSession).isEqualTo(session);
		assertThat(loadedSession).isNotSameAs(session);
		assertThat(loadedSession.getAttributeNames()).containsOnly("jonDoe", "janeDoe");
		assertThat(loadedSession.<Customer>getAttribute("jonDoe")).isEqualTo(jonDoe);
		assertThat(loadedSession.<Customer>getAttribute("janeDoe")).isEqualTo(janeDoe);
	}

	@ClientCacheApplication(logLevel = GEMFIRE_LOG_LEVEL, subscriptionEnabled = true)
	@EnableGemFireHttpSession(
		poolName = "DEFAULT",
		regionName =  SESSIONS_REGION_NAME,
		sessionSerializerBeanName = GemFireHttpSessionConfiguration.SESSION_DATA_SERIALIZER_BEAN_NAME
	)
	static class GemFireClientConfiguration { }

	@CacheServerApplication(logLevel = GEMFIRE_LOG_LEVEL)
	@EnableGemFireHttpSession(
		regionName = SESSIONS_REGION_NAME,
		sessionSerializerBeanName = GemFireHttpSessionConfiguration.SESSION_DATA_SERIALIZER_BEAN_NAME
	)
	static class GemFireServerConfiguration {

		public static void main(String[] args) {

			AnnotationConfigApplicationContext applicationContext =
				new AnnotationConfigApplicationContext(GemFireServerConfiguration.class);

			applicationContext.registerShutdownHook();
		}
	}

	@Data
	@ToString
	@EqualsAndHashCode
	@RequiredArgsConstructor(staticName = "newCustomer")
	//static class Customer {
	static class Customer implements Serializable {

		@NonNull
		private String name;

		/*
		// Uncomment this method to see exactly how/where GemFire tries to deserialize the Customer object
		// and resolve the Customer class on the server-side.
		private void readObject(ObjectInputStream inputStream) throws IOException {
			throw new IllegalStateException("Customer could not be deserialized");
		}
		*/
	}
}
