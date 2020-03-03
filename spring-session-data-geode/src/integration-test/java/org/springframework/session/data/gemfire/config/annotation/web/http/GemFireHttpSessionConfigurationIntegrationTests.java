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
import static org.springframework.data.gemfire.util.ArrayUtils.nullSafeArray;

import java.io.DataInput;
import java.io.DataOutput;
import java.util.Arrays;

import org.junit.Test;

import org.apache.geode.DataSerializer;
import org.apache.geode.cache.GemFireCache;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.internal.InternalDataSerializer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.data.gemfire.tests.integration.SpringApplicationContextIntegrationTestsSupport;
import org.springframework.data.gemfire.tests.mock.annotation.EnableGemFireMockObjects;
import org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy;
import org.springframework.session.data.gemfire.serialization.SessionSerializer;
import org.springframework.session.data.gemfire.serialization.data.AbstractDataSerializableSessionSerializer;
import org.springframework.session.data.gemfire.serialization.data.provider.DataSerializableSessionSerializer;
import org.springframework.session.data.gemfire.serialization.pdx.support.PdxSerializerSessionSerializerAdapter;

/**
 * The GemFireHttpSessionConfigurationIntegrationTests class...
 *
 * @author John Blum
 * @since 1.0.0
 */
@SuppressWarnings("unused")
public class GemFireHttpSessionConfigurationIntegrationTests extends SpringApplicationContextIntegrationTestsSupport {

	private void assertDataSerializerRegistered(DataSerializer dataSerializer) {
		assertDataSerializerRegistered(dataSerializer.getClass());
	}

	private void assertDataSerializerRegistered(Class<? extends DataSerializer> dataSerializerType) {

		assertThat(Arrays.stream(nullSafeArray(InternalDataSerializer.getSerializers(), DataSerializer.class))
			.map(Object::getClass)
			.filter(dataSerializerType::isAssignableFrom)
			.findFirst()
			.orElse(null)).isNotNull();
	}

	private void testUsesDataSerialization(Class<? extends DataSerializer> expectedDataSerializerType) {

		GemFireHttpSessionConfiguration configuration =
			getApplicationContext().getBean(GemFireHttpSessionConfiguration.class);

		assertThat(configuration).isNotNull();
		assertThat(configuration.isUsingDataSerialization()).isTrue();

		GemFireCache gemfireCache = getApplicationContext().getBean(GemFireCache.class);

		assertThat(gemfireCache).isNotNull();
		assertThat(gemfireCache.getPdxSerializer()).isNull();

		DataSerializer dataSerializer =
			getApplicationContext().getBean(configuration.getSessionSerializerBeanName(), expectedDataSerializerType);

		assertThat(dataSerializer).isInstanceOf(expectedDataSerializerType);
		assertDataSerializerRegistered(dataSerializer);
	}

	@Test
	public void usesDataSerializationWhenDataSerializableSessionSerializerConfigured() {

		newApplicationContext(DataSerializableSessionSerializerConfiguration.class);

		testUsesDataSerialization(DataSerializableSessionSerializer.class);
	}

	@Test
	public void usesDataSerializationWhenTestDataSerializerConfigured() {

		newApplicationContext(TestDataSerializerConfiguration.class);

		testUsesDataSerialization(TestDataSerializer.class);
	}

	@Test
	public void notUsingDataSerializationWhenPdxConfigured() {

		newApplicationContext(TestSessionSerializerConfiguration.class);

		GemFireHttpSessionConfiguration configuration =
			getApplicationContext().getBean(GemFireHttpSessionConfiguration.class);

		assertThat(configuration).isNotNull();
		assertThat(configuration.isUsingDataSerialization()).isFalse();

		GemFireCache gemfireCache = getApplicationContext().getBean(GemFireCache.class);

		SessionSerializer testSessionSerializer =
			getApplicationContext().getBean("TestSessionSerializer", SessionSerializer.class);

		assertThat(gemfireCache).isNotNull();
		assertThat(gemfireCache.getPdxSerializer()).isInstanceOf(PdxSerializerSessionSerializerAdapter.class);
		assertThat(((PdxSerializerSessionSerializerAdapter<?>) gemfireCache.getPdxSerializer()).getSessionSerializer())
			.isEqualTo(testSessionSerializer);

		assertThat(Arrays.stream(nullSafeArray(InternalDataSerializer.getSerializers(), DataSerializer.class))
			.filter(testSessionSerializer::equals)
			.findAny()
			.orElse(null)).isNull();
	}

	@Test
	public void exposedSpringSessionGemFireConfigurationIsAccessibleToOtherBeansViaTheAtValueAnnotation() {

		newApplicationContext(ExposingSpringSessionGemFireConfigurationAsPropertiesConfiguration.class);

		SpringSessionGemFireProperties properties =
			getApplicationContext().getBean(SpringSessionGemFireProperties.class);

		assertThat(properties).isNotNull();
		assertThat(properties.clientRegionShortcut()).isEqualTo(ClientRegionShortcut.LOCAL);
		assertThat(properties.indexableSessionAttributes()).containsExactly("AttributeOne", "AttributeTwo");
		assertThat(properties.maxInactiveIntervalInSeconds()).isEqualTo(300);
		assertThat(properties.poolName()).isEqualTo("DEFAULT");
		assertThat(properties.regionName()).isEqualTo("Sessions");
		assertThat(properties.serverRegionShortcut()).isEqualTo(RegionShortcut.REPLICATE);
		assertThat(properties.sessionExpirationPolicyBeanName()).isEqualTo("MockSessionExpirationPolicy");
		assertThat(properties.sessionSerializerBeanName()).isEqualTo("MockSessionSerializer");
	}

