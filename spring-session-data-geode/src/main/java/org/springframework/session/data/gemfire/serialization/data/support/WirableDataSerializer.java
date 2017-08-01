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

import static org.springframework.data.gemfire.support.GemfireBeanFactoryLocator.newBeanFactoryLocator;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.wiring.BeanConfigurerSupport;
import org.springframework.session.data.gemfire.serialization.data.AbstractDataSerializableSessionSerializer;

/**
 * The WirableDataSerializer class...
 *
 * @author John Blum
 * @since 2.0.0
 */
public abstract class WirableDataSerializer<T> extends AbstractDataSerializableSessionSerializer<T> {

	protected final void autowire() {

		BeanConfigurerSupport beanConfigurer = newBeanConfigurer(locateBeanFactory());

		beanConfigurer.configureBean(this);
		beanConfigurer.destroy();
	}

	private BeanFactory locateBeanFactory() {
		return newBeanFactoryLocator().useBeanFactory();
	}

	private BeanConfigurerSupport newBeanConfigurer(BeanFactory beanFactory) {

		BeanConfigurerSupport beanConfigurer = new BeanConfigurerSupport();

		beanConfigurer.setBeanFactory(beanFactory);
		beanConfigurer.afterPropertiesSet();

		return beanConfigurer;
	}
}
