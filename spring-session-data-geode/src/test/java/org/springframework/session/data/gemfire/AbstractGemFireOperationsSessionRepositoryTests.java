/*
 * Copyright 2014-2017 the original author or authors.
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

package org.springframework.session.data.gemfire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.data.gemfire.util.CollectionUtils.asSet;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSession;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSessionAttributes;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSessionAttributes;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import edu.umd.cs.mtc.MultithreadedTestCase;
import edu.umd.cs.mtc.TestFramework;

import org.apache.geode.cache.AttributesMutator;
import org.apache.geode.cache.DataPolicy;
import org.apache.geode.cache.EntryEvent;
import org.apache.geode.cache.Operation;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionAttributes;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.data.gemfire.GemfireTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.session.events.SessionExpiredEvent;

import org.apache.commons.logging.Log;

/**
 * Unit tests for {@link AbstractGemFireOperationsSessionRepository}.
 *
 * @author John Blum
 * @since 1.1.0
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.mockito.junit.MockitoJUnitRunner
 * @see org.mockito.Spy
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.session.Session
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.apache.geode.cache.Region
 * @see edu.umd.cs.mtc.MultithreadedTestCase
 * @see edu.umd.cs.mtc.TestFramework
 */
@RunWith(MockitoJUnitRunner.class)
public class AbstractGemFireOperationsSessionRepositoryTests {

	protected static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 600;

	private AbstractGemFireOperationsSessionRepository sessionRepository;

	@Mock
	private Log mockLog;

	@Mock
	private Session mockSession;

	@Before
	@SuppressWarnings("all")
	public void setup() {

		GemfireTemplate gemfireTemplate = new GemfireTemplate(mock(Region.class));

		this.sessionRepository = spy(new TestGemFireOperationsSessionRepository(gemfireTemplate));

		doReturn(this.mockLog).when(this.sessionRepository).getLogger();
	}

	@SuppressWarnings("unchecked")
	protected <K, V> EntryEvent<K, V> mockEntryEvent(Operation operation, K key, V oldValue, V newValue) {

		EntryEvent<K, V> mockEntryEvent = mock(EntryEvent.class);

		given(mockEntryEvent.getOperation()).willReturn(operation);
		given(mockEntryEvent.getKey()).willReturn(key);
		given(mockEntryEvent.getOldValue()).willReturn(oldValue);
		given(mockEntryEvent.getNewValue()).willReturn(newValue);

		return mockEntryEvent;
	}

	@SuppressWarnings("unchecked")
	protected <K, V> Region<K, V> mockRegion(String name, DataPolicy dataPolicy) {

		Region<K, V> mockRegion = mock(Region.class, name);

		RegionAttributes<K, V> mockRegionAttributes = mockRegionAttributes(name);

		given(mockRegion.getAttributes()).willReturn(mockRegionAttributes);
		given(mockRegionAttributes.getDataPolicy()).willReturn(dataPolicy);

		return mockRegion;
	}

	@SuppressWarnings("unchecked")
	protected <K, V> RegionAttributes<K, V> mockRegionAttributes(String name) {
		return mock(RegionAttributes.class, name);
	}

	protected Session mockSession(String sessionId, long creationAndLastAccessedTime,
			long maxInactiveIntervalInSeconds) {

		return mockSession(sessionId, creationAndLastAccessedTime, creationAndLastAccessedTime,
			maxInactiveIntervalInSeconds);
	}

	protected Session mockSession(String sessionId, long creationTime, long lastAccessedTime,
			long maxInactiveIntervalInSeconds) {

		Session mockSession = mock(Session.class, sessionId);

		given(mockSession.getId()).willReturn(sessionId);
		given(mockSession.getCreationTime()).willReturn(Instant.ofEpochMilli(creationTime));
		given(mockSession.getLastAccessedTime()).willReturn(Instant.ofEpochMilli(lastAccessedTime));
		given(mockSession.getMaxInactiveInterval()).willReturn(Duration.ofSeconds(maxInactiveIntervalInSeconds));

		return mockSession;
	}

	protected AbstractGemFireOperationsSessionRepository withRegion(
			AbstractGemFireOperationsSessionRepository sessionRepository, Region region) {

		((GemfireTemplate) sessionRepository.getTemplate()).setRegion(region);

		return sessionRepository;
	}

	@Test
	@SuppressWarnings("unchecked")
	public void constructGemFireOperationsSessionRepositoryAndInitialize() throws Exception {

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		AttributesMutator<Object, Session> mockAttributesMutator = mock(AttributesMutator.class);

		Region<Object, Session> mockRegion = mock(Region.class);

		when(mockRegion.getAttributesMutator()).thenReturn(mockAttributesMutator);
		when(mockRegion.getFullPath()).thenReturn(GemFireUtils.toRegionPath("Example"));

		GemfireTemplate template = new GemfireTemplate(mockRegion);

		AbstractGemFireOperationsSessionRepository sessionRepository =
			new TestGemFireOperationsSessionRepository(template);

		assertThat(sessionRepository.getApplicationEventPublisher()).isNotNull();
		assertThat(sessionRepository.getApplicationEventPublisher()).isNotEqualTo(mockApplicationEventPublisher);
		assertThat(sessionRepository.getFullyQualifiedRegionName()).isNull();
		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS);
		assertThat(sessionRepository.getTemplate()).isSameAs(template);

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.setMaxInactiveIntervalInSeconds(300);
		sessionRepository.afterPropertiesSet();

		assertThat(sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);
		assertThat(sessionRepository.getFullyQualifiedRegionName())
			.isEqualTo(GemFireUtils.toRegionPath("Example"));
		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(300);
		assertThat(sessionRepository.getTemplate()).isSameAs(template);

