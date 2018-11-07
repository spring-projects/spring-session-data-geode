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

package org.springframework.session.data.gemfire;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import edu.umd.cs.mtc.TestFramework;

import org.apache.geode.cache.client.ClientRegionShortcut;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.gemfire.config.annotation.CacheServerApplication;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * The ConcurrentSessionOperationsUsingClientCachingProxyRegionIntegrationTests class...
 *
 * @author John Blum
 * @since 1.0.0
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(
	classes = ConcurrentSessionOperationsUsingClientCachingProxyRegionIntegrationTests.GemFireClientConfiguration.class
)
public class ConcurrentSessionOperationsUsingClientCachingProxyRegionIntegrationTests
		extends AbstractConcurrentSessionOperationsIntegrationTests {

	@Test
	public void concurrentCachedSessionAccessIsCorrect() throws Throwable {
		TestFramework.runOnce(new ConcurrentCachedSessionAccessTestCase(this));
	}

	@SuppressWarnings("unused")
	public static class ConcurrentCachedSessionAccessTestCase extends AbstractConcurrentSessionOperationsTestCase {

		private final AtomicReference<String> sessionId = new AtomicReference<>(null);

		public ConcurrentCachedSessionAccessTestCase(
				ConcurrentSessionOperationsUsingClientCachingProxyRegionIntegrationTests testInstance) {

			super(testInstance);
		}

		public void thread1() {

			Thread.currentThread().setName("User Session One");

			assertTick(0);

			Session session = newSession();

			assertThat(session).isNotNull();
			assertThat(session.getId()).isNotEmpty();
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).isEmpty();

			save(session);

			this.sessionId.set(session.getId());

			waitForTick(2);
			assertTick(2);

			// modify the Session without saving
			session.setAttribute("attributeOne", "one");
			session.setAttribute("attributeTwo", "two");
		}

		public void thread2() {

			Thread.currentThread().setName("User Session Two");

			waitForTick(1);
			assertTick(1);

			Session session = findById(this.sessionId.get());

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(this.sessionId.get());
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).isEmpty();

			waitForTick(3);
			assertTick(3);

			assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
			assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("one");
			assertThat(session.<String>getAttribute("attributeTwo")).isEqualTo("two");
		}
	}

	@BeforeClass
	public static void startGemFireServer() throws IOException {
		startGemFireServer(GemFireServerConfiguration.class);
	}

	// Tests fail when copyOnRead is set to true.
	//@ClientCacheApplication(copyOnRead = true, logLevel = "error", subscriptionEnabled = true)
	@ClientCacheApplication(logLevel = "error", subscriptionEnabled = true)
	@EnableGemFireHttpSession(
		clientRegionShortcut = ClientRegionShortcut.CACHING_PROXY,
		poolName = "DEFAULT",
		sessionSerializerBeanName = GemFireHttpSessionConfiguration.SESSION_DATA_SERIALIZER_BEAN_NAME
	)
	static class GemFireClientConfiguration { }

	@CacheServerApplication(
		name = "ConcurrentSessionOperationsUsingClientCachingProxyRegionIntegrationTests",
		logLevel = "error"
	)
	@EnableGemFireHttpSession(
		sessionSerializerBeanName = GemFireHttpSessionConfiguration.SESSION_DATA_SERIALIZER_BEAN_NAME
	)
	static class GemFireServerConfiguration {

		public static void main(String[] args) {

			AnnotationConfigApplicationContext applicationContext =
				new AnnotationConfigApplicationContext(GemFireServerConfiguration.class);

			applicationContext.registerShutdownHook();
		}
	}
}
