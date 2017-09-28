/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.session.data.gemfire.serialization.data.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.wiring.BeanConfigurerSupport;
import org.springframework.data.gemfire.support.GemfireBeanFactoryLocator;
import org.springframework.session.Session;

/**
 * The WirableDataSerializerTests class...
 *
 * @author John Blum
 * @since 1.0.0
 */
@RunWith(MockitoJUnitRunner.class)
public class WirableDataSerializerTests {

	@Mock
	private ConfigurableListableBeanFactory mockBeanFactory;

	@Spy
	private WirableDataSerializer<Session> dataSerializer;

	@Test
	public void autowiresThis() {

		AtomicReference<BeanConfigurerSupport> beanConfigurerReference = new AtomicReference<>(null);

		doReturn(Optional.of(this.mockBeanFactory)).when(this.dataSerializer).locateBeanFactory();

		doAnswer(invocation -> {
			BeanConfigurerSupport beanConfigurer = spy((BeanConfigurerSupport) invocation.callRealMethod());
			doNothing().when(beanConfigurer).configureBean(any());
			beanConfigurerReference.compareAndSet(null, beanConfigurer);
			return beanConfigurer;
		}).when(this.dataSerializer).newBeanConfigurer(any(BeanFactory.class));

		this.dataSerializer.autowire();

		assertThat(beanConfigurerReference.get()).isNotNull();

		verify(this.dataSerializer, times(1)).locateBeanFactory();
		verify(this.dataSerializer, times(1)).newBeanConfigurer(eq(this.mockBeanFactory));
		verify(beanConfigurerReference.get(), times(1)).configureBean(eq(this.dataSerializer));
		verify(beanConfigurerReference.get(), times(1)).destroy();
	}

	@Test
	public void noAutowiringWhenBeanFactoryCannotBeLocated() {

		doReturn(Optional.empty()).when(this.dataSerializer).locateBeanFactory();

		this.dataSerializer.autowire();

		verify(this.dataSerializer, times(1)).locateBeanFactory();
		verify(this.dataSerializer, never()).newBeanConfigurer(any(BeanFactory.class));
	}

	@Test
	public void locatesBeanFactory() {

		GemfireBeanFactoryLocator beanFactoryLocator = null;

		try {
			when(this.mockBeanFactory.getAliases(anyString())).thenReturn(new String[0]);

			beanFactoryLocator = GemfireBeanFactoryLocator.newBeanFactoryLocator(this.mockBeanFactory,
				"testBeanFactory");

			assertThat(this.dataSerializer.locateBeanFactory().orElse(null)).isSameAs(this.mockBeanFactory);
		}
		finally {
			verify(this.mockBeanFactory, times(1)).getAliases(eq("testBeanFactory"));
			Optional.ofNullable(beanFactoryLocator).ifPresent(GemfireBeanFactoryLocator::destroy);
		}
	}

	@Test
	public void unableToLocateBeanFactory() {
		assertThat(this.dataSerializer.locateBeanFactory().orElse(null)).isNull();
	}

	@Test
	public void constructsNewBeanConfigurerSupportWithBeanFactory() {

		BeanConfigurerSupport beanConfigurer = null;

		try {
			beanConfigurer = this.dataSerializer.newBeanConfigurer(this.mockBeanFactory);

			assertThat(beanConfigurer).isNotNull();
		}
		finally {
			Optional.ofNullable(beanConfigurer).ifPresent(BeanConfigurerSupport::destroy);
		}
	}
}
