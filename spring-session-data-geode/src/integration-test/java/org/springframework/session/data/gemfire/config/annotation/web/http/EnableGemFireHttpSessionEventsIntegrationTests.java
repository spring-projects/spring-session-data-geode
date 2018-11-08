/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.session.data.gemfire.config.annotation.web.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.geode.cache.DataPolicy;
import org.apache.geode.cache.ExpirationAction;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionShortcut;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.gemfire.config.annotation.PeerCacheApplication;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests;
import org.springframework.session.data.gemfire.GemFireOperationsSessionRepository;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * Integration test suite of test cases testing the Session Event functionality and behavior
 * of the {@link GemFireOperationsSessionRepository} and GemFire's configuration.
 *
 * @author John Blum
 * @since 1.1.0
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.apache.geode.cache.Region
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests
 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository
 * @see org.springframework.session.events.SessionCreatedEvent
 * @see org.springframework.session.events.SessionDeletedEvent
 * @see org.springframework.session.events.SessionExpiredEvent
 * @see org.springframework.test.annotation.DirtiesContext
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @see org.springframework.test.context.web.WebAppConfiguration
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@DirtiesContext
@WebAppConfiguration
@SuppressWarnings("unused")
public class EnableGemFireHttpSessionEventsIntegrationTests extends AbstractGemFireIntegrationTests {

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 1;

	private static final String GEMFIRE_LOG_LEVEL = "warning";
	private static final String SPRING_SESSION_DATA_GEMFIRE_REGION_NAME = "TestReplicatedSessions";

	@Autowired
	private SessionEventListener sessionEventListener;

