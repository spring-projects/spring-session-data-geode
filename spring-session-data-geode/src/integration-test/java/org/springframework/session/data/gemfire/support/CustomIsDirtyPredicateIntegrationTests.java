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

package org.springframework.session.data.gemfire.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.context.annotation.Bean;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.data.gemfire.tests.mock.annotation.EnableGemFireMockObjects;
import org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests;
import org.springframework.session.data.gemfire.GemFireOperationsSessionRepository;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * The CustomIsDirtyPredicateIntegrationTests class...
 *
 * @author John Blum
 * @since 1.0.0
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
public class CustomIsDirtyPredicateIntegrationTests extends AbstractGemFireIntegrationTests {

	@Before
	public void setup() {

		GemFireOperationsSessionRepository sessionRepository = getSessionRepository();

		assertThat(sessionRepository).isNotNull();
		assertThat(sessionRepository.getIsDirtyPredicate())
			.isInstanceOf(OddNumberDirtyPredicateStrategy.class);
	}

	@Test
	public void isDirtyStrategyIsCorrect() {

		GemFireSession<?> session = commit(createSession());

		assertThat(session).isNotNull();
		assertThat(session.hasDelta()).isFalse();

		session.setAttribute("1", 2);

		assertThat(session.getAttributeNames()).containsExactly("1");
		assertThat(session.<Integer>getAttribute("1")).isEqualTo(2);
		assertThat(session.hasDelta()).isFalse();

		session.setAttribute("2", 1);

		assertThat(session.getAttributeNames()).containsOnly("1", "2");
		assertThat(session.<Integer>getAttribute("1")).isEqualTo(2);
		assertThat(session.<Integer>getAttribute("2")).isEqualTo(1);
		assertThat(session.hasDelta()).isTrue();

		commit(session);

		assertThat(session.getAttributeNames()).containsOnly("1", "2");
		assertThat(session.hasDelta()).isFalse();

		session.setAttribute("2", 1);

		assertThat(session.getAttributeNames()).containsOnly("1", "2");
		assertThat(session.<Integer>getAttribute("1")).isEqualTo(2);
		assertThat(session.<Integer>getAttribute("2")).isEqualTo(1);
		assertThat(session.hasDelta()).isTrue();

		commit(session);

		assertThat(session.getAttributeNames()).containsOnly("1", "2");
		assertThat(session.hasDelta()).isFalse();

		session.setAttribute("3", 4);

		assertThat(session.getAttributeNames()).containsOnly("1", "2", "3");
		assertThat(session.<Integer>getAttribute("1")).isEqualTo(2);
		assertThat(session.<Integer>getAttribute("2")).isEqualTo(1);
		assertThat(session.<Integer>getAttribute("3")).isEqualTo(4);
		assertThat(session.hasDelta()).isFalse();

		session.setAttribute("2", 3);

		assertThat(session.getAttributeNames()).containsOnly("1", "2", "3");
		assertThat(session.<Integer>getAttribute("1")).isEqualTo(2);
		assertThat(session.<Integer>getAttribute("2")).isEqualTo(3);
		assertThat(session.<Integer>getAttribute("3")).isEqualTo(4);
		assertThat(session.hasDelta()).isTrue();
	}

	@ClientCacheApplication
	@EnableGemFireMockObjects
	@EnableGemFireHttpSession(
		poolName = "DEFAULT",
		sessionSerializerBeanName = GemFireHttpSessionConfiguration.SESSION_DATA_SERIALIZER_BEAN_NAME
	)
	@SuppressWarnings("unused")
	static class TestConfiguration {

		@Bean
		IsDirtyPredicate oddNumberDirtyPredicate() {
			return new OddNumberDirtyPredicateStrategy();
		}
	}

	static class OddNumberDirtyPredicateStrategy implements IsDirtyPredicate {

		@Override
		public boolean isDirty(Object oldValue, Object newValue) {
			return toInteger(newValue) % 2 != 0;
		}

		private int toInteger(Object value) {

			return value instanceof Number
				? ((Number) value).intValue()
				: Integer.parseInt(String.valueOf(value));
		}
	}
}
