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

import java.util.Collections;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.data.gemfire.config.annotation.ClientCacheConfigurer;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;

// tag::class[]
@ClientCacheApplication(name = "SpringSessionDataGeodeClientJavaConfigSample", logLevel = "error",
	pingInterval = 5000L, readTimeout = 15000, retryAttempts = 1, subscriptionEnabled = true) // <1>
@EnableGemFireHttpSession(maxInactiveIntervalInSeconds = 30, poolName = "DEFAULT") // <2>
public class ClientConfig extends IntegrationTestConfig {

	// Required to resolve property placeholders in Spring @Value annotations.
	@Bean
	static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}

	@Bean
	ClientCacheConfigurer clientCacheServerPortConfigurer(
			@Value("${spring.session.data.geode.cache.server.port:40404}") int port) { // <3>

		return (beanName, clientCacheFactoryBean) ->
			clientCacheFactoryBean.setServers(Collections.singletonList(
				newConnectionEndpoint("localhost", port)));
	}
}
// end::class[]
