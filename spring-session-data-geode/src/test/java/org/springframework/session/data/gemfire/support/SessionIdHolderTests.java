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

package org.springframework.session.data.gemfire.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import org.springframework.session.Session;

/**
 * Unit tests for {@link SessionIdHolder}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.support.SessionIdHolder
 * @since 2.0.0
 */
public class SessionIdHolderTests {

	@Test
	public void createSessionIdHolderWithId() {

		SessionIdHolder session = SessionIdHolder.create("12345");

		assertThat(session).isNotNull();
		assertThat(session.getId()).isEqualTo("12345");
	}

	@Test(expected = IllegalArgumentException.class)
	public void createSessionIdHolderWithEmptyId() {

		try {
			SessionIdHolder.create("  ");
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("Session ID [  ] is required");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void createSessionIdHolderWithNoId() {

		try {
			SessionIdHolder.create(null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("Session ID [null] is required");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void equalsWithSameSessionReturnsTrue() {

		Session session = SessionIdHolder.create("12345");

		assertThat(session.equals(session)).isTrue();
	}

	@Test
	public void equalsWithEqualSessionsReturnsTrue() {

		Session sessionOne = SessionIdHolder.create("12345");
		Session sessionTwo = SessionIdHolder.create("12345");

		assertThat(sessionOne.equals(sessionTwo)).isTrue();
	}

	@Test
	@SuppressWarnings("all")
	public void equalsWithUnequalSessionsReturnsFalse() {

		Session sessionOne = SessionIdHolder.create("123");
		Session sessionTwo = SessionIdHolder.create("12345");

		assertThat(sessionOne.equals(sessionTwo)).isFalse();
		assertThat(sessionTwo.equals(null)).isFalse();
	}

	@Test
	public void hashCodeForSameSessionIsEqual() {

		Session session = SessionIdHolder.create("12345");

		assertThat(session.hashCode()).isEqualTo(session.hashCode());
	}

	@Test
	public void hashCodeWithEqualSessionsIsEqual() {

		Session sessionOne = SessionIdHolder.create("12345");
		Session sessionTwo = SessionIdHolder.create("12345");

		assertThat(sessionOne.hashCode()).isEqualTo(sessionTwo.hashCode());
	}

	@Test
	public void hashCodeForUnequalSessionsAreNotEqual() {

		Session sessionOne = SessionIdHolder.create("123");
		Session sessionTwo = SessionIdHolder.create("12345");

		assertThat(sessionOne.hashCode()).isNotEqualTo(sessionTwo.hashCode());
	}

	@Test
	public void toStringReturnsSessionId() {
		assertThat(SessionIdHolder.create("12345").toString()).isEqualTo("12345");
	}
}
