/*
 * Copyright 2015-present the original author or authors.
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

import java.io.Serializable;
import java.time.Instant;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.geode.cache.client.ClientRegionShortcut;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.data.gemfire.tests.integration.IntegrationTestsSupport;
import org.springframework.data.gemfire.tests.mock.annotation.EnableGemFireMockObjects;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Unit Tests for persisting and accessing {@link Session} objects in Apache Geode using Mock Objects.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.apache.geode.cache.client.ClientCache
 * @see org.springframework.data.gemfire.config.annotation.ClientCacheApplication
 * @see org.springframework.data.gemfire.tests.integration.IntegrationTestsSupport
 * @see org.springframework.data.gemfire.tests.mock.annotation.EnableGemFireMockObjects
 * @see org.springframework.session.Session
 * @see org.springframework.session.SessionRepository
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @since 2.4.1
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
public class PersistentSessionAccessUnitTests extends IntegrationTestsSupport {

	@Autowired
	private SessionRepository<Session> sessionRepository;

	@Test
	public void sessionAccessIsSuccessful() {

		Instant beforeCreationTime = Instant.now();

		User jonDoe = User.newUser("jonDoe").identifiedBy(1L);

		Session session = this.sessionRepository.createSession();

		assertThat(session).isNotNull();
		assertThat(session.getId()).isNotBlank();
		assertThat(session.getCreationTime()).isAfterOrEqualTo(beforeCreationTime);
		assertThat(session.isExpired()).isFalse();

		session.setAttribute(jonDoe.getName(), jonDoe);

		assertThat(session.<User>getAttribute(jonDoe.getName())).isEqualTo(jonDoe);

		this.sessionRepository.save(session);

		Instant beforeLastAccessedTime = Instant.now();

		Session loadedSession = this.sessionRepository.findById(session.getId());

		assertThat(loadedSession).isNotNull();
		assertThat(loadedSession.getId()).isEqualTo(session.getId());
		assertThat(loadedSession.getCreationTime()).isEqualTo(session.getCreationTime());
		assertThat(loadedSession.getLastAccessedTime()).isAfterOrEqualTo(beforeLastAccessedTime);
		assertThat(loadedSession.isExpired()).isFalse();
		assertThat(loadedSession.<User>getAttribute(jonDoe.getName())).isEqualTo(jonDoe);
	}

	@ClientCacheApplication
	@EnableGemFireMockObjects
	@EnableGemFireHttpSession(clientRegionShortcut = ClientRegionShortcut.LOCAL, poolName = "DEFAULT")
	static class TestGeodeSessionConfiguration { }

	@Getter
	@ToString(of = "name")
	@EqualsAndHashCode(of = "name")
	@RequiredArgsConstructor(staticName = "newUser")
	static class User implements Serializable {

		private Long id;

		@NonNull
		private final String name;

		User identifiedBy(Long id) {
			this.id = id;
			return this;
		}
	}
}
