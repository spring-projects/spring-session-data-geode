/*
 * Copyright 2020 the original author or authors.
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
package org.springframework.session.data.gemfire.config.annotation.web.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.client.Pool;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.data.gemfire.tests.mock.annotation.EnableGemFireMockObjects;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Unit Tests testing the configuration of Spring Session for Apache Geode (SSDG)
 * using {@literal spring-session.properties}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.data.gemfire.config.annotation.ClientCacheApplication
 * @see org.springframework.data.gemfire.tests.mock.annotation.EnableGemFireMockObjects
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @since 2.6.0
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = GemFireHttpSessionConfigurationWithSpringSessionPropertiesUnitTests.TestConfiguration.class)
public class GemFireHttpSessionConfigurationWithSpringSessionPropertiesUnitTests {

	@BeforeClass
	public static void setSpringSessionPropertiesLocation() {
		System.setProperty("spring-session-properties-location", "test-spring-session.properties");
	}

	@AfterClass
	public static void clearSpringSessionPropertiesLocation() {
		System.clearProperty("spring-session-properties-location");
	}

	@Autowired
	private GemFireHttpSessionConfiguration sessionConfiguration;

	@Before
	public void setup() {
		assertThat(this.sessionConfiguration).isNotNull();
	}

	@Test
	public void springSessionConfigurationIsCorrect() {

		assertThat(this.sessionConfiguration.getClientRegionShortcut()).isEqualTo(ClientRegionShortcut.CACHING_PROXY);
		assertThat(this.sessionConfiguration.getIndexableSessionAttributes()).containsExactly("username", "userId");
		assertThat(this.sessionConfiguration.getMaxInactiveIntervalInSeconds()).isEqualTo(300);
		assertThat(this.sessionConfiguration.getPoolName()).isEqualTo("CarPool");
		assertThat(this.sessionConfiguration.getServerRegionShortcut()).isEqualTo(RegionShortcut.REPLICATE);
		assertThat(this.sessionConfiguration.getSessionExpirationPolicyBeanName().orElse(null)).isEqualTo("TestSessionExpirationPolicyBean");
		assertThat(this.sessionConfiguration.getSessionRegionName()).isEqualTo("TestSessions");
		assertThat(this.sessionConfiguration.getSessionSerializerBeanName()).isEqualTo(GemFireHttpSessionConfiguration.SESSION_DATA_SERIALIZER_BEAN_NAME);
	}

	@ClientCacheApplication
	@EnableGemFireHttpSession
	@EnableGemFireMockObjects
	static class TestConfiguration {

		@Bean("CarPool")
		Pool mockPool() {
			return mock(Pool.class);
		}
	}
}
