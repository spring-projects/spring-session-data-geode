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
package org.springframework.session.data.gemfire.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.session.Session;

/**
 * Unit Tests for {@link SessionChangedEvent}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.mockito.junit.MockitoJUnitRunner
 * @see org.springframework.session.Session
 * @since 2.2.0
 */
@RunWith(MockitoJUnitRunner.class)
public class SessionChangedEventUnitTests {

	@Mock
	private Session mockSession;

	@Test
	public void constructSessionChangedEvent() {

		Object source = new Object();

		SessionChangedEvent event = new SessionChangedEvent(source, this.mockSession);

		assertThat(event).isNotNull();
		assertThat(event.getSource()).isEqualTo(source);
		assertThat(event.<Session>getSession()).isEqualTo(this.mockSession);
	}
}
