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

package org.springframework.session.data.gemfire.config.annotation.web.http.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.GemFireCache;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientRegionShortcut;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.session.Session;

/**
 * Unit tests for {@link SessionCacheTypeAwareRegionFactoryBean}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.apache.geode.cache.Cache
 * @see org.apache.geode.cache.GemFireCache
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.cache.RegionShortcut
 * @see org.apache.geode.cache.client.ClientCache
 * @see org.apache.geode.cache.client.ClientRegionShortcut
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.support.SessionCacheTypeAwareRegionFactoryBean
 * @since 1.1.0
 */
@RunWith(MockitoJUnitRunner.class)
public class SessionCacheTypeAwareRegionFactoryBeanTests {

	@Mock
	Cache mockCache;

	@Mock
	ClientCache mockClientCache;

	@Mock
	Region<Object, Session> mockClientRegion;

	@Mock
	Region<Object, Session> mockServerRegion;

	private SessionCacheTypeAwareRegionFactoryBean<Object, Session> regionFactoryBean;

	@Before
	public void setup() {
		this.regionFactoryBean = new SessionCacheTypeAwareRegionFactoryBean<>();
	}

	private void testAfterPropertiesSetCreatesCorrectRegionForGemFireCacheType(GemFireCache expectedCache,
			Region<Object, Session> expectedRegion) throws Exception {

		this.regionFactoryBean = new SessionCacheTypeAwareRegionFactoryBean<Object, Session>() {

			@Override
			protected Region<Object, Session> newClientRegion(GemFireCache gemfireCache, String name) throws Exception {
				assertThat(gemfireCache).isSameAs(expectedCache);
				return SessionCacheTypeAwareRegionFactoryBeanTests.this.mockClientRegion;
			}

			@Override
			protected Region<Object, Session> newServerRegion(GemFireCache gemfireCache, String name) throws Exception {
				assertThat(gemfireCache).isSameAs(expectedCache);
				return SessionCacheTypeAwareRegionFactoryBeanTests.this.mockServerRegion;
			}
		};

		this.regionFactoryBean.setCache(expectedCache);
		this.regionFactoryBean.afterPropertiesSet();

		assertThat(this.regionFactoryBean.getCache()).isSameAs(expectedCache);
		assertThat(this.regionFactoryBean.getObject()).isEqualTo(expectedRegion);
		assertThat(this.regionFactoryBean.getObjectType()).isEqualTo(expectedRegion.getClass());
	}

	@Test
	public void afterPropertiesSetCreatesClientRegionForClientCache() throws Exception {
		testAfterPropertiesSetCreatesCorrectRegionForGemFireCacheType(this.mockClientCache, this.mockClientRegion);
	}

	@Test
	public void afterPropertiesSetCreatesServerRegionForPeerCache() throws Exception {
		testAfterPropertiesSetCreatesCorrectRegionForGemFireCacheType(this.mockCache, this.mockServerRegion);
	}

	@Test
	public void getObjectTypeBeforeInitializationIsRegionClass() {
		assertThat(this.regionFactoryBean.getObjectType()).isEqualTo(Region.class);
	}

	@Test
	public void isSingletonIsTrue() {
		assertThat(this.regionFactoryBean.isSingleton()).isTrue();
	}

	@Test
	public void setAndGetBeanClassLoader() {

		ClassLoader mockClassLoader = mock(ClassLoader.class);

		assertThat(this.regionFactoryBean.getBeanClassLoader()).isNull();

		this.regionFactoryBean.setBeanClassLoader(Thread.currentThread().getContextClassLoader());

		assertThat(this.regionFactoryBean.getBeanClassLoader())
			.isEqualTo(Thread.currentThread().getContextClassLoader());

		this.regionFactoryBean.setBeanClassLoader(mockClassLoader);

		assertThat(this.regionFactoryBean.getBeanClassLoader()).isEqualTo(mockClassLoader);

		this.regionFactoryBean.setBeanClassLoader(ClassLoader.getSystemClassLoader());

		assertThat(this.regionFactoryBean.getBeanClassLoader()).isEqualTo(ClassLoader.getSystemClassLoader());
	}

	@Test
	public void setAndGetBeanFactory() {

		BeanFactory mockBeanFactory = mock(BeanFactory.class);

		assertThat(this.regionFactoryBean.getBeanFactory()).isNull();

		this.regionFactoryBean.setBeanFactory(mockBeanFactory);

		assertThat(this.regionFactoryBean.getBeanFactory()).isEqualTo(mockBeanFactory);
	}