	@ClientCacheApplication
	@EnableGemFireMockObjects
	@EnableGemFireHttpSession(
		clientRegionShortcut = ClientRegionShortcut.LOCAL,
		exposeConfigurationAsProperties = true,
		indexableSessionAttributes = { "AttributeOne", "AttributeTwo" },
		maxInactiveIntervalInSeconds = 300,
		poolName = "DEFAULT",
		regionName = "Sessions",
		serverRegionShortcut = RegionShortcut.REPLICATE,
		sessionExpirationPolicyBeanName = "MockSessionExpirationPolicy",
		sessionSerializerBeanName = "MockSessionSerializer"
	)
	static class ExposingSpringSessionGemFireConfigurationAsPropertiesConfiguration {

		@Bean("MockSessionExpirationPolicy")
		SessionExpirationPolicy mockSessionExpirationPolicy() {
			return mock(SessionExpirationPolicy.class);
		}

		@Bean("MockSessionSerializer")
		SessionSerializer mockSessionSerializer() {
			return mock(SessionSerializer.class);
		}

		@Bean("SpringSessionGemFireProperties")
		SpringSessionGemFireProperties springSessionGemFireProperties(
			@Value("${spring.session.data.gemfire.cache.client.region.shortcut}") ClientRegionShortcut clientRegionShortcut,
			@Value("${spring.session.data.gemfire.session.attributes.indexed}") String[] indexedSessionAttributes,
			@Value("${spring.session.data.gemfire.session.expiration.max-inactive-interval-seconds}") int maxInactiveIntervalInSeconds,
			@Value("${spring.session.data.gemfire.cache.client.pool.name}") String poolName,
			@Value("${spring.session.data.gemfire.session.region.name}") String regionName,
			@Value("${spring.session.data.gemfire.cache.server.region.shortcut}") RegionShortcut serverRegionShortcut,
			@Value("${spring.session.data.gemfire.session.expiration.bean-name}") String sessionExpirationPolicyBeanName,
			@Value("${spring.session.data.gemfire.session.serializer.bean-name}") String sessionSerializerBeanName) {

			return new SpringSessionGemFireProperties() {

				@Override
				public ClientRegionShortcut clientRegionShortcut() {
					return clientRegionShortcut;
				}

				@Override
				public String[] indexableSessionAttributes() {
					return indexedSessionAttributes;
				}

				@Override
				public int maxInactiveIntervalInSeconds() {
					return maxInactiveIntervalInSeconds;
				}

				@Override
				public String poolName() {
					return poolName;
				}

				@Override
				public String regionName() {
					return regionName;
				}

				@Override
				public RegionShortcut serverRegionShortcut() {
					return serverRegionShortcut;
				}

				@Override
				public String sessionExpirationPolicyBeanName() {
					return sessionExpirationPolicyBeanName;
				}

				@Override
				public String sessionSerializerBeanName() {
					return sessionSerializerBeanName;
				}
			};
		}
	}

	@ClientCacheApplication
	@EnableGemFireMockObjects
	@EnableGemFireHttpSession(
		poolName = "DEFAULT",
		sessionSerializerBeanName = GemFireHttpSessionConfiguration.SESSION_DATA_SERIALIZER_BEAN_NAME
	)
	static class DataSerializableSessionSerializerConfiguration { }

	@ClientCacheApplication
	@EnableGemFireMockObjects
	@EnableGemFireHttpSession(poolName = "DEFAULT", sessionSerializerBeanName = "TestDataSerializer")
	static class TestDataSerializerConfiguration {

		@Bean("TestDataSerializer")
		DataSerializer testDataSerializer() {
			return new TestDataSerializer();
		}
	}

	@ClientCacheApplication
	@EnableGemFireMockObjects
	@EnableGemFireHttpSession(poolName = "DEFAULT", sessionSerializerBeanName = "TestSessionSerializer")
	static class TestSessionSerializerConfiguration {

		@Bean("TestSessionSerializer")
		SessionSerializer testSessionSerializer() {
			return mock(SessionSerializer.class);
		}
	}

	interface SpringSessionGemFireProperties {

		ClientRegionShortcut clientRegionShortcut();

		String[] indexableSessionAttributes();

		int maxInactiveIntervalInSeconds();

		String poolName();

		String regionName();

		RegionShortcut serverRegionShortcut();

		String sessionExpirationPolicyBeanName();

		String sessionSerializerBeanName();
	}

	static class TestDataSerializer extends AbstractDataSerializableSessionSerializer<Object> {

		@Override
		public Class<?>[] getSupportedClasses() {
			return new Class[] { Object.class };
		}

		@Override
		public void serialize(Object session, DataOutput dataOutput) {
			throw new UnsupportedOperationException("Not Implemented");
		}

		@Override
		public Object deserialize(DataInput dataInput) {
			throw new UnsupportedOperationException("Not Implemented");
		}
	}

	static class TestSessionSerializer implements SessionSerializer<Object, DataInput, DataOutput> {

		@Override
		public void serialize(Object session, DataOutput dataOutput) {
			throw new UnsupportedOperationException("Not Implemented");
		}

		@Override
		public Object deserialize(DataInput dataInput) {
			throw new UnsupportedOperationException("Not Implemented");
		}

		@Override
		public boolean canSerialize(Class<?> type) {
			return false;
		}
	}
}
