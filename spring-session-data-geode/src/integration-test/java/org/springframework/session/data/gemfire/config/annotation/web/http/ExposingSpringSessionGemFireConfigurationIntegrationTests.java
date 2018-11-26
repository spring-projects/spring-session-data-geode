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

package org.springframework.session.data.gemfire.config.annotation.web.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.After;
import org.junit.Test;

import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.client.Pool;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.data.gemfire.tests.mock.annotation.EnableGemFireMockObjects;
import org.springframework.mock.env.MockPropertySource;
import org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests;
import org.springframework.session.data.gemfire.config.annotation.web.http.support.SpringSessionGemFireConfigurer;
import org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy;
import org.springframework.session.data.gemfire.serialization.SessionSerializer;

/**
 * The ExposingSpringSessionGemFireConfigurationIntegrationTests class...
 *
 * @author John Blum
 * @since 1.0.0
 */
@SuppressWarnings("unused")
public class ExposingSpringSessionGemFireConfigurationIntegrationTests extends AbstractGemFireIntegrationTests {

	private ConfigurableApplicationContext applicationContext;

	@After
	public void tearDown() {
		Optional.ofNullable(this.applicationContext).ifPresent(ConfigurableApplicationContext::close);
	}

	private ConfigurableApplicationContext newApplicationContext(Class<?>... annotatedClasses) {
		return newApplicationContext(new MockPropertySource("TestProperties"), annotatedClasses);
	}

	private ConfigurableApplicationContext newApplicationContext(PropertySource<?> testPropertySource,
			Class<?>... annotatedClasses) {

		AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();

		applicationContext.getEnvironment().getPropertySources().addFirst(testPropertySource);
		applicationContext.register(annotatedClasses);
		applicationContext.registerShutdownHook();
		applicationContext.refresh();

		return applicationContext;
	}

	@Test
	public void exposeAnnotationAttributesAsProperties() {

		this.applicationContext = newApplicationContext(TestGemFireHttpSessionConfiguration.class);

		Environment environment = this.applicationContext.getEnvironment();

		assertThat(environment).isNotNull();
		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.cache.client.region.shortcut"))
			.isEqualTo(ClientRegionShortcut.LOCAL.name());

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.configuration.expose"))
			.isEqualTo(Boolean.TRUE.toString());

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.attributes.indexed"))
			.isEqualTo("one,two");

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.expiration.max-inactive-interval-seconds"))
			.isEqualTo("600");

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.cache.client.pool.name"))
			.isEqualTo("Car");

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.region.name"))
			.isEqualTo("Sessions");

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.cache.server.region.shortcut"))
			.isEqualTo(RegionShortcut.REPLICATE.name());

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.expiration.bean-name"))
			.isEqualTo("AttributeSessionExpirationPolicy");

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.serializer.bean-name"))
			.isEqualTo("AttributeSessionSerializer");
	}

	@Test
	public void exposePropertiesAsPropertiesOverridesAnnotationAttributes() {

		MockPropertySource testPropertySource = new MockPropertySource("TestProperties")
			.withProperty("spring.session.data.gemfire.cache.client.region.shortcut", ClientRegionShortcut.LOCAL_PERSISTENT.name())
			.withProperty("spring.session.data.gemfire.session.configuration.expose", "true")
			.withProperty("spring.session.data.gemfire.session.attributes.indexed", "one, two, three")
			.withProperty("spring.session.data.gemfire.session.expiration.max-inactive-interval-seconds", "900")
			.withProperty("spring.session.data.gemfire.cache.client.pool.name", "Dead")
			.withProperty("spring.session.data.gemfire.session.region.name", "PropertySessions")
			.withProperty("spring.session.data.gemfire.cache.server.region.shortcut", RegionShortcut.REPLICATE_PERSISTENT.name())
			.withProperty("spring.session.data.gemfire.session.expiration.bean-name", "PropertySessionExpirationPolicy")
			.withProperty("spring.session.data.gemfire.session.serializer.bean-name", "PropertySessionSerializer");

		this.applicationContext = newApplicationContext(testPropertySource, TestGemFireHttpSessionConfiguration.class);

		Environment environment = this.applicationContext.getEnvironment();

		assertThat(environment).isNotNull();
		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.cache.client.region.shortcut"))
			.isEqualTo(ClientRegionShortcut.LOCAL_PERSISTENT.name());

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.configuration.expose"))
			.isEqualTo(Boolean.TRUE.toString());

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.attributes.indexed"))
			.isEqualTo("one,two,three");

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.expiration.max-inactive-interval-seconds"))
			.isEqualTo("900");

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.cache.client.pool.name"))
			.isEqualTo("Dead");

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.region.name"))
			.isEqualTo("PropertySessions");

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.cache.server.region.shortcut"))
			.isEqualTo(RegionShortcut.REPLICATE_PERSISTENT.name());

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.expiration.bean-name"))
			.isEqualTo("PropertySessionExpirationPolicy");

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.serializer.bean-name"))
			.isEqualTo("PropertySessionSerializer");
	}

