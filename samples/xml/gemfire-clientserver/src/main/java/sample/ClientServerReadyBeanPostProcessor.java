/*
 * Copyright 2014-2016 the original author or authors.
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

package sample;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.gemfire.client.ClientCacheFactoryBean;
import org.springframework.data.gemfire.tests.integration.ClientServerIntegrationTestsSupport;

@SuppressWarnings("unused")
public class ClientServerReadyBeanPostProcessor extends ClientServerIntegrationTestsSupport
		implements BeanPostProcessor {

	@Value("${spring.session.data.geode.cache.server.port:${spring.data.gemfire.cache.server.port:40404}}")
	private int port;

	@Value("${spring.session.data.geode.cache.server.host:${spring.data.gemfire.cache.server.host:localhost}}")
	private String host;

	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {

		if (bean instanceof ClientCacheFactoryBean) {
			waitForServerToStart(host, port);
		}

		return bean;
	}
}
