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

package org.springframework.session.data.gemfire.expiration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy.ExpirationAction;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for {@link SessionExpirationPolicy}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.mockito.junit.MockitoJUnitRunner
 * @see org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy
 * @see org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy.ExpirationAction
 * @since 2.1.0
 */
@RunWith(MockitoJUnitRunner.class)
public class SessionExpirationPolicyUnitTests {

	@Mock
	private SessionExpirationPolicy mockSessionExpirationPolicy;

	@Test
	public void expirationActionDefaultsToInvalidate() {

		when(this.mockSessionExpirationPolicy.getExpirationAction()).thenCallRealMethod();

		assertThat(this.mockSessionExpirationPolicy.getExpirationAction())
			.isEqualTo(SessionExpirationPolicy.ExpirationAction.INVALIDATE);

		verify(this.mockSessionExpirationPolicy, times(1)).getExpirationAction();
	}

	@Test
	public void expirationActionDefaultIfNullReturnsDefault() {
		assertThat(ExpirationAction.defaultIfNull(null)).isEqualTo(ExpirationAction.INVALIDATE);
	}

	@Test
	public void expirationActionDefaultIfNullReturnsExpirationAction() {
		assertThat(ExpirationAction.defaultIfNull(ExpirationAction.DESTROY)).isEqualTo(ExpirationAction.DESTROY);
	}
}