	@Test
	public void exposeConfigurerConfigurationAsPropertiesOverridesAnnotationAttributesAndProperties() {

		MockPropertySource testPropertySource = new MockPropertySource("TestProperties")
			.withProperty("spring.session.data.gemfire.cache.client.region.shortcut", ClientRegionShortcut.LOCAL_PERSISTENT.name())
			.withProperty("spring.session.data.gemfire.session.configuration.expose", "false")
			.withProperty("spring.session.data.gemfire.session.attributes.indexed", "one, two, three")
			.withProperty("spring.session.data.gemfire.session.expiration.max-inactive-interval-seconds", "900")
			.withProperty("spring.session.data.gemfire.cache.client.pool.name", "Dead")
			.withProperty("spring.session.data.gemfire.session.region.name", "PropertySessions")
			.withProperty("spring.session.data.gemfire.cache.server.region.shortcut", RegionShortcut.REPLICATE_PERSISTENT.name())
			.withProperty("spring.session.data.gemfire.session.expiration.bean-name", "PropertySessionExpirationPolicy")
			.withProperty("spring.session.data.gemfire.session.serializer.bean-name", "PropertySessionSerializer");

		this.applicationContext = newApplicationContext(testPropertySource, TestGemFireHttpSessionConfiguration.class,
			TestSpringSessionGemFireConfigurerConfiguration.class);

		Environment environment = this.applicationContext.getEnvironment();

		assertThat(environment).isNotNull();
		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.cache.client.region.shortcut"))
			.isEqualTo(ClientRegionShortcut.CACHING_PROXY.name());

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.configuration.expose"))
			.isEqualTo(Boolean.TRUE.toString());

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.attributes.indexed"))
			.isEqualTo("two,four");

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.expiration.max-inactive-interval-seconds"))
			.isEqualTo("300");

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.cache.client.pool.name"))
			.isEqualTo("Swimming");

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.region.name"))
			.isEqualTo("ConfigurerSessions");

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.cache.server.region.shortcut"))
			.isEqualTo(RegionShortcut.PARTITION_REDUNDANT.name());

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.expiration.bean-name"))
			.isEqualTo("ConfigurerSessionExpirationPolicy");

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.serializer.bean-name"))
			.isEqualTo("ConfigurerSessionSerializer");
	}

	@Test
	public void exposeConfigurationAsPropertiesUsesAnnotationAttributesConfigurerConfigurationAndProperties() {

		MockPropertySource testPropertySource = new MockPropertySource("TestProperties")
			.withProperty("spring.session.data.gemfire.cache.client.region.shortcut", ClientRegionShortcut.LOCAL_PERSISTENT.name())
			.withProperty("spring.session.data.gemfire.session.expiration.max-inactive-interval-seconds", "300")
			.withProperty("spring.session.data.gemfire.cache.client.pool.name", "Dead")
			.withProperty("spring.session.data.gemfire.session.region.name", "PropertySessions");

		this.applicationContext = newApplicationContext(testPropertySource, TestGemFireHttpSessionConfiguration.class,
			MockSpringSessionGemFirerConfigurerConfiguration.class);

		Environment environment = this.applicationContext.getEnvironment();

		assertThat(environment).isNotNull();
		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.cache.client.region.shortcut"))
			.isEqualTo(ClientRegionShortcut.CACHING_PROXY.name());

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.configuration.expose"))
			.isEqualTo(Boolean.TRUE.toString());

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.attributes.indexed"))
			.isEqualTo("one,two");

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.expiration.max-inactive-interval-seconds"))
			.isEqualTo("300");

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.cache.client.pool.name"))
			.isEqualTo("Dead");

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.region.name"))
			.isEqualTo("PropertySessions");

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.cache.server.region.shortcut"))
			.isEqualTo(RegionShortcut.PARTITION_REDUNDANT.name());

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.expiration.bean-name"))
			.isEqualTo("AttributeSessionExpirationPolicy");

		assertThat(environment.getRequiredProperty("spring.session.data.gemfire.session.serializer.bean-name"))
			.isEqualTo("ConfigurerSessionSerializer");
	}

