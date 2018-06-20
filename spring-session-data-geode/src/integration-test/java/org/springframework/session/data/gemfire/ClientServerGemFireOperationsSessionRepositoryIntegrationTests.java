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

package org.springframework.session.data.gemfire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.gemfire.util.ArrayUtils.asArray;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.geode.cache.DataPolicy;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionAttributes;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientRegionShortcut;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.data.gemfire.config.annotation.CacheServerApplication;
import org.springframework.data.gemfire.config.annotation.CacheServerConfigurer;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.data.gemfire.config.annotation.ClientCacheConfigurer;
import org.springframework.data.gemfire.support.ConnectionEndpoint;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.SocketUtils;

/**
 * Integration tests testing the functionality of Apache Geode / Pivotal GemFire backed Spring Sessions
 * using the Pivotal GemFire client-server topology.
 *
 * @author John Blum
 * @since 1.1.0
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.apache.geode.cache.Cache
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.cache.client.ClientCache
 * @see org.apache.geode.cache.client.Pool
 * @see org.apache.geode.cache.server.CacheServer
 * @see org.springframework.context.ConfigurableApplicationContext
 * @see org.springframework.data.gemfire.config.annotation.CacheServerApplication
 * @see org.springframework.data.gemfire.config.annotation.ClientCacheApplication
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.springframework.session.events.SessionCreatedEvent
 * @see org.springframework.session.events.SessionDeletedEvent
 * @see org.springframework.session.events.SessionExpiredEvent
 * @see org.springframework.test.annotation.DirtiesContext
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @see org.springframework.test.context.web.WebAppConfiguration
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes =
	ClientServerGemFireOperationsSessionRepositoryIntegrationTests.TestGemFireClientConfiguration.class)
@DirtiesContext
@WebAppConfiguration
public class ClientServerGemFireOperationsSessionRepositoryIntegrationTests
		extends AbstractGemFireIntegrationTests {

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 1;

	private static final DateFormat TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

	private static File processWorkingDirectory;

	private static Process gemfireServer;

	private static final String TEST_SESSION_REGION_NAME = "TestClientServerSessions";

	@Autowired
	@SuppressWarnings("all")
	private SessionEventListener sessionEventListener;

	@BeforeClass
	public static void startGemFireServer() throws IOException {

		long t0 = System.currentTimeMillis();

		int port = SocketUtils.findAvailableTcpPort();

		System.err.printf("Starting a Pivotal GemFire Server running on host [%1$s] listening on port [%2$d]%n",
			TestGemFireServerConfiguration.SERVER_HOSTNAME, port);

		System.setProperty("spring.session.data.gemfire.port", String.valueOf(port));

		String processWorkingDirectoryPathname =
			String.format("gemfire-client-server-tests-%1$s", TIMESTAMP.format(new Date()));

		processWorkingDirectory = createDirectory(processWorkingDirectoryPathname);

		gemfireServer = run(TestGemFireServerConfiguration.class, processWorkingDirectory,
			String.format("-Dspring.session.data.gemfire.port=%1$d", port));

		assertThat(waitForCacheServerToStart(TestGemFireServerConfiguration.SERVER_HOSTNAME, port))
			.isTrue();

		System.err.printf("GemFire Server [startup time = %1$d ms]%n", System.currentTimeMillis() - t0);
	}

	@AfterClass
	public static void stopGemFireServer() {

		if (gemfireServer != null) {

			gemfireServer.destroy();

			System.err.printf("GemFire Server [exit code = %1$d]%n",
				waitForProcessToStop(gemfireServer, processWorkingDirectory));
		}

		if (Boolean.valueOf(System.getProperty("spring.session.data.gemfire.fork.clean", Boolean.TRUE.toString()))) {
			FileSystemUtils.deleteRecursively(processWorkingDirectory);
		}

		assertThat(waitForClientCacheToClose(DEFAULT_WAIT_DURATION)).isTrue();
	}

	@Before
	public void setup() {

		assertThat(GemFireUtils.isClient(gemfireCache)).isTrue();

		Region<Object, Session> springSessionGemFireRegion =
			gemfireCache.getRegion(TEST_SESSION_REGION_NAME);

		assertThat(springSessionGemFireRegion).isNotNull();

		RegionAttributes<Object, Session> springSessionGemFireRegionAttributes =
			springSessionGemFireRegion.getAttributes();

		assertThat(springSessionGemFireRegionAttributes).isNotNull();
		assertThat(springSessionGemFireRegionAttributes.getDataPolicy()).isEqualTo(DataPolicy.NORMAL);
	}

	@After
	public void tearDown() {
		this.sessionEventListener.getSessionEvent();
	}

	@Test
	public void createSessionFiresSessionCreatedEvent() {

		Instant beforeOrAtCreationTime = Instant.now();

		Session expectedSession = save(createSession());

		AbstractSessionEvent sessionEvent = this.sessionEventListener.waitForSessionEvent(500);

		assertThat(sessionEvent).isInstanceOf(SessionCreatedEvent.class);

		Session createdSession = sessionEvent.getSession();

		assertThat(createdSession.getId()).isEqualTo(expectedSession.getId());
		assertThat(createdSession.getCreationTime().compareTo(beforeOrAtCreationTime)).isGreaterThanOrEqualTo(0);
		assertThat(createdSession.getLastAccessedTime()).isEqualTo(createdSession.getCreationTime());
		assertThat(createdSession.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS));

		createdSession.setAttribute("attrOne", 1);

		assertThat(save(touch(createdSession)).<Integer>getAttribute("attrOne")).isEqualTo(1);

		sessionEvent = this.sessionEventListener.waitForSessionEvent(500);

		assertThat(sessionEvent).isNull();

		this.gemfireSessionRepository.deleteById(expectedSession.getId());
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

		sessionEvent = this.sessionEventListener
			.waitForSessionEvent(TimeUnit.SECONDS.toMillis(MAX_INACTIVE_INTERVAL_IN_SECONDS + 5));

		assertThat(sessionEvent)
			.describedAs("SessionEvent was type [%s]", ObjectUtils.nullSafeClassName(sessionEvent))
			.isInstanceOf(SessionExpiredEvent.class);

		assertThat(sessionEvent.getSessionId()).isEqualTo(expectedSession.getId());

		Session expiredSession = this.gemfireSessionRepository.findById(expectedSession.getId());

		assertThat(expiredSession).isNull();
	}

	@Test
	public void deleteExistingNonExpiredSessionFiresSessionDeletedEventAndReturnsNullOnGet() {

		Session expectedSession = save(touch(createSession()));

		AbstractSessionEvent sessionEvent = this.sessionEventListener.waitForSessionEvent(500);

		assertThat(sessionEvent).isInstanceOf(SessionCreatedEvent.class);
		assertThat(sessionEvent.<Session>getSession()).isEqualTo(expectedSession);

		this.gemfireSessionRepository.deleteById(expectedSession.getId());

		sessionEvent = this.sessionEventListener.waitForSessionEvent(500);

		assertThat(sessionEvent).isInstanceOf(SessionDeletedEvent.class);
		assertThat(sessionEvent.getSessionId()).isEqualTo(expectedSession.getId());

		Session deletedSession = this.gemfireSessionRepository.findById(expectedSession.getId());

		assertThat(deletedSession).isNull();
	}

	@ClientCacheApplication(logLevel = "warning", pingInterval = 5000, readTimeout = 2500, retryAttempts = 1,
		subscriptionEnabled = true)
	@EnableGemFireHttpSession(regionName = TEST_SESSION_REGION_NAME, poolName = "DEFAULT",
		clientRegionShortcut = ClientRegionShortcut.CACHING_PROXY,
			maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	@SuppressWarnings("unused")
	static class TestGemFireClientConfiguration {

		@Bean
		static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
			return new PropertySourcesPlaceholderConfigurer();
		}

		@Bean
		ClientCacheConfigurer clientCacheDefaultPoolPortConfigurer(
				@Value("${spring.session.data.gemfire.port:" + DEFAULT_GEMFIRE_SERVER_PORT + "}") int port) {

			return (beanName, clientCacheFactoryBean) ->
				clientCacheFactoryBean.setServers(asArray(new ConnectionEndpoint("localhost", port)));
		}

		@Bean
		public SessionEventListener sessionEventListener() {
			return new SessionEventListener();
		}

		// used for debugging purposes
		@SuppressWarnings("resource")
		public static void main(String[] args) {

			ConfigurableApplicationContext applicationContext = new AnnotationConfigApplicationContext(
					TestGemFireClientConfiguration.class);

			applicationContext.registerShutdownHook();

			ClientCache clientCache = applicationContext.getBean(ClientCache.class);

			for (InetSocketAddress server : clientCache.getCurrentServers()) {
				System.err.printf("GemFire Server [host: %1$s, port: %2$d]%n",
					server.getHostName(), server.getPort());
			}
		}
	}

	@CacheServerApplication(name = "ClientServerGemFireOperationsSessionRepositoryIntegrationTests", logLevel = "warning")
	@EnableGemFireHttpSession(regionName = TEST_SESSION_REGION_NAME,
		maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	@SuppressWarnings("unused")
	static class TestGemFireServerConfiguration {

		static final String SERVER_HOSTNAME = "localhost";

		@Bean
		static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
			return new PropertySourcesPlaceholderConfigurer();
		}

		@Bean
		CacheServerConfigurer cacheServerPortConfigurer(
				@Value("${spring.session.data.gemfire.port:" + DEFAULT_GEMFIRE_SERVER_PORT + "}") int port) {

			return (beanName, cacheServerFactoryBean) -> cacheServerFactoryBean.setPort(port);
		}

		@SuppressWarnings("resource")
		public static void main(String[] args) throws IOException {

			AnnotationConfigApplicationContext applicationContext =
				new AnnotationConfigApplicationContext(TestGemFireServerConfiguration.class);

			applicationContext.registerShutdownHook();

			writeProcessControlFile(WORKING_DIRECTORY);
		}
	}
}
