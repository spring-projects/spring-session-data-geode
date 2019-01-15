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

package org.springframework.session.data.gemfire.serialization.pdx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.NotSerializableException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.geode.DeltaSerializationException;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.data.gemfire.config.annotation.CacheServerApplication;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.data.gemfire.config.annotation.EnablePdx;
import org.springframework.data.gemfire.config.annotation.PeerCacheConfigurer;
import org.springframework.data.gemfire.mapping.MappingPdxSerializer;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * The SessionSerializationWithDataSerializationDeltasAndPdxIntegrationTests class...
 *
 * @author John Blum
 * @since 1.0.0
 */
@SuppressWarnings("unused")
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = SessionSerializationWithDataSerializationDeltasAndPdxIntegrationTests.GemFireClientConfiguration.class)
public class SessionSerializationWithDataSerializationDeltasAndPdxIntegrationTests
		extends AbstractGemFireIntegrationTests {

	private CacheFactoryBean cache;

	private static final String GEMFIRE_LOG_LEVEL = "error";

	@BeforeClass
	public static void startGemFireServer() throws IOException {
		startGemFireServer(GemFireServerConfiguration.class);
	}

	@Test
	public void sessionDataSerializationWithCustomerPdxSerializationWorksAsExpected() {

		Customer jonDoe = Customer.newCustomer("Jon Doe");
		Customer janeDoe = Customer.newCustomer("Jane Doe");

		Session session = createSession();

		assertThat(session).isNotNull();
		assertThat(session.getId()).isNotEmpty();
		assertThat(session.isExpired()).isFalse();
		assertThat(session.getAttributeNames()).isEmpty();

		session.setAttribute("1", jonDoe);

		assertThat(session.getAttributeNames()).containsExactly("1");
		assertThat(session.<Customer>getAttribute("1")).isEqualTo(jonDoe);

		save(touch(session));

		Session loadedSession = get(session.getId());

		assertThat(loadedSession).isNotNull();
		assertThat(loadedSession).isNotSameAs(session);
		assertThat(loadedSession.getId()).isEqualTo(session.getId());
		assertThat(loadedSession.isExpired()).isFalse();
		assertThat(loadedSession.getAttributeNames()).containsExactly("1");
		assertThat(loadedSession.<Customer>getAttribute("1")).isEqualTo(jonDoe);
		assertThat(loadedSession.<Customer>getAttribute("1")).isNotSameAs(jonDoe);

		loadedSession.setAttribute("2", janeDoe);

		assertThat(loadedSession.getAttributeNames()).containsOnly("1", "2");
		assertThat(loadedSession.<Customer>getAttribute("1")).isEqualTo(jonDoe);
		assertThat(loadedSession.<Customer>getAttribute("2")).isEqualTo(janeDoe);

		save(touch(loadedSession));

		Session reloadedSession = get(loadedSession.getId());

		assertThat(reloadedSession).isNotNull();
		assertThat(reloadedSession).isNotSameAs(loadedSession);
		assertThat(reloadedSession.getId()).isEqualTo(loadedSession.getId());
		assertThat(reloadedSession.isExpired()).isFalse();
		assertThat(reloadedSession.getAttributeNames()).containsOnly("1", "2");
		assertThat(reloadedSession.<Customer>getAttribute("1")).isEqualTo(jonDoe);
		assertThat(reloadedSession.<Customer>getAttribute("2")).isEqualTo(janeDoe);
	}

	@Test(expected = DeltaSerializationException.class)
	public void serializationOfNonSerializableTypeThrowsException() {

		NonSerializableType value = NonSerializableType.of("test");

		Session session = createSession();

		assertThat(session).isNotNull();
		assertThat(session.getId()).isNotEmpty();
		assertThat(session.isExpired()).isFalse();
		assertThat(session.getAttributeNames()).isEmpty();

		session.setAttribute("9", value);

		assertThat(session.getAttributeNames()).containsExactly("9");
		assertThat(session.<NonSerializableType>getAttribute("9")).isEqualTo(value);

		try {
			save(touch(session));
		}
		catch (Exception expected) {

			assertThat(expected).isInstanceOf(DeltaSerializationException.class);
			assertThat(expected).hasCauseInstanceOf(NotSerializableException.class);
			assertThat(expected.getCause()).hasMessageContaining(NonSerializableType.class.getName());

			throw expected;
		}
	}

	@ClientCacheApplication(logLevel = GEMFIRE_LOG_LEVEL, subscriptionEnabled = true)
	@EnableGemFireHttpSession(
		poolName = "DEFAULT",
		sessionSerializerBeanName = GemFireHttpSessionConfiguration.SESSION_DATA_SERIALIZER_BEAN_NAME
	)
	@EnablePdx(serializerBeanName = "customerPdxSerializer")
	static class GemFireClientConfiguration {

		@Bean
		MappingPdxSerializer customerPdxSerializer() {

			MappingPdxSerializer pdxSerializer = new MappingPdxSerializer();

			pdxSerializer.setIncludeTypeFilters(type -> type != null && Customer.class.isAssignableFrom(type));

			return pdxSerializer;
		}
	}

	@CacheServerApplication(
		name = "SessionSerializationWithDataSerializationDeltasAndPdxIntegrationTests",
		logLevel = GEMFIRE_LOG_LEVEL
	)
	@EnableGemFireHttpSession(sessionSerializerBeanName = GemFireHttpSessionConfiguration.SESSION_DATA_SERIALIZER_BEAN_NAME)
	static class GemFireServerConfiguration {

		@Bean
		PeerCacheConfigurer pdxReadSerializedConfigurer() {
			return (beanName, cacheFactoryBean) -> cacheFactoryBean.setPdxReadSerialized(true);
		}

		public static void main(String[] args) {

			AnnotationConfigApplicationContext applicationContext =
				new AnnotationConfigApplicationContext(GemFireServerConfiguration.class);

			applicationContext.registerShutdownHook();
		}
	}

	@Data
	@EqualsAndHashCode
	@RequiredArgsConstructor(staticName = "newCustomer")
	static class Customer {

		@NonNull
		private String name;

		@Override
		public String toString() {
			return getName();
		}
	}

	@Data
	@EqualsAndHashCode
	@RequiredArgsConstructor(staticName = "of")
	static class NonSerializableType {

		@NonNull
		private String value;

		@Override
		public String toString() {
			return getValue();
		}
	}
}
