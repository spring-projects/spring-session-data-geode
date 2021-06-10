/*
 * Copyright 2020 the original author or authors.
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

import org.apache.geode.cache.GemFireCache;
import org.apache.geode.cache.client.ClientCache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.gemfire.LocalRegionFactoryBean;
import org.springframework.data.gemfire.config.annotation.CacheServerApplication;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Integration tests testing the configuration of client-side Data Serialization with no explicit server-side
 * Serialization configuration.
 *
 * @author John Blum
 * @see java.io.Serializable
 * @see org.junit.Test
 * @see org.apache.geode.cache.client.ClientCache
 * @see org.springframework.context.annotation.AnnotationConfigApplicationContext
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.data.gemfire.config.annotation.CacheServerApplication
 * @see org.springframework.data.gemfire.config.annotation.ClientCacheApplication
 * @see org.springframework.session.Session
 * @see org.springframework.session.SessionRepository
 * @see org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @since 2.6.0
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = SessionSerializationWithDataSerializationAndNoServerSerializationConfigurationIntegrationTests.ClientTestConfiguration.class)
public class SessionSerializationWithDataSerializationAndNoServerSerializationConfigurationIntegrationTests
		extends AbstractGemFireIntegrationTests {

	private static final String SESSIONS_REGION_NAME = "Sessions";

	@BeforeClass
	public static void startGemFireServer() throws IOException {

		String[] arguments = {
			//"-Dspring.profiles.active=server-session-config",
			"-Dspring.session.data.gemfire.session.serializer.bean-name=SessionDataSerializer"
		};

		startGemFireServer(ServerTestConfiguration.class, arguments);
	}

	@Autowired
	private ClientCache clientCache;

	@Autowired
	private SessionRepository<Session> sessionRepository;

	@Before
	public void setup() {

		assertThat(this.clientCache).isNotNull();
		assertThat(this.clientCache.getPdxSerializer()).isNull();
		assertThat(this.sessionRepository).isNotNull();
	}

	@Test
	public void serializesSessionStateCorrectly() {

		Session session = this.sessionRepository.createSession();

		assertThat(session).isNotNull();
		assertThat(session.getId()).isNotEmpty();
		assertThat(session.isExpired()).isFalse();
		assertThat(session.getAttributeNames()).isEmpty();

		User jonDoe = User.newUser("jonDoe").identifiedBy(1);

		session.setAttribute("user", jonDoe);

		assertThat(session.getAttributeNames()).containsExactly("user");
		assertThat(session.<User>getAttribute("user")).isEqualTo(jonDoe);

		// Saves the entire Session object
		this.sessionRepository.save(session);

		User janeDoe = User.newUser("janeDoe").identifiedBy(2);

		session.setAttribute("user", janeDoe);

		assertThat(session.getAttributeNames()).containsExactly("user");
		assertThat(session.<User>getAttribute("user")).isEqualTo(janeDoe);

		// Saves only the delta
		this.sessionRepository.save(session);

		Session loadedSession = this.sessionRepository.findById(session.getId());

		assertThat(loadedSession).isNotNull();
		assertThat(loadedSession).isNotSameAs(session);
		assertThat(loadedSession).isEqualTo(session);
		assertThat(loadedSession.getAttributeNames()).containsExactly("user");
		assertThat(loadedSession.<User>getAttribute("user")).isEqualTo(janeDoe);
	}

	@ClientCacheApplication
	@EnableGemFireHttpSession(
		poolName = "DEFAULT",
		regionName = SESSIONS_REGION_NAME,
		sessionSerializerBeanName = GemFireHttpSessionConfiguration.SESSION_DATA_SERIALIZER_BEAN_NAME
	)
	static class ClientTestConfiguration { }

	@CacheServerApplication
	@Import(ServerSessionTestConfiguration.class)
	static class ServerTestConfiguration {

		public static void main(String[] args) {
			new AnnotationConfigApplicationContext(ServerTestConfiguration.class)
				.registerShutdownHook();
		}

		@Bean(SESSIONS_REGION_NAME)
		public LocalRegionFactoryBean<Object, Object> localRegion(GemFireCache gemfireCache) {

			LocalRegionFactoryBean<Object, Object> localRegion = new LocalRegionFactoryBean<>();

			localRegion.setCache(gemfireCache);
			localRegion.setPersistent(false);

			return localRegion;
		}
	}

	@Configuration
	@Profile("server-session-config")
	@EnableGemFireHttpSession(sessionSerializerBeanName =
		GemFireHttpSessionConfiguration.SESSION_DATA_SERIALIZER_BEAN_NAME)
	static class ServerSessionTestConfiguration { }

	@Getter
	@ToString(of = "name")
	@EqualsAndHashCode(of = "name")
	@RequiredArgsConstructor(staticName = "newUser")
	static class User implements Serializable {

		private Integer id;

		@lombok.NonNull
		private final String name;

		public @NonNull User identifiedBy(@Nullable Integer id) {
			this.id = id;
			return this;
		}
	}
}
