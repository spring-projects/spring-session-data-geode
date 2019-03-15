/*
 * Copyright 2018 the original author or authors.
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.Pool;
import org.apache.geode.cache.client.PoolManager;
import org.apache.geode.cache.client.internal.PoolImpl;
import org.apache.geode.management.membership.ClientMembership;
import org.apache.geode.management.membership.ClientMembershipEvent;
import org.apache.geode.management.membership.ClientMembershipListenerAdapter;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.data.gemfire.config.annotation.ClientCacheConfigurer;
import org.springframework.data.gemfire.config.xml.GemfireConstants;
import org.springframework.data.gemfire.support.ConnectionEndpoint;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.util.Assert;

/**
 * The IntegrationTestConfiguration class...
 *
 * @author John Blum
 * @since 1.0.0
 */
public class IntegrationTestConfiguration {

	static final long DEFAULT_TIMEOUT = TimeUnit.SECONDS.toMillis(60);

	static final CountDownLatch LATCH = new CountDownLatch(1);

	static final String GEMFIRE_DEFAULT_POOL_NAME = "DEFAULT";

	@Bean
	BeanPostProcessor clientServerReadyBeanPostProcessor(
		@Value("${spring.session.data.geode.cache.server.port:40404}") int port) { // <5>

		return new BeanPostProcessor() {

			private final AtomicBoolean checkGemFireServerIsRunning = new AtomicBoolean(true);
			private final AtomicReference<Pool> defaultPool = new AtomicReference<>(null);

			public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {

				if (shouldCheckWhetherGemFireServerIsRunning(bean, beanName)) {
					try {
						validateCacheClientNotified();
						validateCacheClientSubscriptionQueueConnectionEstablished();
					}
					catch (InterruptedException cause) {
						Thread.currentThread().interrupt();
					}
				}

				return bean;
			}

			private boolean shouldCheckWhetherGemFireServerIsRunning(Object bean, String beanName) {

				return (isGemFireRegion(bean, beanName)
					? checkGemFireServerIsRunning.compareAndSet(true, false)
					: whenGemFireCache(bean, beanName));
			}

			private boolean isGemFireRegion(Object bean, String beanName) {

				return (GemFireHttpSessionConfiguration.DEFAULT_SESSION_REGION_NAME.equals(beanName)
					|| bean instanceof Region);
			}

			private boolean whenGemFireCache(Object bean, String beanName) {

				if (bean instanceof ClientCache) {
					defaultPool.compareAndSet(null, ((ClientCache) bean).getDefaultPool());
				}

				return false;
			}

			private void validateCacheClientNotified() throws InterruptedException {

				boolean didNotTimeout = LATCH.await(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);

				Assert.state(didNotTimeout, String.format(
					"Apache Geode Cache Server failed to start on host [%s] and port [%d]", "localhost", port));
			}

			@SuppressWarnings("all")
			private void validateCacheClientSubscriptionQueueConnectionEstablished() throws InterruptedException {

				boolean cacheClientSubscriptionQueueConnectionEstablished = false;

				Pool pool = defaultIfNull(this.defaultPool.get(), GemfireConstants.DEFAULT_GEMFIRE_POOL_NAME,
					GEMFIRE_DEFAULT_POOL_NAME);

				if (pool instanceof PoolImpl) {

					long timeout = (System.currentTimeMillis() + DEFAULT_TIMEOUT);

					while (System.currentTimeMillis() < timeout && !((PoolImpl) pool).isPrimaryUpdaterAlive()) {
						synchronized (pool) {
							TimeUnit.MILLISECONDS.timedWait(pool, 500L);
						}

					}

					cacheClientSubscriptionQueueConnectionEstablished |= ((PoolImpl) pool).isPrimaryUpdaterAlive();
				}

				Assert.state(cacheClientSubscriptionQueueConnectionEstablished,
					String.format("Cache client subscription queue connection not established; Apache Geode Pool was [%s];"
							+ "  Apache Geode Pool configuration was [locators = %s, servers = %s]",
						pool, pool.getLocators(), pool.getServers()));
			}

			private Pool defaultIfNull(Pool pool, String... poolNames) {

				for (String poolName : poolNames) {
					pool = (pool != null ? pool : PoolManager.find(poolName));
				}

				return pool;
			}
		};
	}

	ConnectionEndpoint newConnectionEndpoint(String host, int port) {
		return new ConnectionEndpoint(host, port);
	}

	@Bean
	ClientCacheConfigurer registerClientMembershipListener() {

		return (beanName, bean) -> {

			ClientMembership.registerClientMembershipListener(new ClientMembershipListenerAdapter() {

				@Override
				public void memberJoined(ClientMembershipEvent event) {
					LATCH.countDown();
				}
			});
		};
	}
}
