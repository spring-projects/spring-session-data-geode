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

import static org.mockito.Mockito.mock;

import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.Pool;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.gemfire.tests.integration.IntegrationTestsSupport;
import org.springframework.data.gemfire.tests.mock.GemFireMockObjectsSupport;
import org.springframework.data.gemfire.tests.util.ObjectUtils;
import org.springframework.data.gemfire.tests.util.ReflectionUtils;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;

/**
 * {@link AbstractGemFireUnitTests} is an abstract base class encapsulating functionality common to all Unit Tests
 * in the SSDG test suite.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.apache.geode.cache.client.ClientCache
 * @see org.apache.geode.cache.client.Pool
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.data.gemfire.tests.mock.GemFireMockObjectsSupport
 * @see org.springframework.data.gemfire.tests.integration.IntegrationTestsSupport
 * @since 2.5.0
 */
public class AbstractGemFireUnitTests extends IntegrationTestsSupport {

	protected <T> T getValue(Object target, String fieldName) {
		return ObjectUtils.doOperationSafely(() -> ReflectionUtils.getFieldValue(target, fieldName), null);
	}

	@Configuration
	protected static class BaseTestConfiguration {

		@Bean
		ClientCache mockClientCache() {
			return GemFireMockObjectsSupport.mockClientCache();
		}

		@Bean(GemFireHttpSessionConfiguration.DEFAULT_POOL_NAME)
		Pool mockPool() {
			return mock(Pool.class);
		}
	}
}
