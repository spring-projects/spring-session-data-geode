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

package org.springframework.session.data.gemfire.serialization.data.support;

import static org.springframework.data.gemfire.support.GemfireBeanFactoryLocator.newBeanFactoryLocator;

import java.util.Optional;

import org.apache.geode.DataSerializer;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.wiring.BeanConfigurerSupport;
import org.springframework.session.data.gemfire.serialization.data.AbstractDataSerializableSessionSerializer;
import org.springframework.stereotype.Component;

/**
 * {@link WirableDataSerializer} is an abstract base class supporting auto-wiring of a non-managed,
 * GemFire/Geode {@link DataSerializer}, Spring {@link Component}.
 *
 * @author John Blum
 * @see org.apache.geode.DataSerializer
 * @see org.springframework.beans.factory.BeanFactory
 * @see org.springframework.beans.factory.wiring.BeanConfigurerSupport
 * @see org.springframework.session.data.gemfire.serialization.data.AbstractDataSerializableSessionSerializer
 * @since 2.0.0
 */
abstract class WirableDataSerializer<T> extends AbstractDataSerializableSessionSerializer<T> {

	protected final void autowire() {

		locateBeanFactory().map(this::newBeanConfigurer).ifPresent(beanConfigurer -> {
			beanConfigurer.configureBean(this);
			beanConfigurer.destroy();
		});
	}

	Optional<BeanFactory> locateBeanFactory() {

		try {
			return Optional.ofNullable(newBeanFactoryLocator().useBeanFactory());
		}
		catch (Exception ignore) {
			return Optional.empty();
		}
	}

	BeanConfigurerSupport newBeanConfigurer(BeanFactory beanFactory) {

		BeanConfigurerSupport beanConfigurer = new BeanConfigurerSupport();

		beanConfigurer.setBeanFactory(beanFactory);
		beanConfigurer.afterPropertiesSet();

		return beanConfigurer;
	}
}
