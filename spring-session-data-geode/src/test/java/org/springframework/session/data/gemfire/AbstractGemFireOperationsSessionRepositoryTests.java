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
import static org.mockito.Mockito.withSettings;
import static org.springframework.data.gemfire.util.CollectionUtils.asSet;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSession;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSessionAttributes;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSessionAttributes;

import java.io.DataInput;
import java.io.DataOutput;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.mockito.junit.MockitoJUnitRunner
 * @see org.mockito.Spy
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.apache.geode.cache.Region
 * @see edu.umd.cs.mtc.MultithreadedTestCase
 * @see edu.umd.cs.mtc.TestFramework
 * @since 1.1.0
 */
@RunWith(MockitoJUnitRunner.class)
public class AbstractGemFireOperationsSessionRepositoryTests {

	protected static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 300;

	private AbstractGemFireOperationsSessionRepository sessionRepository;

	@Mock
	private Log mockLog;

	@Mock
	private Region<Object, Session> mockRegion;

	@Mock
	private Session mockSession;

	@Before
	@SuppressWarnings("all")
	public void setup() {

		GemfireTemplate gemfireTemplate = new GemfireTemplate(this.mockRegion);

		this.sessionRepository = spy(new TestGemFireOperationsSessionRepository(gemfireTemplate));
		this.sessionRepository.setUseDataSerialization(false);

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

	private Session mockSession() {

		String sessionId = UUID.randomUUID().toString();

		Instant now = Instant.now();

		Duration maxInactiveInterval = Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);

		Session mockSession = mock(Session.class, withSettings().name(sessionId).lenient());

		when(mockSession.getId()).thenReturn(sessionId);
		when(mockSession.getAttributeNames()).thenReturn(Collections.emptySet());
		when(mockSession.getCreationTime()).thenReturn(now);
		when(mockSession.getLastAccessedTime()).thenReturn(now);
		when(mockSession.getMaxInactiveInterval()).thenReturn(maxInactiveInterval);

		return mockSession;
	}

	private Session mockSession(String sessionId, long creationAndLastAccessedTime, long maxInactiveInterval) {
		return mockSession(sessionId, creationAndLastAccessedTime, creationAndLastAccessedTime, maxInactiveInterval);
	}

	private Session mockSession(String sessionId, long creationTime, long lastAccessedTime, long maxInactiveInterval) {

		Session mockSession = mock(Session.class, sessionId);

		when(mockSession.getId()).thenReturn(sessionId);
		when(mockSession.getCreationTime()).thenReturn(Instant.ofEpochMilli(creationTime));
		when(mockSession.getLastAccessedTime()).thenReturn(Instant.ofEpochMilli(lastAccessedTime));
		when(mockSession.getMaxInactiveInterval()).thenReturn(Duration.ofSeconds(maxInactiveInterval));

		return mockSession;
	}

	private GemFireSession newNonDirtyGemFireSession() {

		GemFireSession session = GemFireSession.create();

		session.commit();

		return session;
	}

