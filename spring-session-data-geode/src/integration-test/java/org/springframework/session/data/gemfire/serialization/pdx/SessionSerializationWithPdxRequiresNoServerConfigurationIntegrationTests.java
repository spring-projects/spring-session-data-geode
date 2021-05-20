/*
 * Copyright 2020 the original author or authors.
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
package org.springframework.session.data.gemfire.serialization.pdx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.geode.cache.server.CacheServer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.data.gemfire.config.annotation.ClientCacheConfigurer;
import org.springframework.data.gemfire.support.ConnectionEndpoint;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests;
import org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;
import org.springframework.session.data.gemfire.GemFireOperationsSessionRepository;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.session.data.gemfire.server.GemFireServer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.SocketUtils;

/**
 * Integration tests asserting that a GemFire/Geode Server does not require any Spring Session Data GemFire/Geode
 * dependencies or any transitive dependencies when PDX serialization is used.
 *
 * /Library/Java/JavaVirtualMachines/jdk1.8.0_65.jdk/Contents/Home/jre/bin/java -server -ea
 * -Dgemfire.log-level=FINEST -Dgemfire.Query.VERBOSE=false -Dspring.session.data.gemfire.cache.server.port=34095
 * -classpath
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/javax.transaction/javax.transaction-api/1.3/e006adf5cf3cca2181d16bd640ecb80148ec0fce/javax.transaction-api-1.3.jar
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/antlr/antlr/2.7.7/83cd2cd674a217ade95a4bb83a8a14f351f48bd0/antlr-2.7.7.jar
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/org.apache.commons/commons-lang3/3.10/e155460aaf5b464062a09c3923f089ce99128a17/commons-lang3-3.10.jar
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/commons-io/commons-io/2.6/815893df5f31da2ece4040fe0a12fd44b577afaf/commons-io-2.6.jar
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/commons-validator/commons-validator/1.6/e989d1e87cdd60575df0765ed5bac65c905d7908/commons-validator-1.6.jar
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/it.unimi.dsi/fastutil/8.3.0/742307990505e3a149c9c60825ffc1db5ceef02e/fastutil-8.3.0.jar
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/org.apache.logging.log4j/log4j-api/2.13.1/cc670f92dc77bbf4540904c3fa211b997cba00d8/log4j-api-2.13.1.jar
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/org.apache.logging.log4j/log4j-to-slf4j/2.13.1/acb14cc60bb8f45a8ccf17cd7e94961236b3306e/log4j-to-slf4j-2.13.1.jar
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/ch.qos.logback/logback-classic/1.2.3/7c4f3c474fb2c041d8028740440937705ebb473a/logback-classic-1.2.3.jar
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/ch.qos.logback/logback-core/1.2.3/864344400c3d4d92dfeb0a305dc87d953677c03c/logback-core-1.2.3.jar
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/org.apache.geode/geode-common/1.12.0/d2393d18b5f610307cb6e63c2776220fca6322ae/geode-common-1.12.0.jar
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/org.apache.geode/geode-core/1.12.0/bfec9700eb2bee8bfa88787adc2d61605fad41c7/geode-core-1.12.0.jar
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/org.apache.geode/geode-logging/1.12.0/2498f81fb2cb99a1602a13ef6741125a4b50bf60/geode-logging-1.12.0.jar
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/org.apache.geode/geode-management/1.12.0/26351f8030c73019b4d00db34ac9f372bffd1e4d/geode-management-1.12.0.jar
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/org.apache.geode/geode-membership/1.12.0/d706f7283cc8bb0b4aa4dab9ba84e1c3f81594b9/geode-membership-1.12.0.jar
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/org.apache.geode/geode-serialization/1.12.0/26a0024437ac13bade2ce1c5b6e5857d724c02bf/geode-serialization-1.12.0.jar
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/org.apache.geode/geode-tcp-server/1.12.0/d9b6055de9b8a9579958582b15536b38e91f4c84/geode-tcp-server-1.12.0.jar
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/org.jgroups/jgroups/3.6.14.Final/ee11e0645462b6937625f56f42bf5e853673168/jgroups-3.6.14.Final.jar
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/io.micrometer/micrometer-core/1.3.7/f5e87e953ffd8082c80c6415c5cdc4db5e912533/micrometer-core-1.3.7.jar
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/com.healthmarketscience.rmiio/rmiio/2.1.2/1d35887bc716bff6e51d7530bb5abf14fc211e70/rmiio-2.1.2.jar
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/org.apache.shiro/shiro-core/1.4.1/4825f3cd3156d197c17edca51061675e4a72260d/shiro-core-1.4.1.jar
 *  /Users/jblum/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/1.7.30/b5a4b6d16ab13e34a88fae84c35cd5d68cac922c/slf4j-api-1.7.30.jar
 *  /Users/jblum/pivdev/spring-session-data-geode/spring-session-data-geode/build/classes/java/integrationTest
 *
 * @author John Blum
 * @see org.junit.Test
 * @see java.io.File
 * @see java.time.Instant
 * @see org.apache.geode.cache.server.CacheServer
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.support.PropertySourcesPlaceholderConfigurer
 * @see org.springframework.data.gemfire.config.annotation.ClientCacheApplication
 * @see org.springframework.data.gemfire.config.annotation.ClientCacheConfigurer
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests
 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @see org.springframework.session.data.gemfire.server.GemFireServer
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @since 2.0.0
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@SuppressWarnings("unused")
public class SessionSerializationWithPdxRequiresNoServerConfigurationIntegrationTests
		extends AbstractGemFireIntegrationTests {

	private static final DateFormat TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

	private static File processWorkingDirectory;

	private static Process gemfireServer;

	private static final String GEMFIRE_LOG_LEVEL = "error";

	@Autowired
	private GemFireOperationsSessionRepository sessionRepository;

	@BeforeClass
	public static void startGemFireServer() throws IOException {

		long t0 = System.currentTimeMillis();

		int port = SocketUtils.findAvailableTcpPort();

		//System.err.printf("Starting a Pivotal GemFire Server on host [localhost], listening on port [%d]%n", port);

		System.setProperty("spring.session.data.gemfire.cache.server.port", String.valueOf(port));

		String classpath = buildClassPathContainingJarFiles("javax.transaction-api", "antlr",
			"commons-lang", "commons-io", "commons-validator", "fastutil", "log4j-api", "log4j-to-slf4j",
			"geode-common", "geode-core", "geode-logging", "geode-management", "geode-membership", "geode-serialization",
			"geode-tcp-server", "jgroups", "micrometer-core", "rmiio", "shiro-core", "slf4j-api");

		String processWorkingDirectoryPathname =
			String.format("gemfire-server-pdx-serialization-tests-%1$s", TIMESTAMP.format(new Date()));

		processWorkingDirectory = createDirectory(processWorkingDirectoryPathname);

		gemfireServer = run(classpath, GemFireServer.class,
			processWorkingDirectory, String.format("-Dspring.session.data.gemfire.cache.server.port=%d", port),
				String.format("-Dgemfire.log-level=%s", GEMFIRE_LOG_LEVEL));

 		assertThat(waitForServerToStart("localhost", port)).isTrue();

		//System.err.printf("GemFire Server [startup time = %d ms]%n", System.currentTimeMillis() - t0);
	}

	@AfterClass
	public static void stopGemFireServer() {

		Optional.ofNullable(gemfireServer).ifPresent(server -> {

			server.destroy();

			System.err.printf("GemFire Server [exit code = %d]%n",
				waitForProcessToStop(server, processWorkingDirectory));
		});

		boolean forkClean = !System.getProperties().containsKey("spring.session.data.gemfire.fork.clean")
			|| Boolean.getBoolean("spring.session.data.gemfire.fork.clean");

		if (forkClean) {
			FileSystemUtils.deleteRecursively(processWorkingDirectory);
		}
	}

	@Test
	@SuppressWarnings("rawtypes")
	public void sessionOperationsIsSuccessful() {

		Session session = save(createSession("jonDoe"));

		assertThat(session).isInstanceOf(GemFireSession.class);
		assertThat(session.getId()).isNotNull();
		assertThat(session.getCreationTime()).isBeforeOrEqualTo(Instant.now());
		assertThat(session.isExpired()).isFalse();
		assertThat(((GemFireSession) session).getPrincipalName()).isEqualTo("jonDoe");

		Session sessionById = get(session.getId());

		assertThat(sessionById).isEqualTo(session);
		assertThat(sessionById).isNotSameAs(session);
		assertThat(sessionById.isExpired()).isFalse();

		Map<String, Session> sessionsByPrincipalName = this.sessionRepository.findByPrincipalName("jonDoe");

		assertThat(sessionsByPrincipalName).hasSize(1);

		Session sessionByPrincipalName = sessionsByPrincipalName.values().iterator().next();

		assertThat(sessionByPrincipalName).isInstanceOf(GemFireSession.class);
		assertThat(sessionByPrincipalName).isEqualTo(session);
		assertThat(sessionByPrincipalName).isNotSameAs(session);
		assertThat(sessionByPrincipalName.isExpired()).isFalse();
		assertThat(((GemFireSession) sessionByPrincipalName).getPrincipalName()).isEqualTo("jonDoe");
	}

	@Test
	@SuppressWarnings("rawtypes")
	public void operationsOnSessionContainingApplicationDomainModelObjectIsSuccessful() {

		UsernamePasswordAuthenticationToken jxblumToken =
			new UsernamePasswordAuthenticationToken("jxblum", "p@55w0rd");

		Session session = createSession("janeDoe");

		assertThat(session).isInstanceOf(GemFireSession.class);

		session.setAttribute("userToken", jxblumToken);

		assertThat(session.getId()).isNotNull();
		assertThat(session.getCreationTime()).isBeforeOrEqualTo(Instant.now());
		assertThat(session.isExpired()).isFalse();
		assertThat(session.<UsernamePasswordAuthenticationToken>getAttribute("userToken"))
			.isEqualTo(jxblumToken);

		Session savedSession = save(session);

		assertThat(savedSession).isEqualTo(session);
		assertThat(savedSession).isInstanceOf(GemFireSession.class);

		// NOTE: You must update and save the Session again, after it has already been saved to cause Apache Geode to
		// update the 'principalNameIndex' OQL Index on an Index Maintenance Operation!!!
		((GemFireSession) savedSession).setPrincipalName("pieDoe");

		savedSession = save(touch(savedSession));

		assertThat(savedSession).isEqualTo(session);

		Session loadedSession = get(savedSession.getId());

		assertThat(loadedSession).isEqualTo(savedSession);
		assertThat(loadedSession).isNotSameAs(savedSession);
		assertThat(loadedSession.getCreationTime()).isEqualTo(savedSession.getCreationTime());
		assertThat(loadedSession.getLastAccessedTime()).isAfterOrEqualTo(savedSession.getLastAccessedTime());
		assertThat(loadedSession.isExpired()).isFalse();
		assertThat(session.<UsernamePasswordAuthenticationToken>getAttribute("userToken"))
			.isEqualTo(jxblumToken);
	}

	@ClientCacheApplication(logLevel = GEMFIRE_LOG_LEVEL, subscriptionEnabled = true)
	@EnableGemFireHttpSession(poolName = "DEFAULT")
	static class GemFireClientConfiguration {

		@Bean
		static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
			return new PropertySourcesPlaceholderConfigurer();
		}

		@Bean
		ClientCacheConfigurer clientCachePoolPortConfigurer(@Value("${spring.session.data.gemfire.cache.server.port:"
				+ CacheServer.DEFAULT_PORT + "}") int cacheServerPort) {

			return (beanName, clientCacheFactoryBean) -> clientCacheFactoryBean.setServers(Collections.singletonList(
				new ConnectionEndpoint("localhost", cacheServerPort)));
		}
	}
}
