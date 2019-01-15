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

package org.springframework.session.data.gemfire;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.geode.cache.DataPolicy;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionAttributes;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.data.gemfire.client.PoolFactoryBean;
import org.springframework.data.gemfire.config.annotation.CacheServerApplication;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.data.gemfire.support.ConnectionEndpoint;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * Integration test to test the functionality of a Pivotal GemFire cache client in a Spring Session application
 * using a specifically named Pivotal GemFire {@link org.apache.geode.cache.client.Pool} configured with
 * the 'poolName' attribute in the Spring Session Data Pivotal GemFire {@link EnableGemFireHttpSession} annotation.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.springframework.test.annotation.DirtiesContext
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringJUnit4ClassRunner
 * @see org.springframework.test.context.web.WebAppConfiguration
 * @see org.apache.geode.cache.Cache
 * @see org.apache.geode.cache.client.ClientCache
 * @see org.apache.geode.cache.client.Pool
 * @see org.apache.geode.cache.server.CacheServer
 * @since 1.3.0
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(
	classes = MultiPoolClientServerGemFireOperationsSessionRepositoryIntegrationTests.SpringSessionDataGemFireClientConfiguration.class
)
@DirtiesContext
@WebAppConfiguration
public class MultiPoolClientServerGemFireOperationsSessionRepositoryIntegrationTests
		extends AbstractGemFireIntegrationTests {

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 1;

	private static final String SPRING_SESSION_GEMFIRE_REGION_NAME = "TestMultiPoolClientServerSessions";

	@Autowired @SuppressWarnings("all")
	private SessionEventListener sessionEventListener;

	@BeforeClass
	public static void startGemFireServer() throws IOException {
		startGemFireServer(SpringSessionDataGemFireServerConfiguration.class);
	}

	@Before
	public void setup() {

		assertThat(GemFireUtils.isClient(gemfireCache)).isTrue();

		Region<Object, Session> springSessionGemFireRegion =
			gemfireCache.getRegion(SPRING_SESSION_GEMFIRE_REGION_NAME);

		assertThat(springSessionGemFireRegion).isNotNull();

		RegionAttributes<Object, Session> springSessionGemFireRegionAttributes =
			springSessionGemFireRegion.getAttributes();

		assertThat(springSessionGemFireRegionAttributes).isNotNull();
		assertThat(springSessionGemFireRegionAttributes.getDataPolicy()).isEqualTo(DataPolicy.EMPTY);
	}

	protected static ConnectionEndpoint newConnectionEndpoint(String host, int port) {
		return new ConnectionEndpoint(host, port);
	}

	@Test
	public void getExistingNonExpiredSessionBeforeAndAfterExpiration() {

		Session expectedSession = save(touch(createSession()));

		AbstractSessionEvent sessionEvent = this.sessionEventListener.waitForSessionEvent(500);

		assertThat(sessionEvent).isInstanceOf(SessionCreatedEvent.class);
		assertThat(sessionEvent.<Session>getSession()).isEqualTo(expectedSession);
		assertThat(this.sessionEventListener.<SessionCreatedEvent>getSessionEvent()).isNull();

		Session savedSession = this.gemfireSessionRepository.findById(expectedSession.getId());

		assertThat(savedSession).isEqualTo(expectedSession);

		this.sessionEventListener.getSessionEvent();

		sessionEvent = this.sessionEventListener.waitForSessionEvent(
			TimeUnit.SECONDS.toMillis(MAX_INACTIVE_INTERVAL_IN_SECONDS + 1));

		assertThat(sessionEvent).isInstanceOf(SessionExpiredEvent.class);
		assertThat(sessionEvent.getSessionId()).isEqualTo(expectedSession.getId());

		Session expiredSession = this.gemfireSessionRepository.findById(expectedSession.getId());

		assertThat(expiredSession).isNull();
	}

	@ClientCacheApplication(logLevel = "error")
	@EnableGemFireHttpSession(
		regionName = SPRING_SESSION_GEMFIRE_REGION_NAME,
		poolName = "serverPool",
		maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS
	)
	@SuppressWarnings("unused")
	static class SpringSessionDataGemFireClientConfiguration {

		@Bean
		PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
			return new PropertySourcesPlaceholderConfigurer();
		}

		@Bean
		PoolFactoryBean gemfirePool() {

			PoolFactoryBean poolFactory = new PoolFactoryBean();

			poolFactory.setFreeConnectionTimeout(5000); // 5 seconds
			poolFactory.setKeepAlive(false);
			poolFactory.setMinConnections(0);
			poolFactory.setReadTimeout(500);

			// deliberately set to a non-existing Pivotal GemFire (Cache) Server
			poolFactory.addServers(newConnectionEndpoint("localhost", 53135));

			return poolFactory;
		}

		@Bean
		PoolFactoryBean serverPool(@Value("${" + GEMFIRE_CACHE_SERVER_PORT_PROPERTY + "}") int port) {

			PoolFactoryBean poolFactory = new PoolFactoryBean();

			poolFactory.setFreeConnectionTimeout(5000); // 5 seconds
			poolFactory.setKeepAlive(false);
			poolFactory.setMaxConnections(50);
			poolFactory.setMinConnections(1);
			poolFactory.setPingInterval(TimeUnit.SECONDS.toMillis(5));
			poolFactory.setReadTimeout(2000); // 2 seconds
			poolFactory.setRetryAttempts(1);
			poolFactory.setSubscriptionEnabled(true);
			poolFactory.setThreadLocalConnections(false);
			poolFactory.addServers(newConnectionEndpoint("localhost", port));

			return poolFactory;
		}

		@Bean
		public AbstractGemFireIntegrationTests.SessionEventListener sessionEventListener() {
			return new AbstractGemFireIntegrationTests.SessionEventListener();
		}
	}

	@CacheServerApplication(
		name = "MultiPoolClientServerGemFireOperationsSessionRepositoryIntegrationTests",
		maxConnections = 50,
		logLevel = "error"
	)
	@EnableGemFireHttpSession(
		regionName = SPRING_SESSION_GEMFIRE_REGION_NAME,
		maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS
	)
	static class SpringSessionDataGemFireServerConfiguration {

		@SuppressWarnings("resource")
		public static void main(final String[] args) throws IOException {

			AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				SpringSessionDataGemFireServerConfiguration.class);

			context.registerShutdownHook();
		}
	}
}