	@Test
	public void setAndGetClientRegionShortcut() {

		assertThat(this.regionFactoryBean.getClientRegionShortcut())
			.isEqualTo(SessionCacheTypeAwareRegionFactoryBean.DEFAULT_CLIENT_REGION_SHORTCUT);

		this.regionFactoryBean.setClientRegionShortcut(ClientRegionShortcut.LOCAL_PERSISTENT);

		assertThat(this.regionFactoryBean.getClientRegionShortcut()).isEqualTo(ClientRegionShortcut.LOCAL_PERSISTENT);

		this.regionFactoryBean.setClientRegionShortcut(null);

		assertThat(this.regionFactoryBean.getClientRegionShortcut())
			.isEqualTo(SessionCacheTypeAwareRegionFactoryBean.DEFAULT_CLIENT_REGION_SHORTCUT);

		this.regionFactoryBean.setClientRegionShortcut(ClientRegionShortcut.CACHING_PROXY);

		assertThat(this.regionFactoryBean.getClientRegionShortcut()).isEqualTo(ClientRegionShortcut.CACHING_PROXY);
	}

	@Test
	public void setAndGetGemFireCache() {

		Cache mockCache = mock(Cache.class);
		ClientCache mockClientCache = mock(ClientCache.class);

		this.regionFactoryBean.setCache(mockCache);

		assertThat(this.regionFactoryBean.getCache()).isEqualTo(mockCache);

		this.regionFactoryBean.setCache(mockClientCache);

		assertThat(this.regionFactoryBean.getCache()).isEqualTo(mockClientCache);
	}

	@Test
	public void setAndGetPoolName() {

		assertThat(this.regionFactoryBean.getPoolName().orElse(null))
			.isEqualTo(SessionCacheTypeAwareRegionFactoryBean.DEFAULT_POOL_NAME);

		this.regionFactoryBean.setPoolName("TestPoolName");

		assertThat(this.regionFactoryBean.getPoolName().orElse(null)).isEqualTo("TestPoolName");

		this.regionFactoryBean.setPoolName("  ");

		assertThat(this.regionFactoryBean.getPoolName().orElse(null))
			.isEqualTo(SessionCacheTypeAwareRegionFactoryBean.DEFAULT_POOL_NAME);

		this.regionFactoryBean.setPoolName("");

		assertThat(this.regionFactoryBean.getPoolName().orElse(null))
			.isEqualTo(SessionCacheTypeAwareRegionFactoryBean.DEFAULT_POOL_NAME);

		this.regionFactoryBean.setPoolName(null);

		assertThat(this.regionFactoryBean.getPoolName().orElse(null))
			.isEqualTo(SessionCacheTypeAwareRegionFactoryBean.DEFAULT_POOL_NAME);
	}

	@Test
	public void setAndGetRegionName() {

		assertThat(this.regionFactoryBean.getRegionName())
			.isEqualTo(SessionCacheTypeAwareRegionFactoryBean.DEFAULT_SESSION_REGION_NAME);

		this.regionFactoryBean.setRegionName("Example");

		assertThat(this.regionFactoryBean.getRegionName()).isEqualTo("Example");

		this.regionFactoryBean.setRegionName("  ");

		assertThat(this.regionFactoryBean.getRegionName())
			.isEqualTo(SessionCacheTypeAwareRegionFactoryBean.DEFAULT_SESSION_REGION_NAME);

		this.regionFactoryBean.setRegionName("");

		assertThat(this.regionFactoryBean.getRegionName())
			.isEqualTo(SessionCacheTypeAwareRegionFactoryBean.DEFAULT_SESSION_REGION_NAME);

		this.regionFactoryBean.setRegionName(null);

		assertThat(this.regionFactoryBean.getRegionName())
			.isEqualTo(SessionCacheTypeAwareRegionFactoryBean.DEFAULT_SESSION_REGION_NAME);
	}

	@Test
	public void setAndGetServerRegionShortcut() {

		assertThat(this.regionFactoryBean.getServerRegionShortcut())
			.isEqualTo(SessionCacheTypeAwareRegionFactoryBean.DEFAULT_SERVER_REGION_SHORTCUT);

		this.regionFactoryBean.setServerRegionShortcut(RegionShortcut.LOCAL_PERSISTENT);

		assertThat(this.regionFactoryBean.getServerRegionShortcut()).isEqualTo(RegionShortcut.LOCAL_PERSISTENT);

		this.regionFactoryBean.setServerRegionShortcut(null);

		assertThat(this.regionFactoryBean.getServerRegionShortcut())
			.isEqualTo(SessionCacheTypeAwareRegionFactoryBean.DEFAULT_SERVER_REGION_SHORTCUT);

		this.regionFactoryBean.setServerRegionShortcut(RegionShortcut.PARTITION_REDUNDANT);

		assertThat(this.regionFactoryBean.getServerRegionShortcut()).isEqualTo(RegionShortcut.PARTITION_REDUNDANT);
	}
}
