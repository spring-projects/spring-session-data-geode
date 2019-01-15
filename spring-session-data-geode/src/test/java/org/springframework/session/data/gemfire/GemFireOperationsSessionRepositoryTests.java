/*
 * Copyright 2019 the original author or authors.
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSession;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.apache.geode.cache.AttributesMutator;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.query.SelectResults;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.gemfire.GemfireAccessor;
import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.data.gemfire.util.RegionUtils;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.SessionEventHandlerCacheListenerAdapter;
import org.springframework.session.data.gemfire.support.EqualsDirtyPredicate;
import org.springframework.session.data.gemfire.support.IdentityEqualsDirtyPredicate;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionDeletedEvent;

/**
 * Unit tests for {@link GemFireOperationsSessionRepository}.
 *
 * @author John Blum
 * @see java.time.Duration
 * @see java.time.Instant
 * @see java.util.UUID
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.mockito.junit.MockitoJUnitRunner
 * @see org.apache.geode.cache.Region
 * @see org.springframework.context.ApplicationEvent
 * @see org.springframework.context.ApplicationEventPublisher
 * @see org.springframework.data.gemfire.GemfireAccessor
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.session.FindByIndexNameSessionRepository
 * @see org.springframework.session.Session
 * @see org.springframework.session.events.AbstractSessionEvent
 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository
 * @since 1.1.0
 */
@RunWith(MockitoJUnitRunner.class)
public class GemFireOperationsSessionRepositoryTests {

	protected static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 600;

	@Mock
	private ApplicationEventPublisher mockApplicationEventPublisher;

	@Mock
	private GemfireOperationsAccessor mockTemplate;

	// Subject Under Test (SUT)
	private GemFireOperationsSessionRepository sessionRepository;