	@Before
	public void setup() {

		assertThat(GemFireUtils.isPeer(this.gemfireCache)).isTrue();
		assertThat(this.gemfireSessionRepository).isNotNull();
		assertThat(this.gemfireSessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		assertThat(this.sessionEventListener).isNotNull();

		Region<Object, Session> sessionRegion = this.gemfireCache.getRegion(SPRING_SESSION_DATA_GEMFIRE_REGION_NAME);

		assertRegion(sessionRegion, SPRING_SESSION_DATA_GEMFIRE_REGION_NAME, DataPolicy.REPLICATE);
		assertEntryIdleTimeout(sessionRegion, ExpirationAction.INVALIDATE, MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@After
	public void tearDown() {
		this.sessionEventListener.getSessionEvent();
	}

	@Test
	public void sessionCreatedEvent() {

		Instant beforeOrAtCreationTime = Instant.now();

		Session expectedSession = save(createSession());

		AbstractSessionEvent sessionEvent = this.sessionEventListener.getSessionEvent();

		assertThat(sessionEvent).isInstanceOf(SessionCreatedEvent.class);

		Session createdSession = sessionEvent.getSession();

		assertThat(createdSession).isEqualTo(expectedSession);
		assertThat(createdSession.getId()).isNotNull();
		assertThat(createdSession.getCreationTime().compareTo(beforeOrAtCreationTime)).isGreaterThanOrEqualTo(0);
		assertThat(createdSession.getLastAccessedTime()).isEqualTo(createdSession.getCreationTime());
		assertThat(createdSession.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS));
		assertThat(createdSession.isExpired()).isFalse();
	}

	@Test
	public void getExistingNonExpiredSession() {

		Session expectedSession = save(touch(createSession()));

		assertThat(expectedSession.isExpired()).isFalse();

		// NOTE though unlikely, a possible race condition exists between save and get...
		Session savedSession = this.gemfireSessionRepository.findById(expectedSession.getId());

		assertThat(savedSession).isEqualTo(expectedSession);
	}

	@Test
	public void getExistingExpiredSession() {

		Session expectedSession = save(expire(createSession()));

		AbstractSessionEvent sessionEvent = this.sessionEventListener.getSessionEvent();

		assertThat(sessionEvent).isInstanceOf(SessionCreatedEvent.class);

		Session createdSession = sessionEvent.getSession();

		assertThat(createdSession).isEqualTo(expectedSession);
		assertThat(createdSession.isExpired()).isTrue();
		assertThat(this.gemfireSessionRepository.findById(createdSession.getId())).isNull();
	}

	@Test
	public void getNonExistingSession() {
		assertThat(this.gemfireSessionRepository.findById(UUID.randomUUID().toString())).isNull();
	}

	@Test
	public void deleteExistingNonExpiredSession() {

		Session expectedSession = save(touch(createSession()));
		Session savedSession = this.gemfireSessionRepository.findById(expectedSession.getId());

		assertThat(savedSession).isEqualTo(expectedSession);
		assertThat(savedSession.isExpired()).isFalse();

		this.gemfireSessionRepository.deleteById(savedSession.getId());

		AbstractSessionEvent sessionEvent = this.sessionEventListener.getSessionEvent();

		assertThat(sessionEvent).isInstanceOf(SessionDeletedEvent.class);
		assertThat(sessionEvent.getSessionId()).isEqualTo(savedSession.getId());

		Session deletedSession = sessionEvent.getSession();

		assertThat(deletedSession).isEqualTo(savedSession);
		assertThat(this.gemfireSessionRepository.findById(deletedSession.getId())).isNull();
	}

	@Test
	public void deleteExistingExpiredSession() {

		Session expectedSession = save(createSession());

		AbstractSessionEvent sessionEvent = this.sessionEventListener.getSessionEvent();

		assertThat(sessionEvent).isInstanceOf(SessionCreatedEvent.class);

		Session createdSession = sessionEvent.getSession();

		assertThat(createdSession).isEqualTo(expectedSession);

		sessionEvent = this.sessionEventListener.waitForSessionEvent(
			TimeUnit.SECONDS.toMillis(this.gemfireSessionRepository.getMaxInactiveIntervalInSeconds() + 1));

		assertThat(sessionEvent).isInstanceOf(SessionExpiredEvent.class);

		Session expiredSession = sessionEvent.getSession();

		assertThat(expiredSession).isEqualTo(createdSession);
		assertThat(expiredSession.isExpired()).isTrue();

		this.gemfireSessionRepository.deleteById(expectedSession.getId());

		sessionEvent = this.sessionEventListener.getSessionEvent();

		assertThat(sessionEvent).isInstanceOf(SessionDeletedEvent.class);
		assertThat(sessionEvent.<Session>getSession()).isEqualTo(expectedSession);
		assertThat(sessionEvent.getSessionId()).isEqualTo(expiredSession.getId());
		assertThat(this.gemfireSessionRepository.<Session>findById(sessionEvent.getSessionId())).isNull();
	}

	@Test
	public void deleteNonExistingSession() {

		String expectedSessionId = UUID.randomUUID().toString();

		assertThat(this.gemfireSessionRepository.<Session>findById(expectedSessionId)).isNull();

		this.gemfireSessionRepository.deleteById(expectedSessionId);

		AbstractSessionEvent sessionEvent = this.sessionEventListener.getSessionEvent();

		assertThat(sessionEvent).isInstanceOf(SessionDeletedEvent.class);
		assertThat(sessionEvent.<Session>getSession()).isNotNull();
		assertThat(sessionEvent.getSession().getId()).isEqualTo(expectedSessionId);
		assertThat(sessionEvent.getSessionId()).isEqualTo(expectedSessionId);
	}

	@PeerCacheApplication(name = "EnableGemFireHttpSessionEventsIntegrationTests", logLevel = GEMFIRE_LOG_LEVEL)
	@EnableGemFireHttpSession(
		regionName = SPRING_SESSION_DATA_GEMFIRE_REGION_NAME,
		maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS,
		serverRegionShortcut = RegionShortcut.REPLICATE
	)
	@SuppressWarnings("unused")
	static class SpringSessionGemFireConfiguration {

		@Bean
		SessionEventListener sessionEventListener() {
			return new SessionEventListener();
		}
	}
}