	@ClientCacheApplication
	@EnableGemFireMockObjects
	@EnableGemFireHttpSession(
		clientRegionShortcut = ClientRegionShortcut.LOCAL,
		exposeConfigurationAsProperties = true,
		indexableSessionAttributes = { "one", "two" },
		maxInactiveIntervalInSeconds = 600,
		poolName = "Car",
		regionName = "Sessions",
		serverRegionShortcut = RegionShortcut.REPLICATE,
		sessionExpirationPolicyBeanName = "AttributeSessionExpirationPolicy",
		sessionSerializerBeanName = "AttributeSessionSerializer"
	)
	@SuppressWarnings("unused")
	static class TestGemFireHttpSessionConfiguration {

		@Bean("Car")
		Pool carPool() {
			return mock(Pool.class);
		}

		@Bean("Dead")
		Pool deadPool() {
			return mock(Pool.class);
		}

		@Bean
		SpringSessionGemFireConfigurer emptySpringSessionGemFireConfigurer() {
			return new SpringSessionGemFireConfigurer() { };
		}

		@Bean("AttributeSessionExpirationPolicy")
		SessionExpirationPolicy attributeSessionExpirationPolicy() {
			return mock(SessionExpirationPolicy.class);
		}

		@Bean("PropertySessionExpirationPolicy")
		SessionExpirationPolicy propertySessionExpirationPolicy() {
			return mock(SessionExpirationPolicy.class);
		}

		@Bean("AttributeSessionSerializer")
		SessionSerializer attributeSessionSerializer() {
			return mock(SessionSerializer.class);
		}

		@Bean("PropertySessionSerializer")
		SessionSerializer propertySessionSerializer() {
			return mock(SessionSerializer.class);
		}
	}

	@Configuration
	static class TestSpringSessionGemFireConfigurerConfiguration {

		@Bean("Swimming")
		Pool swimmingPool() {
			return mock(Pool.class);
		}

		@Bean("ConfigurerSessionExpirationPolicy")
		SessionExpirationPolicy configurerSessionExpirationPolicy() {
			return mock(SessionExpirationPolicy.class);
		}

		@Bean("ConfigurerSessionSerializer")
		SessionSerializer configurerSessionSerializer() {
			return mock(SessionSerializer.class);
		}

		@Bean
		@Primary
		SpringSessionGemFireConfigurer testSpringSessionGemFireConfigurer() {

			return new SpringSessionGemFireConfigurer() {

				@Override
				public ClientRegionShortcut getClientRegionShortcut() {
					return ClientRegionShortcut.CACHING_PROXY;
				}

				@Override
				public boolean getExposeConfigurationAsProperties() {
					return true;
				}

				@Override
				public String[] getIndexableSessionAttributes() {
					return new String[] { "two", "four" };
				}

				@Override
				public int getMaxInactiveIntervalInSeconds() {
					return 300;
				}

				@Override
				public String getPoolName() {
					return "Swimming";
				}

				@Override
				public String getRegionName() {
					return "ConfigurerSessions";
				}

				@Override
				public RegionShortcut getServerRegionShortcut() {
					return RegionShortcut.PARTITION_REDUNDANT;
				}

				@Override
				public String getSessionExpirationPolicyBeanName() {
					return "ConfigurerSessionExpirationPolicy";
				}

				@Override
				public String getSessionSerializerBeanName() {
					return "ConfigurerSessionSerializer";
				}
			};
		}
	}

	@Configuration
	static class MockSpringSessionGemFirerConfigurerConfiguration {

		@Bean("ConfigurerSessionSerializer")
		SessionSerializer configurerSessionSerializer() {
			return mock(SessionSerializer.class);
		}

		@Bean
		@Primary
		SpringSessionGemFireConfigurer mockSpringSessionGemFireConfigurer() {

			return new SpringSessionGemFireConfigurer() {

				@Override
				public ClientRegionShortcut getClientRegionShortcut() {
					return ClientRegionShortcut.CACHING_PROXY;
				}

				@Override
				public RegionShortcut getServerRegionShortcut() {
					return RegionShortcut.PARTITION_REDUNDANT;
				}

				@Bean("ConfigurerSessionSerializer")
				SessionSerializer configurerSessionSerializer() {
					return mock(SessionSerializer.class);
				}

				@Override
				public String getSessionSerializerBeanName() {
					return "ConfigurerSessionSerializer";
				}
			};
		}
	}
}