	@Before
	@SuppressWarnings("unchecked")
	public void setup() throws Exception {

		AttributesMutator<Object, Session> mockAttributesMutator = mock(AttributesMutator.class);

		Region<Object, Session> mockRegion = mock(Region.class);

		when(mockRegion.getAttributesMutator()).thenReturn(mockAttributesMutator);
		when(mockRegion.getFullPath()).thenReturn(RegionUtils.toRegionPath("Example"));

		doReturn(mockRegion).when(this.mockTemplate).<Object, Session>getRegion();

		this.sessionRepository = new GemFireOperationsSessionRepository(this.mockTemplate);
		this.sessionRepository.setApplicationEventPublisher(this.mockApplicationEventPublisher);
		this.sessionRepository.setMaxInactiveIntervalInSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		this.sessionRepository.setUseDataSerialization(false);

		assertThat(this.sessionRepository.getApplicationEventPublisher()).isSameAs(this.mockApplicationEventPublisher);
		assertThat(this.sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		assertThat(this.sessionRepository.getSessionEventHandler().orElse(null)).isInstanceOf(SessionEventHandlerCacheListenerAdapter.class);
		assertThat(this.sessionRepository.getSessionsRegion()).isSameAs(mockRegion);
		assertThat(this.sessionRepository.getSessionsRegionName()).isEqualTo(RegionUtils.toRegionPath("Example"));
		assertThat(this.sessionRepository.getSessionsTemplate()).isSameAs(this.mockTemplate);
		assertThat(GemFireOperationsSessionRepository.isUsingDataSerialization()).isFalse();

		verify(mockAttributesMutator).addCacheListener(isA(SessionEventHandlerCacheListenerAdapter.class));
		verify(mockRegion, times(1)).getAttributesMutator();
		verify(this.mockTemplate, times(1)).getRegion();
	}

	private GemFireSession newNonDirtyGemFireSession() {

		GemFireSession session = GemFireSession.create();

		session.commit();

		return session;
	}

	@Test
	@SuppressWarnings("unchecked")
	public void constructGemFireOperationSessionRepositoryWithTemplate() {

		AttributesMutator<Object, Session> mockAttributesMutator = mock(AttributesMutator.class);

		Region<Object, Session> mockRegion = mock(Region.class);

		GemfireOperationsAccessor mockTemplate = mock(GemfireOperationsAccessor.class);

		when(mockRegion.getAttributesMutator()).thenReturn(mockAttributesMutator);

		doReturn(mockRegion).when(mockTemplate).getRegion();

		GemFireOperationsSessionRepository sessionRepository =
			new GemFireOperationsSessionRepository(mockTemplate);

		assertThat(sessionRepository).isNotNull();
		assertThat(sessionRepository.getSessionsRegion()).isSameAs(mockRegion);
		assertThat(sessionRepository.getSessionsTemplate()).isSameAs(mockTemplate);

		verify(mockTemplate, times(1)).getRegion();
		verify(mockRegion, times(1)).getAttributesMutator();
		verify(mockAttributesMutator, times(1))
			.addCacheListener(isA(SessionEventHandlerCacheListenerAdapter.class));
		verifyNoMoreInteractions(mockAttributesMutator);
	}

	@Test
	public void createProperlyInitializedSession() {

		Instant beforeCreationTime = Instant.now();

		this.sessionRepository.setIsDirtyPredicate(EqualsDirtyPredicate.INSTANCE);

		Session session = this.sessionRepository.createSession();

		assertThat(session).isInstanceOf(GemFireSession.class);
		assertThat(session.getId()).isNotEmpty();
		assertThat(session.getAttributeNames()).isEmpty();
		assertThat(session.getCreationTime()).isAfterOrEqualTo(beforeCreationTime);
		assertThat(session.getCreationTime()).isBeforeOrEqualTo(Instant.now());
		assertThat(session.isExpired()).isFalse();
		assertThat(((GemFireSession) session).getIsDirtyPredicate()).isEqualTo(EqualsDirtyPredicate.INSTANCE);
		assertThat(session.getLastAccessedTime()).isEqualTo(session.getCreationTime());
		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS));
	}

	@Test
	public void createProperlyInitializedDeltaAwareSession() {

		Instant beforeCreationTime = Instant.now();

		this.sessionRepository.setIsDirtyPredicate(IdentityEqualsDirtyPredicate.INSTANCE);
		this.sessionRepository.setMaxInactiveIntervalInSeconds(300);
		this.sessionRepository.setUseDataSerialization(true);

		Session session = this.sessionRepository.createSession();

		assertThat(session).isInstanceOf(DeltaCapableGemFireSession.class);
		assertThat(session.getId()).isNotEmpty();
		assertThat(session.getAttributeNames()).isEmpty();
		assertThat(session.getCreationTime()).isAfterOrEqualTo(beforeCreationTime);
		assertThat(session.getCreationTime()).isBeforeOrEqualTo(Instant.now());
		assertThat(session.isExpired()).isFalse();
		assertThat(((DeltaCapableGemFireSession) session).getIsDirtyPredicate())
			.isEqualTo(IdentityEqualsDirtyPredicate.INSTANCE);
		assertThat(session.getLastAccessedTime()).isEqualTo(session.getCreationTime());
		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(300));
	}

	@Test
	public void findByIdReturnsMatchingNonExpiredSession() {

		Instant expectedCreationTime = Instant.now();
		Instant currentLastAccessedTime = expectedCreationTime.plusMillis(TimeUnit.MINUTES.toMillis(5L));

		Session mockSession = mock(Session.class);

		when(mockSession.isExpired()).thenReturn(false);
		when(mockSession.getId()).thenReturn("1");
		when(mockSession.getCreationTime()).thenReturn(expectedCreationTime);
		when(mockSession.getLastAccessedTime()).thenReturn(currentLastAccessedTime);
		when(mockSession.getAttributeNames()).thenReturn(Collections.singleton("attributeOne"));
		when(mockSession.getAttribute(eq("attributeOne"))).thenReturn("test");
		when(this.mockTemplate.get(eq("1"))).thenReturn(mockSession);

		GemFireOperationsSessionRepository sessionRepositorySpy = spy(this.sessionRepository);

		Session actualSession = sessionRepositorySpy.findById("1");

		assertThat(actualSession).isNotNull();
		assertThat(actualSession).isNotSameAs(mockSession);
		assertThat(actualSession.getId()).isEqualTo("1");
		assertThat(actualSession.getCreationTime()).isEqualTo(expectedCreationTime);
		assertThat(actualSession.getLastAccessedTime()).isNotEqualTo(currentLastAccessedTime);
		assertThat(actualSession.getLastAccessedTime()).isAfterOrEqualTo(expectedCreationTime);
		assertThat(actualSession.getLastAccessedTime()).isBeforeOrEqualTo(Instant.now());
		assertThat(actualSession.getAttributeNames()).containsExactly("attributeOne");
		assertThat(actualSession.<String>getAttribute("attributeOne")).isEqualTo("test");

		verify(this.mockTemplate, times(1)).get(eq("1"));
		verify(mockSession, times(1)).isExpired();
		verify(mockSession, times(1)).getId();
		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, times(1)).getAttribute(eq("attributeOne"));

		InOrder inOrder = inOrder(sessionRepositorySpy);

		inOrder.verify(sessionRepositorySpy, times(1)).configure(eq(actualSession));
		inOrder.verify(sessionRepositorySpy, times(1)).registerInterest(eq(actualSession));
		inOrder.verify(sessionRepositorySpy, times(1)).commit(eq(actualSession));
		inOrder.verify(sessionRepositorySpy, times(1)).touch(eq(actualSession));
	}

	@Test
	public void findByIdDeletesMatchingExpiredSessionReturnsNull() {

		Session mockSession = mock(Session.class);

		when(mockSession.getId()).thenReturn("1");
		when(mockSession.isExpired()).thenReturn(true);
		when(this.mockTemplate.get(eq("1"))).thenReturn(mockSession);
		when(this.mockTemplate.remove(eq("1"))).thenReturn(mockSession);

		doAnswer(invocation -> {

			ApplicationEvent applicationEvent = invocation.getArgument(0);

			assertThat(applicationEvent).isInstanceOf(SessionDeletedEvent.class);

			AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

			assertThat(sessionEvent.<Session>getSession()).isSameAs(mockSession);
			assertThat(sessionEvent.getSessionId()).isEqualTo("1");
			assertThat(sessionEvent.getSource()).isSameAs(this.sessionRepository);

			return null;

		}).when(this.mockApplicationEventPublisher).publishEvent(any(ApplicationEvent.class));

		assertThat(this.sessionRepository.findById("1")).isNull();

		verify(this.mockTemplate, times(1)).get(eq("1"));
		verify(this.mockTemplate, times(1)).remove(eq("1"));
		verify(mockSession, times(2)).getId();
		verify(mockSession, times(1)).isExpired();
		verify(this.mockApplicationEventPublisher, times(1))
			.publishEvent(isA(SessionDeletedEvent.class));
	}

	@Test
	public void findByIdReturnsNull() {

		when(this.mockTemplate.get(anyString())).thenReturn(null);

		GemFireOperationsSessionRepository sessionRepositorySpy = spy(this.sessionRepository);

		assertThat(sessionRepositorySpy.findById("1")).isNull();

		verify(this.mockTemplate, times(1)).get(eq("1"));
		verify(sessionRepositorySpy, times(1)).findById(eq("1"));
		verify(sessionRepositorySpy, never()).delete(any());
		verify(sessionRepositorySpy, never()).commit(any());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findByIndexNameAndIndexValueReturnsMatchingSession() {

		Session mockSession = mock(Session.class);

		when(mockSession.getId()).thenReturn("1");

		SelectResults<Object> mockSelectResults = mock(SelectResults.class);

		when(mockSelectResults.asList()).thenReturn(Collections.singletonList(mockSession));

		String indexName = "vip";
		String indexValue = "rwinch";

		String expectedQql =
			String.format(GemFireOperationsSessionRepository.FIND_SESSIONS_BY_INDEX_NAME_AND_INDEX_VALUE_QUERY,
				this.sessionRepository.getSessionsRegionName(), indexName);

		when(this.mockTemplate.find(eq(expectedQql), eq(indexValue))).thenReturn(mockSelectResults);

		GemFireOperationsSessionRepository sessionRepositorySpy = spy(this.sessionRepository);

		Map<String, Session> sessions =
			sessionRepositorySpy.findByIndexNameAndIndexValue(indexName, indexValue);

		assertThat(sessions).isNotNull();
		assertThat(sessions).hasSize(1);
		assertThat(sessions.get("1")).isEqualTo(mockSession);

		verify(this.mockTemplate, times(1)).find(eq(expectedQql), eq(indexValue));
		verify(mockSelectResults, times(1)).asList();
		verify(mockSession, times(2)).getId();

		InOrder inOrder = inOrder(sessionRepositorySpy);

		inOrder.verify(sessionRepositorySpy, times(1)).configure(eq(mockSession));
		inOrder.verify(sessionRepositorySpy, times(1)).registerInterest(eq(mockSession));
		inOrder.verify(sessionRepositorySpy, times(1)).commit(eq(mockSession));
		inOrder.verify(sessionRepositorySpy, times(1)).touch(eq(mockSession));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findByPrincipalNameReturnsMatchingSessions() throws Exception {

		Session mockSessionOne = mock(Session.class, "MockSessionOne");
		Session mockSessionTwo = mock(Session.class, "MockSessionTwo");
		Session mockSessionThree = mock(Session.class, "MockSessionThree");

		when(mockSessionOne.getId()).thenReturn("1");
		when(mockSessionTwo.getId()).thenReturn("2");
		when(mockSessionThree.getId()).thenReturn("3");

		SelectResults<Object> mockSelectResults = mock(SelectResults.class);

		when(mockSelectResults.asList()).thenReturn(Arrays.asList(mockSessionOne, mockSessionTwo, mockSessionThree));

		String principalName = "jblum";

		String expectedOql =
			String.format(GemFireOperationsSessionRepository.FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY,
				this.sessionRepository.getSessionsRegionName());

		when(this.mockTemplate.find(eq(expectedOql), eq(principalName))).thenReturn(mockSelectResults);

		GemFireOperationsSessionRepository sessionRepositorySpy = spy(this.sessionRepository);

		Map<String, Session> sessions =
			sessionRepositorySpy.findByIndexNameAndIndexValue(PRINCIPAL_NAME_INDEX_NAME, principalName);

		assertThat(sessions).isNotNull();
		assertThat(sessions).hasSize(3);
		assertThat(sessions.get("1")).isEqualTo(mockSessionOne);
		assertThat(sessions.get("2")).isEqualTo(mockSessionTwo);
		assertThat(sessions.get("3")).isEqualTo(mockSessionThree);

		verify(this.mockTemplate, times(1)).find(eq(expectedOql), eq(principalName));
		verify(mockSelectResults, times(1)).asList();
		verify(mockSessionOne, times(2)).getId();
		verify(mockSessionTwo, times(2)).getId();
		verify(mockSessionThree, times(2)).getId();

		InOrder inOrder = inOrder(sessionRepositorySpy);

		inOrder.verify(sessionRepositorySpy, times(1)).configure(eq(mockSessionOne));
		inOrder.verify(sessionRepositorySpy, times(1)).registerInterest(eq(mockSessionOne));
		inOrder.verify(sessionRepositorySpy, times(1)).commit(eq(mockSessionOne));
		inOrder.verify(sessionRepositorySpy, times(1)).touch(eq(mockSessionOne));
		inOrder.verify(sessionRepositorySpy, times(1)).configure(eq(mockSessionTwo));
		inOrder.verify(sessionRepositorySpy, times(1)).registerInterest(eq(mockSessionTwo));
		inOrder.verify(sessionRepositorySpy, times(1)).commit(eq(mockSessionTwo));
		inOrder.verify(sessionRepositorySpy, times(1)).touch(eq(mockSessionTwo));
		inOrder.verify(sessionRepositorySpy, times(1)).configure(eq(mockSessionThree));
		inOrder.verify(sessionRepositorySpy, times(1)).registerInterest(eq(mockSessionThree));
		inOrder.verify(sessionRepositorySpy, times(1)).commit(eq(mockSessionThree));
		inOrder.verify(sessionRepositorySpy, times(1)).touch(eq(mockSessionThree));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findByPrincipalNameReturnsNoMatchingSessions() {

		SelectResults<Object> mockSelectResults = mock(SelectResults.class);

		when(mockSelectResults.asList()).thenReturn(Collections.emptyList());

		String principalName = "jblum";

		String expectedOql =
			String.format(GemFireOperationsSessionRepository.FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY,
				this.sessionRepository.getSessionsRegionName());

		when(this.mockTemplate.find(eq(expectedOql), eq(principalName))).thenReturn(mockSelectResults);

		GemFireOperationsSessionRepository sessionRepositorySpy = spy(this.sessionRepository);

		Map<String, Session> sessions =
			sessionRepositorySpy.findByIndexNameAndIndexValue(PRINCIPAL_NAME_INDEX_NAME, principalName);

		assertThat(sessions).isNotNull();
		assertThat(sessions).isEmpty();

		verify(this.mockTemplate, times(1)).find(eq(expectedOql), eq(principalName));
		verify(mockSelectResults, times(1)).asList();
		verify(sessionRepositorySpy, times(1))
			.findByIndexNameAndIndexValue(eq(PRINCIPAL_NAME_INDEX_NAME), eq(principalName));
		verify(sessionRepositorySpy, never()).commit(any());
	}

	@Test
	public void prepareQueryReturnsIndexNameAndIndexValueOql() {

		String attributeName = "testAttributeName";

		String actualOql = this.sessionRepository.prepareQuery(attributeName);

		String expectedOql =
			String.format(GemFireOperationsSessionRepository.FIND_SESSIONS_BY_INDEX_NAME_AND_INDEX_VALUE_QUERY,
				this.sessionRepository.getSessionsRegionName(), attributeName);

		assertThat(actualOql).isEqualTo(expectedOql);
	}

	@Test
	public void prepareQueryReturnsPrincipalNameOql() {

		String actualQql = this.sessionRepository.prepareQuery(PRINCIPAL_NAME_INDEX_NAME);

		String expectedOql =
			String.format(GemFireOperationsSessionRepository.FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY,
				this.sessionRepository.getSessionsRegionName());

		assertThat(actualQql).isEqualTo(expectedOql);
	}

	@Test
	public void saveIsNullSafe() {

		this.sessionRepository.save(null);

		verify(this.mockTemplate, never()).put(any(), any());
	}

	@Test
	public void saveStoresSession() {

		Instant expectedCreationTime = Instant.now();
		Instant expectedLastAccessTime = expectedCreationTime.plusMillis(TimeUnit.MINUTES.toMillis(5L));

		Duration expectedMaxInactiveInterval = Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);

		Session mockSession = mock(Session.class);

		when(mockSession.getId()).thenReturn("1");
		when(mockSession.getCreationTime()).thenReturn(expectedCreationTime);
		when(mockSession.getLastAccessedTime()).thenReturn(expectedLastAccessTime);
		when(mockSession.getMaxInactiveInterval()).thenReturn(expectedMaxInactiveInterval);
		when(mockSession.getAttributeNames()).thenReturn(Collections.emptySet());

		when(this.mockTemplate.put(eq("1"), isA(GemFireSession.class)))
			.thenAnswer(invocation -> {

				Session session = invocation.getArgument(1);

				assertThat(session).isNotNull();
				assertThat(session.getId()).isEqualTo("1");
				assertThat(session.getCreationTime()).isEqualTo(expectedCreationTime);
				assertThat(session.getLastAccessedTime()).isEqualTo(expectedLastAccessTime);
				assertThat(session.getMaxInactiveInterval()).isEqualTo(expectedMaxInactiveInterval);
				assertThat(session.getAttributeNames().isEmpty()).isTrue();

				return null;
		});

		GemFireOperationsSessionRepository sessionRepositorySpy = spy(this.sessionRepository);

		sessionRepositorySpy.save(mockSession);

		verify(mockSession, times(2)).getId();
		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
		verify(mockSession, times(1)).getMaxInactiveInterval();
		verify(mockSession, times(1)).getAttributeNames();
		verify(this.mockTemplate, times(1)).put(eq("1"),
			isA(GemFireSession.class));
		verify(sessionRepositorySpy, times(1)).save(eq(mockSession));
		verify(sessionRepositorySpy, times(1)).commit(eq(mockSession));
	}

	@Test
	public void saveStoresAndCommitsGemFireSession() {

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isNotNull();
		assertThat(session.hasDelta()).isTrue();

		session = spy(session);

		this.sessionRepository.save(session);

		InOrder orderVerifier = inOrder(session);

		orderVerifier.verify(session, times(1)).hasDelta();
		orderVerifier.verify(session, times(1)).getId();
		orderVerifier.verify(session, times(1)).commit();

		verify(this.mockTemplate, times(1)).put(eq(session.getId()), eq(session));
		verify(this.mockTemplate, times(1)).put(eq(session.getId()), same(session));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void saveWillNotStoreNonDirtyGemFireSessions() {

		GemFireSession session = newNonDirtyGemFireSession();

		assertThat(session).isNotNull();
		assertThat(session.hasDelta()).isFalse();

		session = spy(session);

		this.sessionRepository.save(session);

		verify(session, times(1)).hasDelta();
		verify(session, never()).getId();
		verify(session, never()).commit();
		verify(this.mockTemplate, never()).put(any(), any(GemFireSession.class));
	}

	@Test
	public void deleteRemovesExistingSessionAndHandlesDelete() {

		AtomicBoolean methodCalled = new AtomicBoolean(false);

		Session mockSession = mock(Session.class);

		when(mockSession.getId()).thenReturn("1");
		when(this.mockTemplate.remove(eq("1"))).thenReturn(mockSession);

		doAnswer(invocation -> {

			ApplicationEvent applicationEvent = invocation.getArgument(0);

			assertThat(applicationEvent).isInstanceOf(SessionDeletedEvent.class);

			AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

			assertThat(sessionEvent.<Session>getSession()).isSameAs(mockSession);
			assertThat(sessionEvent.getSessionId()).isEqualTo("1");
			assertThat(sessionEvent.getSource()).isSameAs(this.sessionRepository);

			methodCalled.set(true);

			return null;

		}).when(this.mockApplicationEventPublisher).publishEvent(isA(SessionDeletedEvent.class));

		this.sessionRepository.deleteById("1");

		assertThat(methodCalled.get()).isTrue();

		verify(mockSession, times(1)).getId();
		verify(this.mockTemplate, times(1)).remove(eq("1"));
		verify(this.mockApplicationEventPublisher, times(1))
			.publishEvent(isA(SessionDeletedEvent.class));
	}

	@Test
	public void deleteRemovesNonExistingSessionAndHandlesDelete() {

		AtomicBoolean methodCalled = new AtomicBoolean(false);

		when(this.mockTemplate.remove(eq("1"))).thenReturn(null);

		doAnswer(invocation -> {

			ApplicationEvent applicationEvent = invocation.getArgument(0);

			assertThat(applicationEvent).isInstanceOf(SessionDeletedEvent.class);

			AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

			Session session = sessionEvent.getSession();

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo("1");
			assertThat(sessionEvent.getSessionId()).isEqualTo("1");
			assertThat(sessionEvent.getSource()).isEqualTo(this.sessionRepository);

			methodCalled.set(true);

			return null;

		}).when(this.mockApplicationEventPublisher).publishEvent(isA(SessionDeletedEvent.class));

		this.sessionRepository.deleteById("1");

		assertThat(methodCalled.get()).isTrue();

		verify(this.mockTemplate, times(1)).remove(eq("1"));
		verify(this.mockApplicationEventPublisher, times(1))
			.publishEvent(isA(SessionDeletedEvent.class));
	}

	protected abstract class GemfireOperationsAccessor extends GemfireAccessor implements GemfireOperations { }

}