	private AbstractGemFireOperationsSessionRepository withRegion(
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
	public void setAndGetMaxInactiveInterval() {

		assertThat(this.sessionRepository.getMaxInactiveInterval())
			.isEqualTo(Duration.ofSeconds(GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS));

		Duration tenMinutes = Duration.ofMinutes(10);

		this.sessionRepository.setMaxInactiveInterval(tenMinutes);

		assertThat(this.sessionRepository.getMaxInactiveInterval()).isEqualTo(tenMinutes);

		this.sessionRepository.setMaxInactiveIntervalInSeconds(300);

		assertThat(this.sessionRepository.getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(5));

		this.sessionRepository.setMaxInactiveInterval(null);

		assertThat(this.sessionRepository.getMaxInactiveInterval()).isNull();
	}

	@Test
	public void setAndIsUsingDataSerialization() {

		assertThat(GemFireOperationsSessionRepository.isUsingDataSerialization()).isFalse();

		this.sessionRepository.setUseDataSerialization(true);

		assertThat(GemFireOperationsSessionRepository.isUsingDataSerialization()).isTrue();

		this.sessionRepository.setUseDataSerialization(false);

		assertThat(GemFireOperationsSessionRepository.isUsingDataSerialization()).isFalse();
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
	public void commitGemFireSessionIsCorrect() {

		GemFireSession<?> session = spy(GemFireSession.create());

		assertThat(session).isNotNull();
		assertThat(session.hasDelta()).isTrue();

		this.sessionRepository.commit(session);

		assertThat(session.hasDelta()).isFalse();

		verify(session, times(1)).commit();
	}

	@Test
	public void commitNonGemFireSessionIsSafe() {
		this.sessionRepository.commit(this.mockSession);
	}

	@Test
	public void commitNullIsSafe() {
		this.sessionRepository.commit(null);
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

		Instant testCreationTime = Instant.now();

		GemFireSession session = new GemFireSession();

		assertThat(session.getId()).isNotNull();
		assertThat(session.getCreationTime()).isAfterOrEqualTo(testCreationTime);
		assertThat(session.hasDelta()).isTrue();
		assertThat(session.getLastAccessedTime()).isEqualTo(session.getCreationTime());
		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ZERO);
		assertThat(session.getAttributes()).isNotNull();
		assertThat(session.getAttributes()).isEmpty();
	}

	@Test
	public void constructGemFireSessionWithId() {

		Instant testCreationTime = Instant.now();

		GemFireSession session = new GemFireSession("1");

		assertThat(session.getId()).isEqualTo("1");
		assertThat(session.getCreationTime()).isAfterOrEqualTo(testCreationTime);
		assertThat(session.hasDelta()).isTrue();
		assertThat(session.getLastAccessedTime()).isEqualTo(session.getCreationTime());
		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ZERO);
		assertThat(session.getAttributes()).isNotNull();
		assertThat(session.getAttributes()).isEmpty();
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructGemFireSessionWithNullId() {

		try {
			new GemFireSession((String) null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("ID is required");
			assertThat(expected).hasNoCause();

			throw expected;
		}
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

		Set<String> expectedAttributedNames = asSet("attributeOne", "attributeTwo");

		when(mockSession.getAttributeNames()).thenReturn(expectedAttributedNames);
		when(mockSession.getAttribute(eq("attributeOne"))).thenReturn("testOne");
		when(mockSession.getAttribute(eq("attributeTwo"))).thenReturn("testTwo");

		GemFireSession gemfireSession = new GemFireSession(mockSession);

		assertThat(gemfireSession.getId()).isEqualTo("2");
		assertThat(gemfireSession.getCreationTime()).isEqualTo(expectedCreationTime);
		assertThat(gemfireSession.hasDelta()).isTrue();
		assertThat(gemfireSession.getLastAccessedTime()).isEqualTo(expectedLastAccessTime);
		assertThat(gemfireSession.getMaxInactiveInterval()).isEqualTo(expectedMaxInactiveInterval);
		assertThat(gemfireSession.getAttributeNames()).isEqualTo(expectedAttributedNames);
		assertThat(gemfireSession.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(gemfireSession.<String>getAttribute("attributeTwo")).isEqualTo("testTwo");

		verify(mockSession, times(1)).getId();
		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
		verify(mockSession, times(1)).getMaxInactiveInterval();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, times(1)).getAttribute(eq("attributeOne"));
		verify(mockSession, times(1)).getAttribute(eq("attributeTwo"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructGemFireSessionWithNull() {

		try {
			new GemFireSession((Session) null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("The Session to copy must not be null");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void createNewGemFireSessionWithDefaultMaxInactiveInterval() {

		Instant testCreationTime = Instant.now();

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isNotNull();
		assertThat(session.getId()).isNotNull();
		assertThat(session.getCreationTime()).isAfterOrEqualTo(testCreationTime);
		assertThat(session.hasDelta()).isTrue();
		assertThat(session.getLastAccessedTime()).isEqualTo(session.getCreationTime());
		assertThat(session.getMaxInactiveInterval()).isEqualTo(GemFireSession.DEFAULT_MAX_INACTIVE_INTERVAL);
		assertThat(session.getAttributes()).isNotNull();
		assertThat(session.getAttributes()).isEmpty();
	}

	@Test
	public void createNewGemFireSessionWithProvidedMaxInactiveInterval() {

		Instant testCreationTime = Instant.now();

		Duration maxInactiveInterval = Duration.ofSeconds(120L);

		GemFireSession<?> session = GemFireSession.create(maxInactiveInterval);

		assertThat(session).isNotNull();
		assertThat(session.getId()).isNotNull();
		assertThat(session.getCreationTime()).isAfterOrEqualTo(testCreationTime);
		assertThat(session.hasDelta()).isTrue();
		assertThat(session.getLastAccessedTime()).isEqualTo(session.getCreationTime());
		assertThat(session.getMaxInactiveInterval()).isEqualTo(maxInactiveInterval);
		assertThat(session.getAttributes()).isNotNull();
		assertThat(session.getAttributes()).isEmpty();
	}

	@Test(expected = IllegalArgumentException.class)
	public void copyNullThrowsException() {

		try {
			GemFireSession.copy(null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("The Session to copy must not be null");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void copySessionWhenNotUsingDataSerialization() {

		assertThat(AbstractGemFireOperationsSessionRepository.isUsingDataSerialization()).isFalse();

		Session mockSession = mockSession();

		when(mockSession.getAttributeNames()).thenReturn(Collections.singleton("attributeOne"));
		when(mockSession.getAttribute(eq("attributeOne"))).thenReturn("test");

		GemFireSession<?> sessionCopy = GemFireSession.copy(mockSession);

		assertThat(sessionCopy).isNotNull();
		assertThat(sessionCopy).isNotInstanceOf(DeltaCapableGemFireSession.class);
		assertThat(sessionCopy.getId()).isEqualTo(mockSession.getId());
		assertThat(sessionCopy.getCreationTime()).isEqualTo(mockSession.getCreationTime());
		assertThat(sessionCopy.hasDelta()).isTrue();
		assertThat(sessionCopy.getLastAccessedTime()).isEqualTo(mockSession.getLastAccessedTime());
		assertThat(sessionCopy.getMaxInactiveInterval()).isEqualTo(mockSession.getMaxInactiveInterval());
		assertThat(sessionCopy.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionCopy.<String>getAttribute("attributeOne")).isEqualTo("test");

		verify(mockSession, times(2)).getId();
		verify(mockSession, times(2)).getCreationTime();
		verify(mockSession, times(2)).getLastAccessedTime();
		verify(mockSession, times(2)).getMaxInactiveInterval();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, times(1)).getAttribute(eq("attributeOne"));
	}

	@Test
	public void copySessionWhenUsingDataSerialization() {

		this.sessionRepository.setUseDataSerialization(true);

		assertThat(AbstractGemFireOperationsSessionRepository.isUsingDataSerialization()).isTrue();

		Session mockSession = mockSession();

		when(mockSession.getAttributeNames()).thenReturn(Collections.singleton("attributeOne"));
		when(mockSession.getAttribute(eq("attributeOne"))).thenReturn("test");

		GemFireSession<?> sessionCopy = GemFireSession.copy(mockSession);

		assertThat(sessionCopy).isInstanceOf(DeltaCapableGemFireSession.class);
		assertThat(sessionCopy.getId()).isEqualTo(mockSession.getId());
		assertThat(sessionCopy.getCreationTime()).isEqualTo(mockSession.getCreationTime());
		assertThat(sessionCopy.hasDelta()).isTrue();
		assertThat(sessionCopy.getLastAccessedTime()).isEqualTo(mockSession.getLastAccessedTime());
		assertThat(sessionCopy.getMaxInactiveInterval()).isEqualTo(mockSession.getMaxInactiveInterval());
		assertThat(sessionCopy.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionCopy.<String>getAttribute("attributeOne")).isEqualTo("test");

		verify(mockSession, times(2)).getId();
		verify(mockSession, times(2)).getCreationTime();
		verify(mockSession, times(2)).getLastAccessedTime();
		verify(mockSession, times(2)).getMaxInactiveInterval();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, times(1)).getAttribute(eq("attributeOne"));
	}

	@Test
	public void fromExistingSessionCopiesSession() {

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
		assertThat(gemfireSession.hasDelta()).isTrue();
		assertThat(gemfireSession.getLastAccessedTime()).isEqualTo(expectedLastAccessedTime);
		assertThat(gemfireSession.getMaxInactiveInterval()).isEqualTo(expectedMaxInactiveInterval);
		assertThat(gemfireSession.getAttributes()).isNotNull();
		assertThat(gemfireSession.getAttributes()).isEmpty();

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

		session.setAttribute("attributeOne", "testOne");

		assertThat(session.getAttributeNames()).containsOnly("attributeOne");
		assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(session.<String>getAttribute("attributeTwo")).isNull();

		session.setAttribute("attributeTwo", "testTwo");

		assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
		assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(session.<String>getAttribute("attributeTwo")).isEqualTo("testTwo");

		session.setAttribute("attributeTwo", null);

		assertThat(session.getAttributeNames()).containsOnly("attributeOne");
		assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(session.<String>getAttribute("attributeTwo")).isNull();

		session.removeAttribute("attributeOne");

		assertThat(session.getAttributeNames()).isEmpty();
		assertThat(session.<String>getAttribute("attributeOne")).isNull();
		assertThat(session.<String>getAttribute("attributeTwo")).isNull();
	}

	@Test
	public void isExpiredWhenMaxInactiveIntervalIsNegativeReturnsFalse() {

		Duration expectedMaxInactiveIntervalInSeconds = Duration.ofSeconds(-1);

		GemFireSession<?> session = GemFireSession.create(expectedMaxInactiveIntervalInSeconds);

		assertThat(session).isNotNull();
		assertThat(session.getMaxInactiveInterval()).isEqualTo(expectedMaxInactiveIntervalInSeconds);
		assertThat(session.isExpired()).isFalse();
	}

	@Test
	public void isExpiredWhenMaxInactiveIntervalIsNullReturnsFalse() {

		GemFireSession<?> session = GemFireSession.create(null);

		assertThat(session).isNotNull();

		session.setMaxInactiveInterval(null);

		assertThat(session.getMaxInactiveInterval()).isEqualTo(GemFireSession.DEFAULT_MAX_INACTIVE_INTERVAL);
		assertThat(session.isExpired()).isFalse();
	}

	@Test
	public void isExpiredWhenMaxInactiveIntervalIsZeroReturnsFalse() {

		Duration expectedMaxInactiveIntervalInSeconds = Duration.ZERO;

		GemFireSession<?> session = GemFireSession.create(expectedMaxInactiveIntervalInSeconds);

		assertThat(session).isNotNull();
		assertThat(session.getMaxInactiveInterval()).isEqualTo(expectedMaxInactiveIntervalInSeconds);
		assertThat(session.isExpired()).isFalse();
	}

	@Test
	public void isExpiredWhenSessionIsActiveReturnsFalse() {

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
	public void isExpiredWhenSessionIsInactiveReturnsTrue() {

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

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isNotNull();
		assertThat(session.getPrincipalName()).isNull();

		session.setPrincipalName("jxblum");

		assertThat(session.getPrincipalName()).isEqualTo("jxblum");
		assertThat(session.getAttributeNames()).isEqualTo(asSet(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME));
		assertThat(session.<String>getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME)).isEqualTo("jxblum");

		session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "rwinch");

		assertThat(session.getAttributeNames()).isEqualTo(asSet(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME));
		assertThat(session.<String>getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME)).isEqualTo("rwinch");
		assertThat(session.getPrincipalName()).isEqualTo("rwinch");

		session.removeAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);

		assertThat(session.getPrincipalName()).isNull();
	}

	@Test
	public void sessionToDelta() throws Exception {

		DataOutput mockDataOutput = mock(DataOutput.class);

		@SuppressWarnings("serial")
		DeltaCapableGemFireSession session = new DeltaCapableGemFireSession();

		session.setLastAccessedTime(Instant.ofEpochMilli(1L));
		session.setMaxInactiveInterval(Duration.ofSeconds(300L));
		session.setAttribute("attributeOne", "test");

		assertThat(session.hasDelta()).isTrue();

		session.toDelta(mockDataOutput);

		assertThat(session.hasDelta()).isTrue();

		verify(mockDataOutput, times(1)).writeLong(eq(1L));
		verify(mockDataOutput, times(1)).writeLong(eq(300L));
		verify(mockDataOutput, times(1)).writeInt(eq(1));
		verify(mockDataOutput, times(1)).writeUTF(eq("attributeOne"));
	}

	@Test
	public void sessionFromDelta() throws Exception {

		DataInput mockDataInput = mock(DataInput.class);

		given(mockDataInput.readUTF()).willReturn("1");
		given(mockDataInput.readLong()).willReturn(1L).willReturn(600L);
		given(mockDataInput.readInt()).willReturn(0);

		DeltaCapableGemFireSession session = new DeltaCapableGemFireSession();

		Instant creationTime = session.getCreationTime();

		session.fromDelta(mockDataInput);

		assertThat(session.getId()).isEqualTo("1");
		assertThat(session.getCreationTime()).isEqualTo(creationTime);
		assertThat(session.getLastAccessedTime()).isEqualTo(Instant.ofEpochMilli(1L));
		assertThat(session.hasDelta()).isTrue();
		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(600L));
		assertThat(session.getAttributeNames().isEmpty()).isTrue();

		verify(mockDataInput, times(1)).readUTF();
		verify(mockDataInput, times(2)).readLong();
		verify(mockDataInput, times(1)).readInt();
	}

	@Test
	public void sessionHasDeltaWithExistingSessionReturnsTrue() {

		GemFireSession<?> session = GemFireSession.from(mockSession());

		assertThat(session).isNotNull();
		assertThat(session.hasDelta()).isTrue();
	}

	@Test
	public void sessionHasDeltaWithNewSessionReturnsTrue() {

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isNotNull();
		assertThat(session.hasDelta()).isTrue();
	}

	@Test
	public void sessionHasDeltaWhenSessionIsNotDirtyReturnsFalse() {
		assertThat(newNonDirtyGemFireSession().hasDelta()).isFalse();
	}

	@Test
	public void sessionHasDeltaWhenSessionAttributeIsAddedReturnsTrue() {

		GemFireSession<?> session = newNonDirtyGemFireSession();

		assertThat(session).isNotNull();
		assertThat(session.getAttributeNames()).isEmpty();
		assertThat(session.hasDelta()).isFalse();

		session.setAttribute("attributeOne", "one");

		assertThat(session.getAttributeNames()).containsExactly("attributeOne");
		assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("one");
		assertThat(session.hasDelta()).isTrue();

		session.commit();

		assertThat(session.hasDelta()).isFalse();
	}

	@Test
	public void sessionHasDeltaWhenSessionAttributeIsRemovedReturnsTrue() {

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isNotNull();

		session.setAttribute("attributeOne", "one");
		session.commit();

		assertThat(session.getAttributeNames()).containsExactly("attributeOne");
		assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("one");
		assertThat(session.hasDelta()).isFalse();

		session.removeAttribute("attributeOne");

		assertThat(session.getAttributeNames()).isEmpty();
		assertThat(session.<String>getAttribute("attributeOne")).isNull();
		assertThat(session.hasDelta()).isTrue();

		session.commit();

		assertThat(session.hasDelta()).isFalse();
	}

	@Test
	public void sessionHasDeltaWhenSessionAttributeIsUpdatedReturnsTrue() {

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isNotNull();

		session.setAttribute("attributeOne", "one");
		session.commit();

		assertThat(session.getAttributeNames()).containsExactly("attributeOne");
		assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("one");
		assertThat(session.hasDelta()).isFalse();

		session.setAttribute("attributeOne", "two");

		assertThat(session.getAttributeNames()).containsExactly("attributeOne");
		assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("two");
		assertThat(session.hasDelta()).isTrue();

		session.commit();

		assertThat(session.hasDelta()).isFalse();
	}

	@Test
	public void sessionHasDeltaWhenSessionIdChangesReturnsTrue() {

		GemFireSession<?> session = newNonDirtyGemFireSession();

		assertThat(session).isNotNull();
		assertThat(session.getId()).isNotEmpty();
		assertThat(session.hasDelta()).isFalse();

		String currentSessionId = session.getId();

		assertThat(currentSessionId).isNotEmpty();
		assertThat(session.changeSessionId()).isNotEqualTo(currentSessionId);
		assertThat(session.hasDelta()).isTrue();

		session.commit();

		assertThat(session.hasDelta()).isFalse();
	}

	@Test
	public void sessionHasDeltaWhenSessionLastAccessedTimeChangesReturnsTrue() {

		GemFireSession<?> session = newNonDirtyGemFireSession();

		assertThat(session).isNotNull();
		assertThat(session.hasDelta()).isFalse();

		Instant lastAccessedTime = session.getLastAccessedTime();

		assertThat(lastAccessedTime).isNotNull();

		session.setLastAccessedTime(lastAccessedTime.plus(Duration.ofSeconds(5)));

		assertThat(session.getLastAccessedTime()).isAfter(lastAccessedTime);
		assertThat(session.hasDelta()).isTrue();

		session.commit();

		assertThat(session.hasDelta()).isFalse();
	}

	@Test
	public void sessionHasDeltaWhenSessionMaxInactiveIntervalChangesReturnsTrue() {

		GemFireSession<?> session = newNonDirtyGemFireSession();

		assertThat(session).isNotNull();
		assertThat(session.hasDelta()).isFalse();

		Duration maxInactiveInterval = session.getMaxInactiveInterval();

		assertThat(maxInactiveInterval).isNotNull();

		session.setMaxInactiveInterval(maxInactiveInterval.plus(Duration.ofSeconds(5)));

		assertThat(session.getMaxInactiveInterval()).isGreaterThan(maxInactiveInterval);
		assertThat(session.hasDelta()).isTrue();

		session.commit();

		assertThat(session.hasDelta()).isFalse();
	}

	@Test
	public void sessionHasDeltaWhenSessionPrincipalNameChangesReturnsTrue() {

		GemFireSession<?> session = newNonDirtyGemFireSession();

		assertThat(session).isNotNull();
		assertThat(session.getPrincipalName()).isNull();
		assertThat(session.hasDelta()).isFalse();

		session.setPrincipalName("jxblum");

		assertThat(session.getPrincipalName()).isEqualTo("jxblum");
		assertThat(session.hasDelta()).isTrue();

		session.commit();

		assertThat(session.hasDelta()).isFalse();
	}

	@Test
	public void sessionHasDeltaWhenSessionIsDirtyAndAttributesAreNotModifiedOnSubsequentUpdatesReturnsTrue() {

		GemFireSession<?> session = newNonDirtyGemFireSession();

		assertThat(session).isNotNull();
		assertThat(session.hasDelta()).isFalse();

		String previousSessionId = session.getId();

		Instant newLastAccessedTime = session.getLastAccessedTime().plusSeconds(5L);

		Duration newMaxInactiveInterval = session.getMaxInactiveInterval().plusSeconds(5L);

		assertThat(session.changeSessionId()).isNotEqualTo(previousSessionId);

		session.setAttribute("attributeOne", "testOne");
		session.setLastAccessedTime(newLastAccessedTime);
		session.setMaxInactiveInterval(newMaxInactiveInterval);
		session.setPrincipalName("TestPrincipal");

		assertThat(session.hasDelta()).isTrue();

		session.setAttribute("attributeOne", "testOne");
		session.setLastAccessedTime(newLastAccessedTime);
		session.setMaxInactiveInterval(newMaxInactiveInterval);
		session.setPrincipalName("TestPrincipal");

		assertThat(session.hasDelta()).isTrue();
	}

	@Test
	public void sessionCompareTo() {

		Instant twoHoursAgo = Instant.now().minusMillis(TimeUnit.HOURS.toMillis(2));

		Session mockSession = mockSession("1", twoHoursAgo.toEpochMilli(), MAX_INACTIVE_INTERVAL_IN_SECONDS);

		GemFireSession<?> sessionOne = new GemFireSession<>(mockSession);

		GemFireSession<?> sessionTwo = new GemFireSession<>("2");

		assertThat(sessionOne.getCreationTime()).isEqualTo(twoHoursAgo);
		assertThat(sessionTwo.getCreationTime().isAfter(twoHoursAgo)).isTrue();
		assertThat(sessionOne.compareTo(sessionTwo)).isLessThan(0);
		assertThat(sessionOne.compareTo(sessionOne)).isEqualTo(0);
		assertThat(sessionTwo.compareTo(sessionOne)).isGreaterThan(0);
	}

	@Test
	@SuppressWarnings("all")
	public void sessionEqualsDifferentSessionBasedOnId() {

		GemFireSession sessionOne = new GemFireSession("1");

		sessionOne.setLastAccessedTime(Instant.ofEpochSecond(12345L));
		sessionOne.setMaxInactiveInterval(Duration.ofSeconds(120L));
		sessionOne.setPrincipalName("jxblum");

		long timestamp = System.currentTimeMillis();

		while (System.currentTimeMillis() == timestamp);

		GemFireSession sessionTwo = new GemFireSession("1");

		sessionTwo.setLastAccessedTime(Instant.ofEpochSecond(67890L));
		sessionTwo.setMaxInactiveInterval(Duration.ofSeconds(300L));
		sessionTwo.setPrincipalName("rwinch");

		assertThat(sessionTwo.getId().equals(sessionOne.getId())).isTrue();
		assertThat(sessionTwo.getCreationTime()).isNotEqualTo(sessionOne.getCreationTime());
		assertThat(sessionTwo.getLastAccessedTime()).isNotEqualTo(sessionOne.getLastAccessedTime());
		assertThat(sessionTwo.getMaxInactiveInterval()).isNotEqualTo(sessionOne.getMaxInactiveInterval());
		assertThat(sessionTwo.getPrincipalName()).isNotEqualTo(sessionOne.getPrincipalName());
		assertThat(sessionOne.equals(sessionTwo)).isTrue();
		assertThat(sessionTwo.equals(sessionOne)).isTrue();
	}

	@Test
	public void sessionIsNotEqualToDifferentSessionBasedOnId() {

		GemFireSession sessionOne = new GemFireSession("1");

		sessionOne.setLastAccessedTime(Instant.ofEpochSecond(12345L));
		sessionOne.setMaxInactiveInterval(Duration.ofSeconds(120L));
		sessionOne.setPrincipalName("jxblum");

		GemFireSession sessionTwo = new GemFireSession(sessionOne);

		sessionTwo.changeSessionId();

		assertThat(sessionTwo.getId().equals(sessionOne.getId())).isFalse();
		assertThat(sessionTwo.getCreationTime()).isEqualTo(sessionOne.getCreationTime());
		assertThat(sessionTwo.getLastAccessedTime()).isEqualTo(sessionOne.getLastAccessedTime());
		assertThat(sessionTwo.getMaxInactiveInterval()).isEqualTo(sessionOne.getMaxInactiveInterval());
		assertThat(sessionTwo.getPrincipalName()).isEqualTo(sessionOne.getPrincipalName());
		assertThat(sessionOne.equals(sessionTwo)).isFalse();
		assertThat(sessionTwo.equals(sessionOne)).isFalse();
	}

	@Test
	public void sessionHashCodeIsNotEqualToStringHashCode() {

		GemFireSession session = new GemFireSession("1");

		assertThat(session.getId()).isEqualTo("1");
		assertThat(session.hashCode()).isNotEqualTo("1".hashCode());
	}

	@Test
	public void sessionHashCodeWithEqualSessionsHaveSameHashCode() {

		GemFireSession<?> sessionOne = new GemFireSession("1");
		GemFireSession<?> sessionTwo = new GemFireSession(sessionOne);

		assertThat(sessionOne).isNotSameAs(sessionTwo);
		assertThat(sessionOne).isEqualTo(sessionTwo);
		assertThat(sessionOne.hashCode()).isEqualTo(sessionTwo.hashCode());
	}

	@Test
	public void sessionHashCodeWithUnequalSessionsHaveDifferentHashCodes() {

		GemFireSession<?> sessionOne = new GemFireSession("1");
		GemFireSession<?> sessionTwo = new GemFireSession(sessionOne);

		sessionTwo.changeSessionId();

		assertThat(sessionOne).isNotSameAs(sessionTwo);
		assertThat(sessionOne).isNotEqualTo(sessionTwo);
		assertThat(sessionOne.hashCode()).isNotEqualTo(sessionTwo.hashCode());
	}

	@Test @SuppressWarnings("unchecked")
	public void sessionToStringContainsId() {

		Session mockSession = mockSession();

		GemFireSession session = GemFireSession.from(mockSession);

		assertThat(session).isNotNull();
		assertThat(session.getId()).isEqualTo(mockSession.getId());
		assertThat(session.toString()).startsWith(String.format("{ @type = %1$s, id = %2$s",
			session.getClass().getName(), session.getId()));
	}

	@Test
	public void sessionAttributesFromMap() {

		Map<String, Object> source = new HashMap<>();

		source.put("attributeOne", "testOne");
		source.put("attributeTwo", "testTwo");

		GemFireSessionAttributes target = new GemFireSessionAttributes();

		assertThat(target).isEmpty();

		target.from(source);

		assertThat(target).hasSize(2);
		assertThat(target.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
		assertThat(target.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(target.<String>getAttribute("attributeTwo")).isEqualTo("testTwo");
	}

	@Test
	public void sessionAttributesFromSession() {

		Session mockSession = mock(Session.class);

		when(mockSession.getAttributeNames()).thenReturn(asSet("attributeOne", "attributeTwo"));
		when(mockSession.getAttribute(eq("attributeOne"))).thenReturn("testOne");
		when(mockSession.getAttribute(eq("attributeTwo"))).thenReturn("testTwo");

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		assertThat(sessionAttributes).isEmpty();

		sessionAttributes.from(mockSession);

		assertThat(sessionAttributes).hasSize(2);
		assertThat(sessionAttributes.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeTwo")).isEqualTo("testTwo");

		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, times(1)).getAttribute(eq("attributeOne"));
		verify(mockSession, times(1)).getAttribute(eq("attributeTwo"));
	}

	@Test
	public void sessionAttributesFromSessionAttributes() {

		GemFireSessionAttributes source = new GemFireSessionAttributes();

		source.setAttribute("attributeOne", "testOne");
		source.setAttribute("attributeTwo", "testTwo");

		GemFireSessionAttributes target = new GemFireSessionAttributes();

		assertThat(target).isEmpty();

		target.from(source);

		assertThat(target).hasSize(2);
		assertThat(target.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
		assertThat(target.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(target.<String>getAttribute("attributeTwo")).isEqualTo("testTwo");
	}

	@Test
	public void sessionAttributesToDelta() throws Exception {

		AtomicInteger count = new AtomicInteger(0);

		DataOutput mockDataOutput = mock(DataOutput.class);

		@SuppressWarnings("serial")
		DeltaCapableGemFireSessionAttributes sessionAttributes = spy(new DeltaCapableGemFireSessionAttributes());

		doAnswer(invocation -> {

			assertThat(Arrays.asList("testOne", "testTwo", "testThree").get(count.getAndIncrement()))
				.isEqualTo(invocation.getArgument(0));

			assertThat(invocation.<DataOutput>getArgument(1)).isSameAs(mockDataOutput);

			return null;

		}).when(sessionAttributes).writeObject(any(), isA(DataOutput.class));

		sessionAttributes.setAttribute("attributeOne", "testOne");
		sessionAttributes.setAttribute("attributeTwo", "testTwo");

		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.toDelta(mockDataOutput);

		assertThat(sessionAttributes.hasDelta()).isTrue();

		verify(mockDataOutput, times(1)).writeInt(eq(2));
		verify(mockDataOutput, times(1)).writeUTF("attributeOne");
		verify(mockDataOutput, times(1)).writeUTF("attributeTwo");
		reset(mockDataOutput);

		sessionAttributes.commit();
		sessionAttributes.setAttribute("attributeOne", "testOne");

		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.toDelta(mockDataOutput);

		verify(mockDataOutput, times(1)).writeInt(eq(0));
		verify(mockDataOutput, never()).writeUTF(any(String.class));
		reset(mockDataOutput);

		sessionAttributes.setAttribute("attributeTwo", "testThree");

		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.toDelta(mockDataOutput);

		assertThat(sessionAttributes.hasDelta()).isTrue();

		verify(mockDataOutput, times(1)).writeInt(eq(1));
		verify(mockDataOutput, times(1)).writeUTF(eq("attributeTwo"));
	}

	@Test
	public void sessionAttributesFromDelta() throws Exception {

		DataInput mockDataInput = mock(DataInput.class);

		when(mockDataInput.readInt()).thenReturn(2);
		when(mockDataInput.readUTF()).thenReturn("attributeOne").thenReturn("attributeTwo");

		DeltaCapableGemFireSessionAttributes sessionAttributes = spy(new DeltaCapableGemFireSessionAttributes());

		AtomicInteger count = new AtomicInteger(0);

		doAnswer(invocation -> {

			assertThat(invocation.<DataInput>getArgument(0)).isSameAs(mockDataInput);

			return Arrays.asList("testOne", "testTwo", "testThree").get(count.getAndIncrement());

		}).when(sessionAttributes).readObject(any(DataInput.class));

		sessionAttributes.setAttribute("attributeOne", "one");
		sessionAttributes.setAttribute("attributeTwo", "two");

		assertThat(sessionAttributes.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("one");
		assertThat(sessionAttributes.<String>getAttribute("attributeTwo")).isEqualTo("two");
		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.fromDelta(mockDataInput);

		assertThat(sessionAttributes.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeTwo")).isEqualTo("testTwo");
		assertThat(sessionAttributes.hasDelta()).isFalse();

		verify(mockDataInput, times(1)).readInt();
		verify(mockDataInput, times(2)).readUTF();

		reset(mockDataInput);

		when(mockDataInput.readInt()).thenReturn(1);
		when(mockDataInput.readUTF()).thenReturn("attributeTwo");

		sessionAttributes.setAttribute("attributeOne", "one");
		sessionAttributes.setAttribute("attributeTwo", "two");

		assertThat(sessionAttributes.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("one");
		assertThat(sessionAttributes.<String>getAttribute("attributeTwo")).isEqualTo("two");
		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.fromDelta(mockDataInput);

		assertThat(sessionAttributes.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("one");
		assertThat(sessionAttributes.<String>getAttribute("attributeTwo")).isEqualTo("testThree");
		assertThat(sessionAttributes.hasDelta()).isTrue();

		verify(mockDataInput, times(1)).readInt();
		verify(mockDataInput, times(1)).readUTF();
	}

	@Test
	public void sessionAttributesHasDeltaReturnsFalse() {
		assertThat(new GemFireSessionAttributes().hasDelta()).isFalse();
	}

	@Test
	public void sessionAttributesHasDeltaReturnsTrue() {

		GemFireSessionAttributes sessionAttributes = new DeltaCapableGemFireSessionAttributes();

		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.setAttribute("attributeOne", "testOne");

		assertThat(sessionAttributes).hasSize(1);
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.setAttribute("attributeOne", "testOne");

		assertThat(sessionAttributes).hasSize(1);
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.commit();

		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.setAttribute("attributeTwo", "testTwo");

		assertThat(sessionAttributes).hasSize(2);
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeTwo")).isEqualTo("testTwo");
		assertThat(sessionAttributes.hasDelta()).isTrue();
	}

	@Test
	public void sessionAttributesHasDeltaWhenSetAddsAttributeReturnsTrue() {

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		assertThat(sessionAttributes.getAttributeNames()).isEmpty();
		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.setAttribute("attributeOne", "testOne");

		// Set attribute to the same value again to make sure it does not clear the dirty bit
		sessionAttributes.setAttribute("attributeOne", "testOne");

		assertThat(sessionAttributes.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.commit();

		assertThat(sessionAttributes.hasDelta()).isFalse();
	}

	@Test
	public void sessionAttributesHasDeltaWhenSetDoesNotModifyAttributeReturnsFalse() {

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		sessionAttributes.from(Collections.singletonMap("attributeOne", "testOne"));
		sessionAttributes.commit();

		assertThat(sessionAttributes.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.setAttribute("attributeOne", "testOne");

		assertThat(sessionAttributes.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.commit();

		assertThat(sessionAttributes.hasDelta()).isFalse();
	}

	@Test
	public void sessionAttributesHasDeltaWhenSetModifiesAttributeReturnsTrue() {

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		sessionAttributes.from(Collections.singletonMap("attributeOne", "testOne"));
		sessionAttributes.commit();

		assertThat(sessionAttributes.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.setAttribute("attributeOne", "testTwo");

		assertThat(sessionAttributes.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testTwo");
		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.commit();

		assertThat(sessionAttributes.hasDelta()).isFalse();
	}

	@Test
	public void sessionAttributesHasDeltaWhenSetRemovesAttributeReturnsTrue() {

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		sessionAttributes.from(Collections.singletonMap("attributeOne", "testOne"));
		sessionAttributes.commit();

		assertThat(sessionAttributes.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.setAttribute("attributeOne", null);

		assertThat(sessionAttributes.getAttributeNames()).isEmpty();
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isNull();
		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.commit();

		assertThat(sessionAttributes.hasDelta()).isFalse();
	}

	@Test
	public void sessionAttributesHasDeltaWhenRemovingExistingAttributeReturnsTrue() {

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		sessionAttributes.from(Collections.singletonMap("attributeOne", "testOne"));
		sessionAttributes.commit();

		assertThat(sessionAttributes.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.removeAttribute("attributeOne");

		// Remove attribute again to make sure it does not clear the dirty bit
		sessionAttributes.removeAttribute("attributeOne");

		assertThat(sessionAttributes.getAttributeNames()).isEmpty();
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isNull();
		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.commit();

		assertThat(sessionAttributes.hasDelta()).isFalse();
	}

	@Test
	public void sessionAttributesHasDeltaWhenRemovingNonExistingAttributeReturnsFalse() {

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		sessionAttributes.from(Collections.singletonMap("attributeOne", "testOne"));
		sessionAttributes.commit();

		assertThat(sessionAttributes.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.removeAttribute("nonExistingAttribute");

		assertThat(sessionAttributes.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isFalse();
	}

	@Test
	public void sessionAttributesHasDeltaWhenRemovingNullReturnsFalse() {

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		assertThat(sessionAttributes.getAttributeNames()).isEmpty();
		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.removeAttribute(null);

		assertThat(sessionAttributes.getAttributeNames()).isEmpty();
		assertThat(sessionAttributes.hasDelta()).isFalse();
	}

	@Test
	public void sessionAttributesEntrySetIteratesAttributeNamesAndValues() {

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
		TestFramework.runOnce(new ThreadSafeSessionTestCase());
	}

	@SuppressWarnings("unused")
	protected static final class ThreadSafeSessionTestCase extends MultithreadedTestCase {

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
			this.session.setPrincipalName("jxblum");
		}

		public void thread1() {

			assertTick(0);

			Thread.currentThread().setName("HTTP Request Processing Thread 1");

			assertThat(this.session).isNotNull();
			assertThat(this.session.getId()).isEqualTo("1");
			assertThat(this.session.getCreationTime()).isEqualTo(this.expectedCreationTime);
			assertThat(this.session.getLastAccessedTime()).isEqualTo(Instant.MIN);
			assertThat(this.session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(60L));
			assertThat(this.session.getPrincipalName()).isEqualTo("jxblum");
			assertThat(this.session.getAttributeNames()).hasSize(1);
			assertThat(this.session.<String>getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME)).isEqualTo("jxblum");

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

	static class Tombstone { }

}
