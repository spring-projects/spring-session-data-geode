/*
 * Copyright 2019 the original author or authors.
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

package sample.client;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.gemfire.tests.integration.ClientServerIntegrationTestsSupport;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;

@SuppressWarnings("unused")
public class ClientServerReadyBeanPostProcessor extends ClientServerIntegrationTestsSupport
		implements BeanPostProcessor {

	private final AtomicBoolean verifyGemFireServerIsRunning = new AtomicBoolean(true);

	@Value("${spring.data.gemfire.cache.server.port:40404}")
	private int port;

	@Value("${spring.session.data.geode.cache.server.host:localhost}")
	private String host;

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {

		if (isGemFireServerRunningVerificationEnabled(bean, beanName)) {
			waitForServerToStart(host, port);
		}

		return bean;
	}

	private boolean isGemFireServerRunningVerificationEnabled(Object bean, String beanName) {

		return isSessionGemFireRegion(bean, beanName)
			&& this.verifyGemFireServerIsRunning.compareAndSet(true, false);
	}

	private boolean isSessionGemFireRegion(Object bean, String beanName) {
		return GemFireHttpSessionConfiguration.DEFAULT_SESSION_REGION_NAME.equals(beanName);
	}
}
