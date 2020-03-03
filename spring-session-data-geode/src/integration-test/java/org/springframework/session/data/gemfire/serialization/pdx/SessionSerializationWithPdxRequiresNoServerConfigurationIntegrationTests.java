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
 * -classpath /Users/jblum/.gradle/caches/modules-2/files-2.1/antlr/antlr/2.7.7/83cd2cd674a217ade95a4bb83a8a14f351f48bd0/antlr-2.7.7.jar
 * 	:/Users/jblum/.gradle/caches/modules-2/files-2.1/commons-collections/commons-collections/3.2.2/8ad72fe39fa8c91eaaf12aadb21e0c3661fe26d5/commons-collections-3.2.2.jar
 * 	:/Users/jblum/.gradle/caches/modules-2/files-2.1/commons-io/commons-io/2.5/2852e6e05fbb95076fc091f6d1780f1f8fe35e0f/commons-io-2.5.jar
 * 	:/Users/jblum/.gradle/caches/modules-2/files-2.1/commons-lang/commons-lang/2.6/ce1edb914c94ebc388f086c6827e8bdeec71ac2/commons-lang-2.6.jar
 * 	:/Users/jblum/.gradle/caches/modules-2/files-2.1/io.github.lukehutch/fast-classpath-scanner/2.0.11/ae34a7a5e6de8ad1f86e12f6f7ae1869fcfe9987/fast-classpath-scanner-2.0.11.jar
 * 	:/Users/jblum/.gradle/caches/modules-2/files-2.1/it.unimi.dsi/fastutil/7.1.0/9835253257524c1be7ab50c057aa2d418fb72082/fastutil-7.1.0.jar
 * 	:/Users/jblum/.gradle/caches/modules-2/files-2.1/javax.resource/javax.resource-api/1.7/ae40e0864eb1e92c48bf82a2a3399cbbf523fb79/javax.resource-api-1.7.jar
 * 	:/Users/jblum/.gradle/caches/modules-2/files-2.1/javax.transaction/javax.transaction-api/1.2/d81aff979d603edd90dcd8db2abc1f4ce6479e3e/javax.transaction-api-1.2.jar
 * 	:/Users/jblum/.gradle/caches/modules-2/files-2.1/net.java.dev.jna/jna/4.5.0/55b548d3195efc5280bf1c3f17b49659c54dee40/jna-4.5.0.jar
 * 	:/Users/jblum/.gradle/caches/modules-2/files-2.1/org.apache.logging.log4j/log4j-api/2.9.1/7a2999229464e7a324aa503c0a52ec0f05efe7bd/log4j-api-2.9.1.jar
 * 	:/Users/jblum/.gradle/caches/modules-2/files-2.1/org.apache.logging.log4j/log4j-core/2.9.1/c041978c686866ee8534f538c6220238db3bb6be/log4j-core-2.9.1.jar
 * 	:/Users/jblum/.gradle/caches/modules-2/files-2.1/org.apache.geode/geode-common/1.2.1/9db253081d33f424f6e3ce0cde4b306e23e3420b/geode-common-1.2.1.jar
 * 	:/Users/jblum/.gradle/caches/modules-2/files-2.1/org.apache.geode/geode-core/1.2.1/fe853317e33dd2a1c291f29cee3c4be549f75a69/geode-core-1.2.1.jar
 * 	:/Users/jblum/.gradle/caches/modules-2/files-2.1/org.apache.geode/geode-json/1.2.1/bdb4c262e4ce6bb3b22e0f511cfb133a65fa0c04/geode-json-1.2.1.jar
 * 	:/Users/jblum/.gradle/caches/modules-2/files-2.1/com.fasterxml.jackson.core/jackson-core/2.9.1/60077fe98b11e4e7cf8af9b20609326a166d6ac4/jackson-core-2.9.1.jar
 * 	:/Users/jblum/.gradle/caches/modules-2/files-2.1/org.jgroups/jgroups/3.6.10.Final/fc0ff5a8a9de27ab62939956f705c2909bf86bc2/jgroups-3.6.10.Final.jar
 * 	:/Users/jblum/.gradle/caches/modules-2/files-2.1/org.apache.lucene/lucene-core/6.4.1/2a18924b9e0ed86b318902cb475a0b9ca4d7be5b/lucene-core-6.4.1.jar
 * 	:/Users/jblum/.gradle/caches/modules-2/files-2.1/org.apache.shiro/shiro-core/1.4.0/6d05bd17e057fc12d278bb367c27f9cb0f3dc197/shiro-core-1.4.0.jar
 * 	:/Users/jblum/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/1.7.25/da76ca59f6a57ee3102f8f9bd9cee742973efa8a/slf4j-api-1.7.25.jar
 * 	:spring-session-data-geode/build/classes/integrationTest
 * 	org.springframework.session.data.gemfire.server.GemFireServer
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.apache.geode.cache.server.CacheServer
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.support.PropertySourcesPlaceholderConfigurer
 * @see org.springframework.data.gemfire.config.annotation.ClientCacheApplication
 * @see org.springframework.data.gemfire.config.annotation.ClientCacheConfigurer
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

		System.err.printf("Starting a Pivotal GemFire Server on host [localhost], listening on port [%d]%n", port);

		System.setProperty("spring.session.data.gemfire.cache.server.port", String.valueOf(port));

		String classpath = buildClassPathContainingJarFiles("javax.transaction-api", "antlr",
			"commons-lang", "commons-io", "fastutil", "log4j-api", "log4j-to-slf4j", "logback-classic", "logback-core",
			"geode-common", "geode-core", "geode-logging", "geode-management", "geode-serialization", "jgroups",
			"micrometer-core", "shiro-core", "slf4j-api");

		String processWorkingDirectoryPathname =
			String.format("gemfire-server-pdx-serialization-tests-%1$s", TIMESTAMP.format(new Date()));

		processWorkingDirectory = createDirectory(processWorkingDirectoryPathname);

		gemfireServer = run(classpath, GemFireServer.class,
			processWorkingDirectory, String.format("-Dspring.session.data.gemfire.cache.server.port=%d", port),
			String.format("-Dgemfire.log-level=%s", GEMFIRE_LOG_LEVEL));

 		assertThat(waitForServerToStart("localhost", port)).isTrue();

		System.err.printf("GemFire Server [startup time = %d ms]%n", System.currentTimeMillis() - t0);
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
