/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.session.data.gemfire.expiration.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.apache.geode.cache.ExpirationAction;
import org.apache.geode.cache.ExpirationAttributes;
import org.apache.geode.cache.Region;
import org.apache.geode.pdx.PdxInstance;

import org.springframework.session.Session;
import org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy;

/**
 * Unit tests for {@link SessionExpirationPolicyCustomExpiryAdapter}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.mockito.junit.MockitoJUnitRunner
 * @see org.springframework.session.data.gemfire.expiration.support.SessionExpirationPolicyCustomExpiryAdapter
 * @since 2.1.0
 */
@RunWith(MockitoJUnitRunner.class)
public class SessionExpirationPolicyCustomExpiryAdapterUnitTests {

	@Mock
	private SessionExpirationPolicy mockSessionExpirationPolicy;

	@Test
	public void constructsNewSessionExpirationPolicyCustomExpiryAdapter() {

		SessionExpirationPolicyCustomExpiryAdapter adapter =
			new SessionExpirationPolicyCustomExpiryAdapter(this.mockSessionExpirationPolicy);

		assertThat(adapter).isNotNull();
		assertThat(adapter.getSessionExpirationPolicy()).isEqualTo(this.mockSessionExpirationPolicy);
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructsNewSessionExpirationPolicyCustomExpiryAdapterWithNullSessionExpirationPolicy() {

		try {
			new SessionExpirationPolicyCustomExpiryAdapter(null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("SessionExpirationPolicy is required");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void getExpiryHandlesNoExpirationTimeout() {

		when(this.mockSessionExpirationPolicy.determineExpirationTimeout(any(Session.class)))
			.thenReturn(Optional.empty());

		Session mockSession = mock(Session.class);

		Region.Entry<String, Object> mockRegionEntry = mock(Region.Entry.class);

		when(mockRegionEntry.getValue()).thenReturn(mockSession);

		SessionExpirationPolicyCustomExpiryAdapter adapter =
			new SessionExpirationPolicyCustomExpiryAdapter(this.mockSessionExpirationPolicy);

		ExpirationAttributes expirationAttributes = adapter.getExpiry(mockRegionEntry);

		assertThat(expirationAttributes).isNull();

		verify(mockRegionEntry, times(1)).getValue();
		verify(this.mockSessionExpirationPolicy, times(1)).determineExpirationTimeout(eq(mockSession));
		verify(this.mockSessionExpirationPolicy, never()).getExpirationAction();
	}

	@Test
	public void getExpiryHandlesNullRegionEntry() {

		SessionExpirationPolicyCustomExpiryAdapter adapter =
			new SessionExpirationPolicyCustomExpiryAdapter(this.mockSessionExpirationPolicy);

		assertThat(adapter.getExpiry(null)).isNull();

		verifyZeroInteractions(this.mockSessionExpirationPolicy);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void getExpiryHandlesNullRegionEntryPdxInstanceObject() {

		PdxInstance mockPdxInstance = mock(PdxInstance.class);

		Region.Entry<String, Object> mockRegionEntry = mock(Region.Entry.class);

		when(mockPdxInstance.getObject()).thenReturn(null);
		when(mockRegionEntry.getValue()).thenReturn(mockPdxInstance);

		SessionExpirationPolicyCustomExpiryAdapter adapter =
			new SessionExpirationPolicyCustomExpiryAdapter(this.mockSessionExpirationPolicy);

		assertThat(adapter.getExpiry(mockRegionEntry)).isNull();

		verify(mockPdxInstance, times(1)).getObject();
		verify(mockRegionEntry, times(1)).getValue();
		verifyZeroInteractions(this.mockSessionExpirationPolicy);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void getExpiryHandlesNullRegionEntryValue() {

		Region.Entry<String, Object> mockRegionEntry = mock(Region.Entry.class);

		when(mockRegionEntry.getValue()).thenReturn(null);

		SessionExpirationPolicyCustomExpiryAdapter adapter =
			new SessionExpirationPolicyCustomExpiryAdapter(this.mockSessionExpirationPolicy);

		assertThat(adapter.getExpiry(mockRegionEntry)).isNull();

		verify(mockRegionEntry, times(1)).getValue();
		verifyZeroInteractions(this.mockSessionExpirationPolicy);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void getExpiryHandlesUnresolvableSession() {

		Region.Entry<String, Object> mockRegionEntry = mock(Region.Entry.class);

		when(mockRegionEntry.getValue()).thenReturn("MockSession");

		SessionExpirationPolicyCustomExpiryAdapter adapter =
			new SessionExpirationPolicyCustomExpiryAdapter(this.mockSessionExpirationPolicy);

		assertThat(adapter.getExpiry(mockRegionEntry)).isNull();

		verify(mockRegionEntry, times(1)).getValue();
		verifyZeroInteractions(this.mockSessionExpirationPolicy);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void getExpiryReturnsExpirationAttributes() {

		Duration expirationTimeout = Duration.ofMinutes(30L);

		SessionExpirationPolicy.ExpirationAction expirationAction = SessionExpirationPolicy.ExpirationAction.DESTROY;

		when(this.mockSessionExpirationPolicy.determineExpirationTimeout(any(Session.class)))
			.thenReturn(Optional.of(expirationTimeout));

		when(this.mockSessionExpirationPolicy.getExpirationAction()).thenReturn(expirationAction);

		Session mockSession = mock(Session.class);

		Region.Entry<String, Object> mockRegionEntry = mock(Region.Entry.class);

		when(mockRegionEntry.getValue()).thenReturn(mockSession);

		SessionExpirationPolicyCustomExpiryAdapter adapter =
			new SessionExpirationPolicyCustomExpiryAdapter(this.mockSessionExpirationPolicy);

		ExpirationAttributes expirationAttributes = adapter.getExpiry(mockRegionEntry);

		assertThat(expirationAttributes).isNotNull();
		assertThat(expirationAttributes.getAction()).isEqualTo(ExpirationAction.DESTROY);
		assertThat(expirationAttributes.getTimeout()).isEqualTo(expirationTimeout.getSeconds());

		verify(mockRegionEntry, times(1)).getValue();
		verify(this.mockSessionExpirationPolicy, times(1)).determineExpirationTimeout(eq(mockSession));
		verify(this.mockSessionExpirationPolicy, times(1)).getExpirationAction();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void getExpiryWithPdxInstanceAndOverflowedExpirationTimeoutAndNullExpirationActionReturnsExpirationAttributes() {

		Duration expirationTimeout = Duration.ofSeconds(987654321369L);

		when(this.mockSessionExpirationPolicy.determineExpirationTimeout(any(Session.class)))
			.thenReturn(Optional.of(expirationTimeout));

		when(this.mockSessionExpirationPolicy.getExpirationAction()).thenReturn(null);

		Session mockSession = mock(Session.class);

		Region.Entry<String, Object> mockRegionEntry = mock(Region.Entry.class);

		PdxInstance mockPdxInstance = mock(PdxInstance.class);

		when(mockPdxInstance.getObject()).thenReturn(mockSession);
		when(mockRegionEntry.getValue()).thenReturn(mockPdxInstance);

		SessionExpirationPolicyCustomExpiryAdapter adapter =
			new SessionExpirationPolicyCustomExpiryAdapter(this.mockSessionExpirationPolicy);

		ExpirationAttributes expirationAttributes = adapter.getExpiry(mockRegionEntry);

		assertThat(expirationAttributes).isNotNull();
		assertThat(expirationAttributes.getAction()).isEqualTo(ExpirationAction.INVALIDATE);
		assertThat(expirationAttributes.getTimeout()).isEqualTo(Integer.MAX_VALUE);

		verify(mockPdxInstance, times(1)).getObject();
		verify(mockRegionEntry, times(1)).getValue();
		verify(this.mockSessionExpirationPolicy, times(1)).determineExpirationTimeout(eq(mockSession));
		verify(this.mockSessionExpirationPolicy, times(1)).getExpirationAction();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void getExpiryWithUnderflowExpirationTimeoutReturnsExpirationAttributes() {

		Duration expirationTimeout = Duration.ofSeconds(-120L);

		SessionExpirationPolicy.ExpirationAction expirationAction = SessionExpirationPolicy.ExpirationAction.DESTROY;

		when(this.mockSessionExpirationPolicy.determineExpirationTimeout(any(Session.class)))
			.thenReturn(Optional.of(expirationTimeout));

		when(this.mockSessionExpirationPolicy.getExpirationAction()).thenReturn(expirationAction);

		Session mockSession = mock(Session.class);

		Region.Entry<String, Object> mockRegionEntry = mock(Region.Entry.class);

		when(mockRegionEntry.getValue()).thenReturn(mockSession);

		SessionExpirationPolicyCustomExpiryAdapter adapter =
			new SessionExpirationPolicyCustomExpiryAdapter(this.mockSessionExpirationPolicy);

		ExpirationAttributes expirationAttributes = adapter.getExpiry(mockRegionEntry);

		assertThat(expirationAttributes).isNotNull();
		assertThat(expirationAttributes.getAction()).isEqualTo(ExpirationAction.DESTROY);
		assertThat(expirationAttributes.getTimeout()).isEqualTo(1);

		verify(mockRegionEntry, times(1)).getValue();
		verify(this.mockSessionExpirationPolicy, times(1)).determineExpirationTimeout(eq(mockSession));
		verify(this.mockSessionExpirationPolicy, times(1)).getExpirationAction();
	}
}
