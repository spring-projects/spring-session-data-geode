/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.session.data.gemfire.serialization.data.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.DataInput;
import java.io.DataOutput;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.gemfire.support.GemfireBeanFactoryLocator;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests;
import org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSession;
import org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSessionAttributes;
import org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;
import org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSessionAttributes;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.session.data.gemfire.serialization.SessionSerializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Integration tests for {@link DataSerializerSessionSerializerAdapter}.
 *
 * @author John Blum
 * @see java.io.DataInput
 * @see java.io.DataOutput
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.data.gemfire.support.GemfireBeanFactoryLocator
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests
 * @see org.springframework.session.data.gemfire.serialization.SessionSerializer
 * @see org.springframework.session.data.gemfire.serialization.data.support.DataSerializerSessionSerializerAdapter
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @since 2.0.0
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@SuppressWarnings("unused")
public class DataSerializerSessionSerializerAdapterIntegrationTests extends AbstractGemFireIntegrationTests {

	@Autowired
	private DataSerializerSessionSerializerAdapter<Session> dataSerializer;

	@Autowired
	@Qualifier(GemFireHttpSessionConfiguration.SESSION_SERIALIZER_BEAN_ALIAS)
	private SessionSerializer<Session, DataInput, DataOutput> sessionSerializer;

	@AfterClass
	public static void tearDown() {
		unregisterAllDataSerializers();
	}

	@Test
	public void constructsAndAutowiresDataSerializerSessionSerializerAdapter() {

		assertThat(this.dataSerializer).isNotNull();
		assertThat(this.sessionSerializer).isNotNull();
		assertThat(this.dataSerializer.getId()).isEqualTo(0xBAC2BAC);
		assertThat(this.dataSerializer.getSupportedClasses())
			.containsExactly(GemFireSession.class, GemFireSessionAttributes.class, DeltaCapableGemFireSession.class,
				DeltaCapableGemFireSessionAttributes.class);
	}

	@Configuration
	@SuppressWarnings("unused")
	static class TestConfiguration {

		@Bean
		GemfireBeanFactoryLocator beanFactoryLocator(BeanFactory beanFactory) {
			return GemfireBeanFactoryLocator.newBeanFactoryLocator(beanFactory, "sessionBeanFactory");
		}

		@Bean
		DataSerializerSessionSerializerAdapter dataSerializer() {
			return new DataSerializerSessionSerializerAdapter();
		}

		@Bean
		@Qualifier(GemFireHttpSessionConfiguration.SESSION_SERIALIZER_BEAN_ALIAS)
		@SuppressWarnings("unchecked")
		SessionSerializer<Session, DataInput, DataOutput> mockSessionSerializer() {
			return mock(SessionSerializer.class, "SessionSerializer");
		}
	}
}