		verify(mockRegion, times(1)).getAttributesMutator();
		verify(mockRegion, times(1)).getFullPath();
		verify(mockAttributesMutator, times(1)).addCacheListener(same(sessionRepository));
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructGemFireOperationsSessionRepositoryWithNullTemplate() {

		try {
			new TestGemFireOperationsSessionRepository(null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("GemfireOperations is required");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@SuppressWarnings("all")
	@Test(expected = IllegalArgumentException.class)
	public void setApplicationEventListenerToNull() {

		try {
			this.sessionRepository.setApplicationEventPublisher(null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("ApplicationEventPublisher is required");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void maxInactiveIntervalInSecondsAllowsExtremelyLargeAndNegativeValues() {

		assertThat(this.sessionRepository.getMaxInactiveIntervalInSeconds())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS);

		this.sessionRepository.setMaxInactiveIntervalInSeconds(-1);

		assertThat(this.sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(-1);

		this.sessionRepository.setMaxInactiveIntervalInSeconds(Integer.MIN_VALUE);

		assertThat(this.sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(Integer.MIN_VALUE);

		this.sessionRepository.setMaxInactiveIntervalInSeconds(1024000);

		assertThat(this.sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(1024000);

		this.sessionRepository.setMaxInactiveIntervalInSeconds(Integer.MAX_VALUE);

		assertThat(this.sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(Integer.MAX_VALUE);
	}

	@Test
	public void isCreateWithCreateOperationReturnsTrue() {

		EntryEvent<Object, Session> mockEntryEvent =
			mockEntryEvent(Operation.CREATE, "12345", null, this.mockSession);

		withRegion(this.sessionRepository, mockRegion("Example", DataPolicy.EMPTY));

		assertThat(this.sessionRepository.isCreate(mockEntryEvent)).isTrue();

		verify(mockEntryEvent, times(1)).getOperation();
		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, times(1)).getNewValue();
		verify(mockEntryEvent, never()).getOldValue();
		verifyZeroInteractions(this.mockSession);
	}

	@Test
	public void isCreateWithCreateOperationAndNonProxyRegionReturnsTrue() {

		EntryEvent<Object, Session> mockEntryEvent =
			this.mockEntryEvent(Operation.CREATE, "12345", null, this.mockSession);

		withRegion(this.sessionRepository, mockRegion("Example", DataPolicy.NORMAL));

		this.sessionRepository.remember("12345");

		assertThat(this.sessionRepository.isCreate(mockEntryEvent)).isTrue();

		verify(mockEntryEvent, times(1)).getOperation();
		verify(mockEntryEvent, never()).getKey();
		verify(mockEntryEvent, times(1)).getNewValue();
		verify(mockEntryEvent, never()).getOldValue();
		verifyZeroInteractions(this.mockSession);
	}

	@Test
	public void isCreateWithLocalLoadCreateOperationReturnsFalse() {

		EntryEvent<Object, Session> mockEntryEvent =
			this.mockEntryEvent(Operation.LOCAL_LOAD_CREATE, "12345", null, this.mockSession);

		withRegion(this.sessionRepository, mockRegion("Example", DataPolicy.EMPTY));

		assertThat(this.sessionRepository.isCreate(mockEntryEvent)).isFalse();

		verify(mockEntryEvent, times(1)).getOperation();
		verify(mockEntryEvent, never()).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, never()).getOldValue();
		verifyZeroInteractions(this.mockSession);
	}

	@Test
	public void isCreateWithRememberedSessionIdReturnsFalse() {

		EntryEvent<Object, Session> mockEntryEvent =
			this.mockEntryEvent(Operation.CREATE, "12345", null, this.mockSession);

		withRegion(this.sessionRepository, mockRegion("Example", DataPolicy.EMPTY));

		this.sessionRepository.remember("12345");

		assertThat(this.sessionRepository.isCreate(mockEntryEvent)).isFalse();

		verify(mockEntryEvent, times(1)).getOperation();
		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, never()).getOldValue();
		verifyZeroInteractions(this.mockSession);
	}

	@Test
	public void isCreateWithUpdateOperationReturnsFalse() {

		Session mockOldValue = mock(Session.class);

		EntryEvent<Object, Session> mockEntryEvent =
			this.mockEntryEvent(Operation.UPDATE, "12345", mockOldValue, this.mockSession);

		withRegion(this.sessionRepository, mockRegion("Example", DataPolicy.EMPTY));

		assertThat(this.sessionRepository.isCreate(mockEntryEvent)).isFalse();

		verify(mockEntryEvent, times(1)).getOperation();
		verify(mockEntryEvent, never()).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, never()).getOldValue();
		verifyZeroInteractions(mockOldValue);
		verifyZeroInteractions(this.mockSession);
	}

	@Test
	public void isCreateWithTombstoneReturnsFalse() {

		EntryEvent<Object, Object> mockEntryEvent =
			this.mockEntryEvent(Operation.CREATE, "12345", null, new Tombstone());

		withRegion(this.sessionRepository, mockRegion("Example", DataPolicy.EMPTY));

		assertThat(this.sessionRepository.isCreate(mockEntryEvent)).isFalse();

		verify(mockEntryEvent, times(1)).getOperation();
		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, times(1)).getNewValue();
		verify(mockEntryEvent, never()).getOldValue();
		verifyZeroInteractions(this.mockSession);
	}

	@Test
	public void isCreateWithNullReturnsFalse() {

		EntryEvent<Object, Object> mockEntryEvent =
			this.mockEntryEvent(Operation.CREATE, "12345", null, null);

		withRegion(this.sessionRepository, mockRegion("Example", DataPolicy.EMPTY));

		assertThat(this.sessionRepository.isCreate(mockEntryEvent)).isFalse();

		verify(mockEntryEvent, times(1)).getOperation();
		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, times(1)).getNewValue();
		verify(mockEntryEvent, never()).getOldValue();
	}

	@Test
	public void toSessionWithSession() {
		assertThat(this.sessionRepository.toSession(this.mockSession, "12345")).isSameAs(this.mockSession);
	}

	@Test
	public void toSessionWithTombstoneAndSessionId() {

		Tombstone tombstone = new Tombstone();

		Session session = this.sessionRepository.toSession(tombstone, "12345");

		assertThat(session).isNotNull();
		assertThat(session).isNotSameAs(tombstone);
		assertThat(session.getId()).isEqualTo("12345");
	}

	@Test(expected = IllegalStateException.class)
	public void toSessionWithNullSessionAndEmptySessionId() {

		try {
			this.sessionRepository.toSession(null, "  ");
		}
		catch (IllegalStateException expected) {

			assertThat(expected).hasMessage("Minimally, the session ID [  ] must be known to trigger a Session event");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test(expected = IllegalStateException.class)
	public void toSessionWithNullSessionAndNullSessionId() {

		try {
			this.sessionRepository.toSession(null, null);
		}
		catch (IllegalStateException expected) {

			assertThat(expected).hasMessage("Minimally, the session ID [null] must be known to trigger a Session event");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void afterCreateHandlesNullEntryEvent() {

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		this.sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);

		assertThat(this.sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		this.sessionRepository.afterCreate(null);

		verify(this.sessionRepository, never()).handleCreated(anyString(), any());
		verifyZeroInteractions(mockApplicationEventPublisher);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterCreateWithNewSessionPublishesSessionCreatedEvent() {

		String sessionId = "12345";

		when(this.mockSession.getId()).thenReturn(sessionId);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(invocation -> {

			ApplicationEvent applicationEvent = invocation.getArgument(0);

			assertThat(applicationEvent).isInstanceOf(SessionCreatedEvent.class);

			AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

			assertThat(sessionEvent.<Session>getSession()).isEqualTo(this.mockSession);
			assertThat(sessionEvent.getSessionId()).isEqualTo(sessionId);
			assertThat(sessionEvent.getSource())
				.isEqualTo(AbstractGemFireOperationsSessionRepositoryTests.this.sessionRepository);

			return null;

		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, Session> mockEntryEvent =
			this.mockEntryEvent(Operation.CREATE, sessionId, null, this.mockSession);

		withRegion(this.sessionRepository, mockRegion("Example", DataPolicy.EMPTY));

		this.sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);

		assertThat(this.sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		this.sessionRepository.afterCreate(mockEntryEvent);

		verify(mockEntryEvent, times(1)).getOperation();
		verify(mockEntryEvent, times(2)).getKey();
		verify(mockEntryEvent, times(2)).getNewValue();
		verify(mockEntryEvent, never()).getOldValue();
		verify(this.mockLog, never()).error(anyString(), any(Throwable.class));
		verify(this.mockSession, times(1)).getId();
		verify(this.sessionRepository, times(1))
			.handleCreated(eq(sessionId), eq(this.mockSession));
		verify(mockApplicationEventPublisher, times(1))
			.publishEvent(isA(SessionCreatedEvent.class));
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void afterCreateForCreateOperationDoesNotPublishSessionCreatedEventWhenSessionIdIsRemembered() {

		EntryEvent<Object, Session> mockEntryEvent =
			this.mockEntryEvent(Operation.CREATE, "12345", null, this.mockSession);

		withRegion(this.sessionRepository, mockRegion("Example", DataPolicy.EMPTY));

		this.sessionRepository.remember("12345");
		this.sessionRepository.afterCreate(mockEntryEvent);

		verify(mockEntryEvent, times(1)).getOperation();
		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, never()).getOldValue();
		verifyZeroInteractions(this.mockSession);
		verify(this.sessionRepository, never()).handleCreated(anyString(), any());
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void afterCreateForLocalLoadCreateOperationDoesNotPublishSessionCreatedEvent() {

		EntryEvent<Object, Session> mockEntryEvent =
			this.mockEntryEvent(Operation.LOCAL_LOAD_CREATE, "12345", null, this.mockSession);

		withRegion(this.sessionRepository, mockRegion("Example", DataPolicy.REPLICATE));

		this.sessionRepository.afterCreate(mockEntryEvent);

		verify(mockEntryEvent, times(1)).getOperation();
		verify(mockEntryEvent, never()).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, never()).getOldValue();
		verifyZeroInteractions(this.mockSession);
		verify(this.sessionRepository, never()).handleCreated(anyString(), any());
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void afterCreateForDestroyOperationDoesNotPublishSessionCreatedEvent() {

		EntryEvent<Object, Session> mockEntryEvent =
			mockEntryEvent(Operation.DESTROY, "12345", null, null);

		this.sessionRepository.afterCreate(mockEntryEvent);

		verify(mockEntryEvent, times(1)).getOperation();
		verify(mockEntryEvent, never()).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, never()).getOldValue();
		verify(this.sessionRepository, never()).handleCreated(anyString(), any());
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void afterCreateForInvalidateOperationDoesNotPublishSessionCreatedEvent() {

		EntryEvent<Object, Session> mockEntryEvent =
			mockEntryEvent(Operation.INVALIDATE, "12345", null, this.mockSession);

		this.sessionRepository.afterCreate(mockEntryEvent);

		verify(mockEntryEvent, times(1)).getOperation();
		verify(mockEntryEvent, never()).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, never()).getOldValue();
		verifyZeroInteractions(this.mockSession);
		verify(this.sessionRepository, never()).handleCreated(anyString(), any());
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void afterCreateForUpdateOperationDoesNotPublishSessionCreatedEvent() {

		Session mockOldValue = mock(Session.class);

		EntryEvent<Object, Session> mockEntryEvent =
			mockEntryEvent(Operation.UPDATE, "12345", mockOldValue, this.mockSession);

		this.sessionRepository.afterCreate(mockEntryEvent);

		verify(mockEntryEvent, times(1)).getOperation();
		verify(mockEntryEvent, never()).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, never()).getOldValue();
		verifyZeroInteractions(mockOldValue);
		verifyZeroInteractions(this.mockSession);
		verify(this.sessionRepository, never()).handleCreated(anyString(), any());
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void afterCreateWithTombstoneDoesNotPublishSessionCreatedEvent() {

		EntryEvent mockEntryEvent = mockEntryEvent(Operation.CREATE, "12345", null, new Tombstone());

		withRegion(this.sessionRepository, mockRegion("Example", DataPolicy.EMPTY));

		this.sessionRepository.afterCreate(mockEntryEvent);

		verify(mockEntryEvent, times(1)).getOperation();
		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, times(1)).getNewValue();
		verify(mockEntryEvent, never()).getOldValue();
		verify(this.sessionRepository, never()).handleCreated(anyString(), any());
	}

	@Test
	public void afterDestroyHandlesNullEntryEvent() {

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		this.sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);

		assertThat(this.sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		this.sessionRepository.afterDestroy(null);

		verify(this.sessionRepository, never()).handleDestroyed(anyString(), any());
		verifyZeroInteractions(mockApplicationEventPublisher);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterDestroyWithSessionPublishesSessionDestroyedEvent() {

		String sessionId = "12345";

		when(this.mockSession.getId()).thenReturn(sessionId);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(invocation -> {

			ApplicationEvent applicationEvent = invocation.getArgument(0);

			assertThat(applicationEvent).isInstanceOf(SessionDestroyedEvent.class);

			AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

			assertThat(sessionEvent.<Session>getSession()).isEqualTo(this.mockSession);
			assertThat(sessionEvent.getSessionId()).isEqualTo(sessionId);
			assertThat(sessionEvent.getSource())
				.isEqualTo(AbstractGemFireOperationsSessionRepositoryTests.this.sessionRepository);

			return null;

		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, Session> mockEntryEvent =
			this.mockEntryEvent(Operation.DESTROY, sessionId, this.mockSession, null);

		this.sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);

		assertThat(this.sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		this.sessionRepository.afterDestroy(mockEntryEvent);

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(this.mockLog, never()).error(anyString(), any(Throwable.class));
		verify(this.mockSession, times(1)).getId();
		verify(this.sessionRepository, times(1))
			.handleDestroyed(eq(sessionId), isA(Session.class));
		verify(mockApplicationEventPublisher, times(1))
			.publishEvent(isA(SessionDestroyedEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterDestroyWithSessionIdPublishesSessionDestroyedEvent() {

		String sessionId = "12345";

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(invocation -> {

			ApplicationEvent applicationEvent = invocation.getArgument(0);

			assertThat(applicationEvent).isInstanceOf(SessionDestroyedEvent.class);

			AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

			Session session = sessionEvent.getSession();

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(sessionId);
			assertThat(sessionEvent.getSessionId()).isEqualTo(sessionId);
			assertThat(sessionEvent.getSource())
				.isEqualTo(AbstractGemFireOperationsSessionRepositoryTests.this.sessionRepository);

			return null;

		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, Session> mockEntryEvent =
			this.mockEntryEvent(Operation.DESTROY, sessionId, null, null);

		this.sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);

		assertThat(this.sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		this.sessionRepository.afterDestroy(mockEntryEvent);

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(this.mockLog, never()).error(anyString(), any(Throwable.class));
		verify(this.sessionRepository, times(1))
			.handleDestroyed(eq(sessionId), isA(Session.class));
		verify(mockApplicationEventPublisher, times(1))
			.publishEvent(isA(SessionDestroyedEvent.class));
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void afterDestroyWithTombstonePublishesSessionDestroyedEventWithSessionId() {

		String sessionId = "12345";

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(invocation -> {

			ApplicationEvent applicationEvent = invocation.getArgument(0);

			assertThat(applicationEvent).isInstanceOf(SessionDestroyedEvent.class);

			AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

			Session session = sessionEvent.getSession();

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(sessionId);
			assertThat(sessionEvent.getSessionId()).isEqualTo(sessionId);
			assertThat(sessionEvent.getSource())
				.isEqualTo(AbstractGemFireOperationsSessionRepositoryTests.this.sessionRepository);

			return null;

		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent mockEntryEvent = mockEntryEvent(Operation.DESTROY, sessionId, new Tombstone(), null);

		this.sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);

		assertThat(this.sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		this.sessionRepository.afterDestroy((EntryEvent<Object, Session>) mockEntryEvent);

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(this.mockLog, never()).error(anyString(), any(Throwable.class));
		verify(this.sessionRepository, times(1))
			.handleDestroyed(eq(sessionId), isA(Session.class));
		verify(mockApplicationEventPublisher, times(1))
			.publishEvent(isA(SessionDestroyedEvent.class));
	}

	@Test
	public void afterInvalidateHandlesNullEntryEvent() {

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		this.sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);

		assertThat(this.sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		this.sessionRepository.afterInvalidate(null);

		verify(this.sessionRepository, never()).handleExpired(anyString(), any());
		verifyZeroInteractions(mockApplicationEventPublisher);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterInvalidateWithSessionPublishesSessionExpiredEvent() {

		String sessionId = "12345";

		when(this.mockSession.getId()).thenReturn(sessionId);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(invocation -> {

			ApplicationEvent applicationEvent = invocation.getArgument(0);

			assertThat(applicationEvent).isInstanceOf(SessionExpiredEvent.class);

			AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

			assertThat(sessionEvent.<Session>getSession()).isEqualTo(this.mockSession);
			assertThat(sessionEvent.getSessionId()).isEqualTo(sessionId);
			assertThat(sessionEvent.getSource())
				.isEqualTo(AbstractGemFireOperationsSessionRepositoryTests.this.sessionRepository);

			return null;

		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, Session> mockEntryEvent =
			this.mockEntryEvent(Operation.INVALIDATE, sessionId, mockSession, null);

		this.sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);

		assertThat(this.sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		this.sessionRepository.afterInvalidate(mockEntryEvent);

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(this.mockLog, never()).error(anyString(), any(Throwable.class));
		verify(this.mockSession, times(1)).getId();
		verify(this.sessionRepository, times(1))
			.handleExpired(eq(sessionId), eq(this.mockSession));
		verify(mockApplicationEventPublisher, times(1))
			.publishEvent(isA(SessionExpiredEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterInvalidateWithSessionIdPublishesSessionExpiredEvent() {

		String sessionId = "12345";

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(invocation -> {

			ApplicationEvent applicationEvent = invocation.getArgument(0);

			assertThat(applicationEvent).isInstanceOf(SessionExpiredEvent.class);

			AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

			Session session = sessionEvent.getSession();

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(sessionId);
			assertThat(sessionEvent.getSessionId()).isEqualTo(sessionId);
			assertThat(sessionEvent.getSource())
				.isEqualTo(AbstractGemFireOperationsSessionRepositoryTests.this.sessionRepository);

			return null;

		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, Session> mockEntryEvent =
			this.mockEntryEvent(Operation.INVALIDATE, sessionId, null, null);

		this.sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);

		assertThat(this.sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		this.sessionRepository.afterInvalidate(mockEntryEvent);

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(this.mockLog, never()).error(anyString(), any(Throwable.class));
		verify(this.sessionRepository, times(1))
			.handleExpired(eq(sessionId), isA(Session.class));
		verify(mockApplicationEventPublisher, times(1))
			.publishEvent(isA(SessionExpiredEvent.class));
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void afterInvalidateWithTombstonePublishesSessionExpiredEventWithSessionId() {

		String sessionId = "12345";

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(invocation -> {

			ApplicationEvent applicationEvent = invocation.getArgument(0);

			assertThat(applicationEvent).isInstanceOf(SessionExpiredEvent.class);

			AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

			Session session = sessionEvent.getSession();

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(sessionId);
			assertThat(sessionEvent.getSessionId()).isEqualTo(sessionId);
			assertThat(sessionEvent.getSource())
				.isEqualTo(AbstractGemFireOperationsSessionRepositoryTests.this.sessionRepository);

			return null;

		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent mockEntryEvent = mockEntryEvent(Operation.INVALIDATE, sessionId, new Tombstone(), null);

		this.sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);

		assertThat(this.sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		this.sessionRepository.afterInvalidate((EntryEvent<Object, Session>) mockEntryEvent);

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(this.mockLog, never()).error(anyString(), any(Throwable.class));
		verify(this.sessionRepository, times(1))
			.handleExpired(eq(sessionId), isA(Session.class));
		verify(mockApplicationEventPublisher, times(1))
			.publishEvent(isA(SessionExpiredEvent.class));
	}

	@Test
	public void sessionCreateCreateExpireRecreatePublishesSessionEventsCreateExpireCreate() {

		String sessionId = "123456789";

		when(this.mockSession.getId()).thenReturn(sessionId);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		willAnswer(new Answer<Void>() {

			int index = 0;

			Class[] expectedSessionTypes = {
				SessionCreatedEvent.class, SessionExpiredEvent.class, SessionCreatedEvent.class
			};

			public Void answer(InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgument(0);

				assertThat(applicationEvent).isInstanceOf(this.expectedSessionTypes[this.index++]);

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.<Session>getSession()).isEqualTo(mockSession);
				assertThat(sessionEvent.getSessionId()).isEqualTo(sessionId);
				assertThat(sessionEvent.getSource())
					.isEqualTo(AbstractGemFireOperationsSessionRepositoryTests.this.sessionRepository);

				return null;
			}
		}).given(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, Session> mockCreateEvent =
			this.mockEntryEvent(Operation.CREATE, sessionId, null, this.mockSession);

		EntryEvent<Object, Session> mockExpireEvent =
			this.mockEntryEvent(Operation.INVALIDATE, sessionId, this.mockSession, null);

		withRegion(this.sessionRepository, mockRegion("Example", DataPolicy.EMPTY));

		this.sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		this.sessionRepository.afterCreate(mockCreateEvent);
		this.sessionRepository.afterCreate(mockCreateEvent);
		this.sessionRepository.afterInvalidate(mockExpireEvent);
		this.sessionRepository.afterCreate(mockCreateEvent);

		assertThat(this.sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		verify(mockCreateEvent, times(3)).getOperation();
		verify(mockCreateEvent, times(5)).getKey();
		verify(mockCreateEvent, times(4)).getNewValue();
		verify(mockCreateEvent, never()).getOldValue();
		verify(mockExpireEvent, never()).getOperation();
		verify(mockExpireEvent, times(1)).getKey();
		verify(mockExpireEvent, never()).getNewValue();
		verify(mockExpireEvent, times(1)).getOldValue();
		verify(this.mockLog, never()).error(anyString(), any(Throwable.class));
		verify(this.mockSession, times(3)).getId();
		verify(this.sessionRepository, times(2))
			.handleCreated(eq(sessionId), eq(this.mockSession));
		verify(this.sessionRepository, times(1))
			.handleExpired(eq(sessionId), eq(this.mockSession));
		verify(mockApplicationEventPublisher, times(2))
			.publishEvent(isA(SessionCreatedEvent.class));
		verify(mockApplicationEventPublisher, times(1))
			.publishEvent(isA(SessionExpiredEvent.class));
	}

	@Test
	public void deleteSessionCallsDeleteSessionId() {

		doNothing().when(this.sessionRepository).deleteById(anyString());
		when(this.mockSession.getId()).thenReturn("2");

		assertThat(this.sessionRepository.delete(this.mockSession)).isNull();

		verify(this.mockSession, times(1)).getId();
		verify(this.sessionRepository, times(1)).deleteById(eq("2"));
	}

	@Test
	public void handleDeletedWithSessionPublishesSessionDeletedEvent() {

		String sessionId = "12345";

		when(this.mockSession.getId()).thenReturn(sessionId);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(invocation -> {

			ApplicationEvent applicationEvent = invocation.getArgument(0);

			assertThat(applicationEvent).isInstanceOf(SessionDeletedEvent.class);

			AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

			assertThat(sessionEvent.<Session>getSession()).isEqualTo(this.mockSession);
			assertThat(sessionEvent.getSessionId()).isEqualTo(sessionId);
			assertThat(sessionEvent.getSource())
				.isEqualTo(AbstractGemFireOperationsSessionRepositoryTests.this.sessionRepository);

			return null;

		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		this.sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);

		assertThat(this.sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		this.sessionRepository.handleDeleted(sessionId, this.mockSession);

		verify(this.mockSession, times(1)).getId();
		verify(this.mockLog, never()).error(anyString(), any(Throwable.class));
		verify(mockApplicationEventPublisher, times(1))
			.publishEvent(isA(SessionDeletedEvent.class));
	}

	@Test
	public void publishEventHandlesThrowable() {

		ApplicationEvent mockApplicationEvent = mock(ApplicationEvent.class);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doThrow(new IllegalStateException("test")).when(mockApplicationEventPublisher)
			.publishEvent(any(ApplicationEvent.class));

		this.sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);

		assertThat(this.sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);

		this.sessionRepository.publishEvent(mockApplicationEvent);

		verify(mockApplicationEventPublisher, times(1)).publishEvent(eq(mockApplicationEvent));
		verify(this.mockLog, times(1))
			.error(eq(String.format("Error occurred while publishing event [%s]", mockApplicationEvent)),
				isA(IllegalStateException.class));
	}

	@Test
	public void touchSetsLastAccessedTime() {
		assertThat(this.sessionRepository.touch(this.mockSession)).isSameAs(this.mockSession);
		verify(this.mockSession, times(1)).setLastAccessedTime(any(Instant.class));
	}

	@Test
	public void constructGemFireSessionWithDefaultInitialization() {

		Instant beforeOrAtCreationTime = Instant.now();

		GemFireSession session = new GemFireSession();

		assertThat(session.getId()).isNotNull();
		assertThat(session.getCreationTime().compareTo(beforeOrAtCreationTime)).isGreaterThanOrEqualTo(0);
		assertThat(session.getLastAccessedTime().compareTo(beforeOrAtCreationTime)).isGreaterThanOrEqualTo(0);
		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ZERO);
		assertThat(session.getAttributeNames()).isNotNull();
		assertThat(session.getAttributeNames()).isEmpty();
	}

	@Test
	public void constructGemFireSessionWithId() {

		Instant beforeOrAtCreationTime = Instant.now();

		GemFireSession session = new GemFireSession("1");

		assertThat(session.getId()).isEqualTo("1");
		assertThat(session.getCreationTime().compareTo(beforeOrAtCreationTime)).isGreaterThanOrEqualTo(0);
		assertThat(session.getLastAccessedTime().compareTo(beforeOrAtCreationTime)).isGreaterThanOrEqualTo(0);
		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ZERO);
		assertThat(session.getAttributeNames()).isNotNull();
		assertThat(session.getAttributeNames()).isEmpty();
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructGemFireSessionWithUnspecifiedId() {

		try {
			new GemFireSession(" ");
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("ID is required");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void constructGemFireSessionWithSession() {

		Instant expectedCreationTime = Instant.ofEpochMilli(1L);
		Instant expectedLastAccessTime = Instant.ofEpochMilli(2L);

		Duration expectedMaxInactiveInterval = Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);

		Session mockSession = mockSession("2", expectedCreationTime.toEpochMilli(),
			expectedLastAccessTime.toEpochMilli(), MAX_INACTIVE_INTERVAL_IN_SECONDS);

		Set<String> expectedAttributedNames = asSet("attrOne", "attrTwo");

		given(mockSession.getAttributeNames()).willReturn(expectedAttributedNames);
		given(mockSession.getAttribute(eq("attrOne"))).willReturn("testOne");
		given(mockSession.getAttribute(eq("attrTwo"))).willReturn("testTwo");

		GemFireSession gemfireSession = new GemFireSession(mockSession);

		assertThat(gemfireSession.getId()).isEqualTo("2");
		assertThat(gemfireSession.getCreationTime()).isEqualTo(expectedCreationTime);
		assertThat(gemfireSession.getLastAccessedTime()).isEqualTo(expectedLastAccessTime);
		assertThat(gemfireSession.getMaxInactiveInterval()).isEqualTo(expectedMaxInactiveInterval);
		assertThat(gemfireSession.getAttributeNames()).isEqualTo(expectedAttributedNames);
		assertThat(gemfireSession.<String>getAttribute("attrOne")).isEqualTo("testOne");
		assertThat(gemfireSession.<String>getAttribute("attrTwo")).isEqualTo("testTwo");

		verify(mockSession, times(1)).getId();
		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
		verify(mockSession, times(1)).getMaxInactiveInterval();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, times(1)).getAttribute(eq("attrOne"));
		verify(mockSession, times(1)).getAttribute(eq("attrTwo"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructGemFireSessionWithNullSession() {

		try {
			new GemFireSession((Session) null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("The Session to copy cannot be null");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void createNewGemFireSession() {

		Instant beforeOrAtCreationTime = Instant.now();

		Duration maxInactiveInterval = Duration.ofSeconds(120L);

		GemFireSession<?> session = GemFireSession.create(maxInactiveInterval);

		assertThat(session).isNotNull();
		assertThat(session.getId()).isNotNull();
		assertThat(session.getCreationTime().compareTo(beforeOrAtCreationTime)).isGreaterThanOrEqualTo(0);
		assertThat(session.getLastAccessedTime()).isEqualTo(session.getCreationTime());
		assertThat(session.getMaxInactiveInterval()).isEqualTo(maxInactiveInterval);
		assertThat(session.getAttributeNames()).isNotNull();
		assertThat(session.getAttributeNames()).isEmpty();
	}

	@Test
	public void createNewGemFireSessionWithDefaultMaxInactiveInterval() {

		Instant beforeOrAtCreationTime = Instant.now();

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isNotNull();
		assertThat(session.getId()).isNotNull();
		assertThat(session.getCreationTime().compareTo(beforeOrAtCreationTime)).isGreaterThanOrEqualTo(0);
		assertThat(session.getLastAccessedTime()).isEqualTo(session.getCreationTime());
		assertThat(session.getMaxInactiveInterval()).isEqualTo(GemFireSession.DEFAULT_MAX_INACTIVE_INTERVAL);
		assertThat(session.getAttributeNames()).isNotNull();
		assertThat(session.getAttributeNames()).isEmpty();
	}

	@Test
	public void fromExistingSession() {

		Instant expectedCreationTime = Instant.ofEpochMilli(1L);
		Instant expectedLastAccessedTime = Instant.ofEpochMilli(2L);

		Duration expectedMaxInactiveInterval = Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);

		Session mockSession = mockSession("4", expectedCreationTime.toEpochMilli(),
			expectedLastAccessedTime.toEpochMilli(), MAX_INACTIVE_INTERVAL_IN_SECONDS);

		given(mockSession.getAttributeNames()).willReturn(Collections.emptySet());

		GemFireSession<?> gemfireSession = GemFireSession.from(mockSession);

		assertThat(gemfireSession).isNotNull();
		assertThat(gemfireSession.getId()).isEqualTo("4");
		assertThat(gemfireSession.getCreationTime()).isEqualTo(expectedCreationTime);
		assertThat(gemfireSession.getLastAccessedTime()).isEqualTo(expectedLastAccessedTime);
		assertThat(gemfireSession.getMaxInactiveInterval()).isEqualTo(expectedMaxInactiveInterval);
		assertThat(gemfireSession.getAttributeNames()).isNotNull();
		assertThat(gemfireSession.getAttributeNames().isEmpty()).isTrue();

		verify(mockSession, times(1)).getId();
		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
		verify(mockSession, times(1)).getMaxInactiveInterval();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, never()).getAttribute(anyString());
	}

	@Test
	public void fromExistingGemFireSessionIsGemFireSession() {

		GemFireSession<?> gemfireSession = GemFireSession.create();

		GemFireSession<?> fromGemFireSession = GemFireSession.from(gemfireSession);

		assertThat(fromGemFireSession).isSameAs(gemfireSession);
	}

	@Test
	public void setGetAndRemoveAttribute() {

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isNotNull();
		assertThat(session.getAttributeNames()).isEmpty();

		session.setAttribute("attrOne", "testOne");

		assertThat(session.getAttributeNames()).isEqualTo(asSet("attrOne"));
		assertThat(session.<String>getAttribute("attrOne")).isEqualTo("testOne");
		assertThat(session.<String>getAttribute("attrTwo")).isNull();

		session.setAttribute("attrTwo", "testTwo");

		assertThat(session.getAttributeNames()).isEqualTo(asSet("attrOne", "attrTwo"));
		assertThat(session.<String>getAttribute("attrOne")).isEqualTo("testOne");
		assertThat(session.<String>getAttribute("attrTwo")).isEqualTo("testTwo");

		session.setAttribute("attrTwo", null);

		assertThat(session.getAttributeNames()).isEqualTo(asSet("attrOne"));
		assertThat(session.<String>getAttribute("attrOne")).isEqualTo("testOne");
		assertThat(session.<String>getAttribute("attrTwo")).isNull();

		session.removeAttribute("attrOne");

		assertThat(session.<String>getAttribute("attrOne")).isNull();
		assertThat(session.<String>getAttribute("attrTwo")).isNull();
		assertThat(session.getAttributeNames().isEmpty()).isTrue();
	}

	@Test
	public void isExpiredIsFalseWhenMaxInactiveIntervalIsNegative() {

		Duration expectedMaxInactiveIntervalInSeconds = Duration.ofSeconds(-1);

		GemFireSession<?> session = GemFireSession.create(expectedMaxInactiveIntervalInSeconds);

		assertThat(session).isNotNull();
		assertThat(session.getMaxInactiveInterval()).isEqualTo(expectedMaxInactiveIntervalInSeconds);
		assertThat(session.isExpired()).isFalse();
	}

	@Test
	public void isExpiredIsFalseWhenMaxInactiveIntervalIsZero() {

		Duration expectedMaxInactiveIntervalInSeconds = Duration.ZERO;

		GemFireSession<?> session = GemFireSession.create(expectedMaxInactiveIntervalInSeconds);

		assertThat(session).isNotNull();
		assertThat(session.getMaxInactiveInterval()).isEqualTo(expectedMaxInactiveIntervalInSeconds);
		assertThat(session.isExpired()).isFalse();
	}

	@Test
	public void isExpiredIsFalseWhenSessionIsActive() {

		long expectedMaxInactiveIntervalInSeconds = TimeUnit.HOURS.toSeconds(2);

		GemFireSession<?> session = GemFireSession.create(Duration.ofSeconds(expectedMaxInactiveIntervalInSeconds));

		assertThat(session).isNotNull();
		assertThat(session.getMaxInactiveInterval())
			.isEqualTo(Duration.ofSeconds(expectedMaxInactiveIntervalInSeconds));

		Instant now = Instant.now();

		session.setLastAccessedTime(now);

		assertThat(session.getLastAccessedTime()).isEqualTo(now);
		assertThat(session.isExpired()).isFalse();
	}

	@Test
	public void isExpiredIsTrueWhenSessionIsInactive() {

		int expectedMaxInactiveIntervalInSeconds = 60;

		GemFireSession<?> session = GemFireSession.create(Duration.ofSeconds(expectedMaxInactiveIntervalInSeconds));

		assertThat(session).isNotNull();
		assertThat(session.getMaxInactiveInterval())
			.isEqualTo(Duration.ofSeconds(expectedMaxInactiveIntervalInSeconds));

		Instant twoHoursAgo = Instant.ofEpochMilli(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2));

		session.setLastAccessedTime(twoHoursAgo);

		assertThat(session.getLastAccessedTime()).isEqualTo(twoHoursAgo);
		assertThat(session.isExpired()).isTrue();
	}

	@Test
	public void setAndGetPrincipalName() {

		GemFireSession<?> session = GemFireSession.create(Duration.ZERO);

		assertThat(session).isNotNull();
		assertThat(session.getPrincipalName()).isNull();

		session.setPrincipalName("jblum");

		assertThat(session.getPrincipalName()).isEqualTo("jblum");
		assertThat(session.getAttributeNames()).isEqualTo(asSet(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME));
		assertThat(session.<String>getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME)).isEqualTo("jblum");

		session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "rwinch");

		assertThat(session.getAttributeNames()).isEqualTo(asSet(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME));
		assertThat(session.<String>getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME)).isEqualTo("rwinch");
		assertThat(session.getPrincipalName()).isEqualTo("rwinch");

		session.removeAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);

		assertThat(session.getPrincipalName()).isNull();
	}

	@Test
	public void hasDeltaWhenNoSessionChangesIsFalse() {
		assertThat(new AbstractGemFireOperationsSessionRepository.GemFireSession().hasDelta()).isFalse();
	}

	@Test
	public void hasDeltaWhenSessionAttributesChangeIsTrue() {

		GemFireSession session = new DeltaCapableGemFireSession();

		assertThat(session.hasDelta()).isFalse();

		session.setAttribute("attrOne", "test");

		assertThat(session.hasDelta()).isTrue();
	}

	@Test
	public void hasDeltaWhenSessionLastAccessedTimeIsUpdatedIsTrue() {

		Instant expectedLastAccessTime = Instant.ofEpochMilli(1L);

		AbstractGemFireOperationsSessionRepository.GemFireSession session =
			new AbstractGemFireOperationsSessionRepository.GemFireSession();

		assertThat(session.getLastAccessedTime()).isNotEqualTo(expectedLastAccessTime);
		assertThat(session.hasDelta()).isFalse();

		session.setLastAccessedTime(expectedLastAccessTime);

		assertThat(session.getLastAccessedTime()).isEqualTo(expectedLastAccessTime);
		assertThat(session.hasDelta()).isTrue();

		session.setLastAccessedTime(expectedLastAccessTime);

		assertThat(session.getLastAccessedTime()).isEqualTo(expectedLastAccessTime);
		assertThat(session.hasDelta()).isTrue();
	}

	@Test
	public void hasDeltaWhenSessionMaxInactiveIntervalInSecondsIsUpdatedIsTrue() {

		Duration expectedMaxInactiveIntervalInSeconds = Duration.ofSeconds(300L);

		AbstractGemFireOperationsSessionRepository.GemFireSession session =
			new AbstractGemFireOperationsSessionRepository.GemFireSession();

		assertThat(session.getMaxInactiveInterval()).isNotEqualTo(expectedMaxInactiveIntervalInSeconds);
		assertThat(session.hasDelta()).isFalse();

		session.setMaxInactiveInterval(expectedMaxInactiveIntervalInSeconds);

		assertThat(session.getMaxInactiveInterval()).isEqualTo(expectedMaxInactiveIntervalInSeconds);
		assertThat(session.hasDelta()).isTrue();

		session.setMaxInactiveInterval(expectedMaxInactiveIntervalInSeconds);

		assertThat(session.getMaxInactiveInterval()).isEqualTo(expectedMaxInactiveIntervalInSeconds);
		assertThat(session.hasDelta()).isTrue();
	}

	@Test
	public void sessionToDelta() throws Exception {

		DataOutput mockDataOutput = mock(DataOutput.class);

		@SuppressWarnings("serial")
		DeltaCapableGemFireSession session = new DeltaCapableGemFireSession();

		session.setLastAccessedTime(Instant.ofEpochMilli(1L));
		session.setMaxInactiveInterval(Duration.ofSeconds(300L));
		session.setAttribute("attrOne", "test");

		assertThat(session.hasDelta()).isTrue();

		session.toDelta(mockDataOutput);

		assertThat(session.hasDelta()).isFalse();

		verify(mockDataOutput, times(1)).writeLong(eq(1L));
		verify(mockDataOutput, times(1)).writeLong(eq(300L));
		verify(mockDataOutput, times(1)).writeInt(eq(1));
		verify(mockDataOutput, times(1)).writeUTF(eq("attrOne"));
	}

	@Test
	public void sessionFromDelta() throws Exception {

		DataInput mockDataInput = mock(DataInput.class);

		given(mockDataInput.readUTF()).willReturn("1");
		given(mockDataInput.readLong()).willReturn(1L).willReturn(600L);
		given(mockDataInput.readInt()).willReturn(0);

		@SuppressWarnings("serial")
		DeltaCapableGemFireSession session = new DeltaCapableGemFireSession();

		session.fromDelta(mockDataInput);

		assertThat(session.hasDelta()).isFalse();
		assertThat(session.getId()).isEqualTo("1");
		assertThat(session.getLastAccessedTime()).isEqualTo(Instant.ofEpochMilli(1L));
		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(600L));
		assertThat(session.getAttributeNames().isEmpty()).isTrue();

		verify(mockDataInput, times(1)).readUTF();
		verify(mockDataInput, times(2)).readLong();
		verify(mockDataInput, times(1)).readInt();
	}

	@Test
	public void sessionComparisons() {

		Instant twoHoursAgo = Instant.now().minusMillis(TimeUnit.HOURS.toMillis(2));

		GemFireSession<?> sessionOne =
			new GemFireSession<>(mockSession("1", twoHoursAgo.toEpochMilli(), MAX_INACTIVE_INTERVAL_IN_SECONDS));

		GemFireSession<?> sessionTwo = new GemFireSession<>("2");

		assertThat(sessionOne.getCreationTime()).isEqualTo(twoHoursAgo);
		assertThat(sessionTwo.getCreationTime().isAfter(twoHoursAgo)).isTrue();
		assertThat(sessionOne.compareTo(sessionTwo)).isLessThan(0);
		assertThat(sessionOne.compareTo(sessionOne)).isEqualTo(0);
		assertThat(sessionTwo.compareTo(sessionOne)).isGreaterThan(0);
	}

	@Test
	public void sessionEqualsDifferentSessionBasedOnId() {

		GemFireSession sessionOne = new GemFireSession("1");

		sessionOne.setLastAccessedTime(Instant.ofEpochSecond(12345L));
		sessionOne.setMaxInactiveInterval(Duration.ofSeconds(120L));
		sessionOne.setPrincipalName("jblum");

		GemFireSession sessionTwo = new GemFireSession("1");

		sessionTwo.setLastAccessedTime(Instant.ofEpochSecond(67890L));
		sessionTwo.setMaxInactiveInterval(Duration.ofSeconds(300L));
		sessionTwo.setPrincipalName("rwinch");

		assertThat(sessionOne.getId().equals(sessionTwo.getId())).isTrue();
		assertThat(sessionOne.getLastAccessedTime() == sessionTwo.getLastAccessedTime()).isFalse();
		assertThat(sessionOne.getMaxInactiveInterval() == sessionTwo.getMaxInactiveInterval()).isFalse();
		assertThat(sessionOne.getPrincipalName().equals(sessionTwo.getPrincipalName())).isFalse();
		assertThat(sessionOne.equals(sessionTwo)).isTrue();
	}

	@Test
	public void sessionHashCodeIsNotEqualToStringIdHashCode() {

		GemFireSession session = new GemFireSession("1");

		assertThat(session.getId()).isEqualTo("1");
		assertThat(session.hashCode()).isNotEqualTo("1".hashCode());
	}

	@Test
	public void sessionAttributesFromSession() {

		Session mockSession = mock(Session.class);

		given(mockSession.getAttributeNames()).willReturn(asSet("attrOne", "attrTwo"));
		given(mockSession.getAttribute(eq("attrOne"))).willReturn("testOne");
		given(mockSession.getAttribute(eq("attrTwo"))).willReturn("testTwo");

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		assertThat(sessionAttributes.getAttributeNames().isEmpty()).isTrue();

		sessionAttributes.from(mockSession);

		assertThat(sessionAttributes.getAttributeNames().size()).isEqualTo(2);
		assertThat(sessionAttributes.getAttributeNames().containsAll(asSet("attrOne", "attrTwo"))).isTrue();
		assertThat(sessionAttributes.<String>getAttribute("attrOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.<String>getAttribute("attrTwo")).isEqualTo("testTwo");

		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, times(1)).getAttribute(eq("attrOne"));
		verify(mockSession, times(1)).getAttribute(eq("attrTwo"));
	}

	@Test
	public void sessionAttributesFromSessionAttributes() {

		AbstractGemFireOperationsSessionRepository.GemFireSessionAttributes source =
			new AbstractGemFireOperationsSessionRepository.GemFireSessionAttributes();

		source.setAttribute("attrOne", "testOne");
		source.setAttribute("attrTwo", "testTwo");

		GemFireSessionAttributes target = new GemFireSessionAttributes();

		assertThat(target.getAttributeNames().isEmpty()).isTrue();

		target.from(source);

		assertThat(target.getAttributeNames().size()).isEqualTo(2);
		assertThat(target.getAttributeNames().containsAll(asSet("attrOne", "attrTwo"))).isTrue();
		assertThat(target.<String>getAttribute("attrOne")).isEqualTo("testOne");
		assertThat(target.<String>getAttribute("attrTwo")).isEqualTo("testTwo");
	}

	@Test
	public void sessionAttributesHasDeltaIsFalse() {
		assertThat(new AbstractGemFireOperationsSessionRepository.GemFireSessionAttributes().hasDelta()).isFalse();
	}

	@Test
	public void sessionAttributesHasDeltaIsTrue() {

		GemFireSessionAttributes sessionAttributes = new DeltaCapableGemFireSessionAttributes();

		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.setAttribute("attrOne", "testOne");

		assertThat(sessionAttributes.<String>getAttribute("attrOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isTrue();
	}

	@Test
	public void sessionAttributesToDelta() throws Exception {

		DataOutput mockDataOutput = mock(DataOutput.class);

		@SuppressWarnings("serial")
		DeltaCapableGemFireSessionAttributes sessionAttributes = new DeltaCapableGemFireSessionAttributes() {

				private int count = 0;

				@Override
				protected void writeObject(Object obj, DataOutput out) throws IOException {
					assertThat(Arrays.asList("testOne", "testTwo", "testThree").get(count++)).isEqualTo(String.valueOf(obj));
					assertThat(out).isSameAs(mockDataOutput);
				}
		};

		sessionAttributes.setAttribute("attrOne", "testOne");
		sessionAttributes.setAttribute("attrTwo", "testTwo");

		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.toDelta(mockDataOutput);

		assertThat(sessionAttributes.hasDelta()).isFalse();

		verify(mockDataOutput, times(1)).writeInt(eq(2));
		verify(mockDataOutput, times(1)).writeUTF("attrOne");
		verify(mockDataOutput, times(1)).writeUTF("attrTwo");
		reset(mockDataOutput);

		sessionAttributes.setAttribute("attrOne", "testOne");

		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.toDelta(mockDataOutput);

		verify(mockDataOutput, times(1)).writeInt(eq(0));
		verify(mockDataOutput, never()).writeUTF(any(String.class));
		reset(mockDataOutput);

		sessionAttributes.setAttribute("attrTwo", "testThree");

		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.toDelta(mockDataOutput);

		verify(mockDataOutput, times(1)).writeInt(eq(1));
		verify(mockDataOutput, times(1)).writeUTF(eq("attrTwo"));
	}

	@Test
	public void sessionAttributesFromDelta() throws Exception {

		DataInput mockDataInput = mock(DataInput.class);

		given(mockDataInput.readInt()).willReturn(2);
		given(mockDataInput.readUTF()).willReturn("attrOne").willReturn("attrTwo");

		@SuppressWarnings("serial")
		DeltaCapableGemFireSessionAttributes sessionAttributes = new DeltaCapableGemFireSessionAttributes() {

				private int count = 0;

				@Override
				@SuppressWarnings("unchecked")
				protected <T> T readObject(DataInput in) throws ClassNotFoundException, IOException {
					assertThat(in).isSameAs(mockDataInput);
					return (T) Arrays.asList("testOne", "testTwo", "testThree").get(count++);
				}
		};

		sessionAttributes.setAttribute("attrOne", "one");
		sessionAttributes.setAttribute("attrTwo", "two");

		assertThat(sessionAttributes.getAttributeNames().size()).isEqualTo(2);
		assertThat(sessionAttributes.getAttributeNames().containsAll(asSet("attrOne", "attrTwo"))).isTrue();
		assertThat(sessionAttributes.<String>getAttribute("attrOne")).isEqualTo("one");
		assertThat(sessionAttributes.<String>getAttribute("attrTwo")).isEqualTo("two");
		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.fromDelta(mockDataInput);

		assertThat(sessionAttributes.getAttributeNames().size()).isEqualTo(2);
		assertThat(sessionAttributes.getAttributeNames().containsAll(asSet("attrOne", "attrTwo"))).isTrue();
		assertThat(sessionAttributes.<String>getAttribute("attrOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.<String>getAttribute("attrTwo")).isEqualTo("testTwo");
		assertThat(sessionAttributes.hasDelta()).isFalse();

		verify(mockDataInput, times(1)).readInt();
		verify(mockDataInput, times(2)).readUTF();
		reset(mockDataInput);

		given(mockDataInput.readInt()).willReturn(1);
		given(mockDataInput.readUTF()).willReturn("attrTwo");

		sessionAttributes.setAttribute("attrOne", "one");
		sessionAttributes.setAttribute("attrTwo", "two");

		assertThat(sessionAttributes.getAttributeNames().size()).isEqualTo(2);
		assertThat(sessionAttributes.getAttributeNames().containsAll(asSet("attrOne", "attrTwo"))).isTrue();
		assertThat(sessionAttributes.<String>getAttribute("attrOne")).isEqualTo("one");
		assertThat(sessionAttributes.<String>getAttribute("attrTwo")).isEqualTo("two");
		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.fromDelta(mockDataInput);

		assertThat(sessionAttributes.getAttributeNames().size()).isEqualTo(2);
		assertThat(sessionAttributes.getAttributeNames().containsAll(asSet("attrOne", "attrTwo"))).isTrue();
		assertThat(sessionAttributes.<String>getAttribute("attrOne")).isEqualTo("one");
		assertThat(sessionAttributes.<String>getAttribute("attrTwo")).isEqualTo("testThree");
		assertThat(sessionAttributes.hasDelta()).isTrue();

		verify(mockDataInput, times(1)).readInt();
		verify(mockDataInput, times(1)).readUTF();
	}

	@Test
	public void sessionAttributesEntrySetIteratesAttributeNameValues() {

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		sessionAttributes.setAttribute("keyOne", "valueOne");
		sessionAttributes.setAttribute("keyTwo", "valueTwo");

		Set<Map.Entry<String, Object>> sessionAttributeEntries = sessionAttributes.entrySet();

		assertThat(sessionAttributeEntries).isNotNull();
		assertThat(sessionAttributeEntries.size()).isEqualTo(2);

		Set<String> expectedNames = new HashSet<>(asSet("keyOne", "keyTwo"));
		Set<?> expectedValues = new HashSet<>(asSet("valueOne", "valueTwo"));

		for (Map.Entry<String, Object> entry : sessionAttributeEntries) {
			expectedNames.remove(entry.getKey());
			expectedValues.remove(entry.getValue());
		}

		assertThat(expectedNames.isEmpty()).isTrue();
		assertThat(expectedValues.isEmpty()).isTrue();

		sessionAttributes.setAttribute("keyThree", "valueThree");

		assertThat(sessionAttributeEntries.size()).isEqualTo(3);

		expectedNames = new HashSet<>(asSet("keyOne", "keyTwo"));
		expectedValues = new HashSet<>(asSet("valueOne", "valueTwo"));

		for (Map.Entry<String, Object> entry : sessionAttributeEntries) {
			expectedNames.remove(entry.getKey());
			expectedValues.remove(entry.getValue());
		}

		assertThat(expectedNames.isEmpty()).isTrue();
		assertThat(expectedValues.isEmpty()).isTrue();

		sessionAttributes.removeAttribute("keyOne");
		sessionAttributes.removeAttribute("keyTwo");

		assertThat(sessionAttributeEntries.size()).isEqualTo(1);

		Map.Entry<String, ?> entry = sessionAttributeEntries.iterator().next();

		assertThat(entry.getKey()).isEqualTo("keyThree");
		assertThat(entry.getValue()).isEqualTo("valueThree");
	}

	@Test
	public void sessionWithAttributesAreThreadSafe() throws Throwable {
		TestFramework.runOnce(new ThreadSafeSessionTest());
	}

	@SuppressWarnings("unused")
	protected static final class ThreadSafeSessionTest extends MultithreadedTestCase {

		private GemFireSession<?> session;

		private final Instant beforeOrAtCreationTime = Instant.now();

		private volatile Instant expectedCreationTime;

		@Override
		public void initialize() {

			this.session = new GemFireSession<>("1");

			assertThat(this.session).isNotNull();
			assertThat(this.session.getId()).isEqualTo("1");
			assertThat(this.session.getCreationTime().compareTo(beforeOrAtCreationTime)).isGreaterThanOrEqualTo(0);
			assertThat(this.session.getLastAccessedTime()).isEqualTo(this.session.getCreationTime());
			assertThat(this.session.getMaxInactiveInterval()).isEqualTo(Duration.ZERO);
			assertThat(this.session.getPrincipalName()).isNull();
			assertThat(this.session.getAttributeNames()).isEmpty();

			this.expectedCreationTime = this.session.getCreationTime();

			this.session.setLastAccessedTime(Instant.MIN);
			this.session.setMaxInactiveInterval(Duration.ofSeconds(60L));
			this.session.setPrincipalName("jblum");
		}

		public void thread1() {

			assertTick(0);

			Thread.currentThread().setName("HTTP Request Processing Thread 1");

			assertThat(this.session).isNotNull();
			assertThat(this.session.getId()).isEqualTo("1");
			assertThat(this.session.getCreationTime()).isEqualTo(this.expectedCreationTime);
			assertThat(this.session.getLastAccessedTime()).isEqualTo(Instant.MIN);
			assertThat(this.session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(60L));
			assertThat(this.session.getPrincipalName()).isEqualTo("jblum");
			assertThat(this.session.getAttributeNames()).hasSize(1);
			assertThat(this.session.<String>getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME)).isEqualTo("jblum");

			this.session.setAttribute("tennis", "ping");
			this.session.setAttribute("junk", "test");
			this.session.setLastAccessedTime(Instant.ofEpochSecond(1L));
			this.session.setMaxInactiveInterval(Duration.ofSeconds(120L));
			this.session.setPrincipalName("rwinch");

			waitForTick(2);

			assertThat(this.session).isNotNull();
			assertThat(this.session.getId()).isEqualTo("1");
			assertThat(this.session.getCreationTime()).isEqualTo(this.expectedCreationTime);
			assertThat(this.session.getLastAccessedTime()).isEqualTo(Instant.ofEpochSecond(2L));
			assertThat(this.session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(180L));
			assertThat(this.session.getPrincipalName()).isEqualTo("rwinch");
			assertThat(this.session.getAttributeNames()).hasSize(3);
			assertThat(this.session.getAttributeNames()).containsAll(asSet("tennis", "greeting"));
			assertThat(this.session.getAttributeNames().contains("junk")).isFalse();
			assertThat(this.session.<String>getAttribute("junk")).isNull();
			assertThat(this.session.<String>getAttribute("tennis")).isEqualTo("pong");
			assertThat(this.session.<String>getAttribute("greeting")).isEqualTo("hello");
		}

		public void thread2() {

			assertTick(0);

			Thread.currentThread().setName("HTTP Request Processing Thread 2");

			waitForTick(1);
			assertTick(1);

			assertThat(this.session).isNotNull();
			assertThat(this.session.getId()).isEqualTo("1");
			assertThat(this.session.getCreationTime()).isEqualTo(this.expectedCreationTime);
			assertThat(this.session.getLastAccessedTime()).isEqualTo(Instant.ofEpochSecond(1L));
			assertThat(this.session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(120L));
			assertThat(this.session.getPrincipalName()).isEqualTo("rwinch");
			assertThat(this.session.getAttributeNames()).hasSize(3);
			assertThat(this.session.getAttributeNames()).containsAll(asSet("tennis", "junk"));
			assertThat(this.session.<String>getAttribute("junk")).isEqualTo("test");
			assertThat(this.session.<String>getAttribute("tennis")).isEqualTo("ping");

			this.session.setAttribute("tennis", "pong");
			this.session.setAttribute("greeting", "hello");
			this.session.removeAttribute("junk");
			this.session.setLastAccessedTime(Instant.ofEpochSecond(2L));
			this.session.setMaxInactiveInterval(Duration.ofSeconds(180L));
			this.session.setPrincipalName("rwinch");
		}

		@Override
		public void finish() {
			this.session = null;
		}
	}

	static class TestGemFireOperationsSessionRepository extends AbstractGemFireOperationsSessionRepository {

		TestGemFireOperationsSessionRepository(GemfireOperations gemfireOperations) {
			super(gemfireOperations);
		}

		public Session createSession() {
			throw new UnsupportedOperationException("Not Implemented");
		}

		public Session findById(String id) {
			throw new UnsupportedOperationException("Not Implemented");
		}

		public Map<String, Session> findByIndexNameAndIndexValue(String indexName, String indexValue) {
			throw new UnsupportedOperationException("Not Implemented");
		}

		public void save(Session session) {
			throw new UnsupportedOperationException("Not Implemented");
		}

		public void deleteById(String id) {
			throw new UnsupportedOperationException("Not Implemented");
		}
	}

	static class Tombstone {
	}
}
