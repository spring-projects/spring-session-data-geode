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

package sample.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.data.gemfire.config.annotation.ClientCacheConfigurer;
import org.springframework.data.gemfire.tests.integration.ClientServerIntegrationTestsSupport;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;

// tag::class[]
@ClientCacheApplication(name = "SpringSessionDataGeodeJavaConfigSampleClient", logLevel = "error",
	readTimeout = 15000, retryAttempts = 1, subscriptionEnabled = true) // <1>
@EnableGemFireHttpSession(poolName = "DEFAULT") // <2>
public class ClientConfig extends ClientServerIntegrationTestsSupport {

	@Bean
	ClientCacheConfigurer gemfireServerReadyConfigurer( // <3>
			@Value("${spring.data.gemfire.cache.server.port:40404}") int cacheServerPort) {

		return (beanName, clientCacheFactoryBean) -> waitForServerToStart("localhost", cacheServerPort);
	}
}
// end::class[]
