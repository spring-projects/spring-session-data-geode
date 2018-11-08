/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.session.data.gemfire.config.annotation.web.http.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.GemFireCache;
import org.apache.geode.cache.InterestResultPolicy;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionAttributes;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientRegionShortcut;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.data.gemfire.client.ClientRegionShortcutWrapper;
import org.springframework.data.gemfire.client.Interest;
import org.springframework.session.Session;

/**
 * The GemFireCacheTypeAwareRegionFactoryBeanTests class is a test suite of test cases
 * testing the contract and functionality of the GemFireCacheTypeAwareRegionFactoryBean
 * class.
 *
 * @author John Blum
 * @since 1.1.0
 * @see org.junit.Rule
 * @see org.junit.Test
 * @see org.junit.rules.ExpectedException
 * @see org.mockito.Mockito
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.support.GemFireCacheTypeAwareRegionFactoryBean
 * @see org.apache.geode.cache.Cache
 * @see org.apache.geode.cache.GemFireCache
 * @see org.apache.geode.cache.InterestResultPolicy
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.cache.RegionAttributes
 * @see org.apache.geode.cache.RegionShortcut
 * @see org.apache.geode.cache.client.ClientCache
 * @see org.apache.geode.cache.client.ClientRegionShortcut
 */
@RunWith(MockitoJUnitRunner.class)
public class GemFireCacheTypeAwareRegionFactoryBeanTests {

	@Mock
	Cache mockCache;

	@Mock
	ClientCache mockClientCache;

	@Mock
	Region<Object, Session> mockClientRegion;

	@Mock
	Region<Object, Session> mockServerRegion;

	private GemFireCacheTypeAwareRegionFactoryBean<Object, Session> regionFactoryBean;

	@Before
	public void setup() {
		this.regionFactoryBean = new GemFireCacheTypeAwareRegionFactoryBean<>();
	}

	protected void afterPropertiesSetCreatesCorrectRegionForGemFireCacheType(GemFireCache expectedCache,
			Region<Object, Session> expectedRegion) throws Exception {

		this.regionFactoryBean = new GemFireCacheTypeAwareRegionFactoryBean<Object, Session>() {

			@Override
			protected Region<Object, Session> newClientRegion(GemFireCache gemfireCache, String name) throws Exception {
				assertThat(gemfireCache).isSameAs(expectedCache);
				return GemFireCacheTypeAwareRegionFactoryBeanTests.this.mockClientRegion;
			}

			@Override
			protected Region<Object, Session> newServerRegion(GemFireCache gemfireCache, String name) throws Exception {
				assertThat(gemfireCache).isSameAs(expectedCache);
				return GemFireCacheTypeAwareRegionFactoryBeanTests.this.mockServerRegion;
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
		afterPropertiesSetCreatesCorrectRegionForGemFireCacheType(this.mockClientCache, this.mockClientRegion);
	}

	@Test
	public void afterPropertiesSetCreatesServerRegionForPeerCache() throws Exception {
		afterPropertiesSetCreatesCorrectRegionForGemFireCacheType(this.mockCache, this.mockServerRegion);
	}

	@Test
	public void allKeysInterestsRegistration() {

		Interest<Object>[] interests = this.regionFactoryBean.registerInterests(true);

		assertThat(interests).isNotNull();
		assertThat(interests.length).isEqualTo(1);
		assertThat(interests[0].isDurable()).isFalse();
		assertThat(interests[0].getKey().toString()).isEqualTo("ALL_KEYS");
		assertThat(interests[0].getPolicy()).isEqualTo(InterestResultPolicy.KEYS);
		assertThat(interests[0].isReceiveValues()).isTrue();
	}

	@Test
	public void emptyInterestsRegistration() {

		Interest<Object>[] interests = this.regionFactoryBean.registerInterests(false);

		assertThat(interests).isNotNull();
		assertThat(interests).isEmpty();
	}

	@Test
	public void getInterestsWhenClientRegionShortcutIsNotLocalReturnsAllKeys() {

		Arrays.stream(ClientRegionShortcut.values())
			.filter(clientRegionShortcut -> !clientRegionShortcut.name().contains("LOCAL"))
			.forEach(clientRegionShortcut -> {

				this.regionFactoryBean.setClientRegionShortcut(clientRegionShortcut);

				assertThat(this.regionFactoryBean.getClientRegionShortcut()).isEqualTo(clientRegionShortcut);
				assertThat(ClientRegionShortcutWrapper.valueOf(clientRegionShortcut).isLocal()).isFalse();

				Interest<Object>[] interests = this.regionFactoryBean.getInterests();

				assertThat(interests).isNotNull();
				assertThat(interests).hasSize(1);
				assertThat(interests[0].isDurable()).isFalse();
				assertThat(interests[0].getKey().toString()).isEqualTo("ALL_KEYS");
				assertThat(interests[0].getPolicy()).isEqualTo(InterestResultPolicy.KEYS);
				assertThat(interests[0].isReceiveValues()).isTrue();
			});
	}

	@Test
	public void getInterestsWhenClientRegionShortcutIsLocalReturnsNoKeys() {

		Arrays.stream(ClientRegionShortcut.values())
			.filter(clientRegionShortcut -> clientRegionShortcut.name().contains("LOCAL"))
			.forEach(clientRegionShortcut -> {

				this.regionFactoryBean.setClientRegionShortcut(clientRegionShortcut);

				assertThat(this.regionFactoryBean.getClientRegionShortcut()).isEqualTo(clientRegionShortcut);
				assertThat(ClientRegionShortcutWrapper.valueOf(clientRegionShortcut).isLocal()).isTrue();

				Interest<Object>[] interests = this.regionFactoryBean.getInterests();

				assertThat(interests).isNotNull();
				assertThat(interests).isEmpty();
			});
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
	public void setAndGetBeanFactory() {

		BeanFactory mockBeanFactory = mock(BeanFactory.class);

		this.regionFactoryBean.setBeanFactory(mockBeanFactory);

		assertThat(this.regionFactoryBean.getBeanFactory()).isEqualTo(mockBeanFactory);
	}

	@Test
	public void setAndGetClientRegionShortcut() {

		assertThat(this.regionFactoryBean.getClientRegionShortcut())
			.isEqualTo(GemFireCacheTypeAwareRegionFactoryBean.DEFAULT_CLIENT_REGION_SHORTCUT);

		this.regionFactoryBean.setClientRegionShortcut(ClientRegionShortcut.LOCAL_PERSISTENT);

		assertThat(this.regionFactoryBean.getClientRegionShortcut()).isEqualTo(
			ClientRegionShortcut.LOCAL_PERSISTENT);

		this.regionFactoryBean.setClientRegionShortcut(null);

		assertThat(this.regionFactoryBean.getClientRegionShortcut()).isEqualTo(
			GemFireCacheTypeAwareRegionFactoryBean.DEFAULT_CLIENT_REGION_SHORTCUT);
	}

	@Test
	public void setAndGetGemfireCache() {

		Cache mockCache = mock(Cache.class);

		this.regionFactoryBean.setCache(mockCache);

		assertThat(this.regionFactoryBean.getCache()).isEqualTo(mockCache);
	}

	@Test
	public void setAndGetPoolName() {

		assertThat(this.regionFactoryBean.getPoolName().orElse(null))
			.isEqualTo(GemFireCacheTypeAwareRegionFactoryBean.DEFAULT_POOL_NAME);

		this.regionFactoryBean.setPoolName("TestPoolName");

		assertThat(this.regionFactoryBean.getPoolName().orElse(null)).isEqualTo("TestPoolName");

		this.regionFactoryBean.setPoolName("  ");

		assertThat(this.regionFactoryBean.getPoolName().orElse(null))
			.isEqualTo(GemFireCacheTypeAwareRegionFactoryBean.DEFAULT_POOL_NAME);

		this.regionFactoryBean.setPoolName("");

		assertThat(this.regionFactoryBean.getPoolName().orElse(null))
			.isEqualTo(GemFireCacheTypeAwareRegionFactoryBean.DEFAULT_POOL_NAME);

		this.regionFactoryBean.setPoolName(null);

		assertThat(this.regionFactoryBean.getPoolName().orElse(null))
			.isEqualTo(GemFireCacheTypeAwareRegionFactoryBean.DEFAULT_POOL_NAME);
	}

	@Test
	@SuppressWarnings({ "deprecation", "unchecked" })
	public void setAndGetRegionAttributes() {

		RegionAttributes<Object, Session> mockRegionAttributes = mock(RegionAttributes.class);

		assertThat(this.regionFactoryBean.getRegionAttributes()).isNull();

		this.regionFactoryBean.setRegionAttributes(mockRegionAttributes);

		assertThat(this.regionFactoryBean.getRegionAttributes()).isSameAs(mockRegionAttributes);

		this.regionFactoryBean.setRegionAttributes(null);

		assertThat(this.regionFactoryBean.getRegionAttributes()).isNull();
	}

	@Test
	public void setAndGetRegionName() {

		assertThat(this.regionFactoryBean.getRegionName())
			.isEqualTo(GemFireCacheTypeAwareRegionFactoryBean.DEFAULT_SESSION_REGION_NAME);

		this.regionFactoryBean.setRegionName("Example");

		assertThat(this.regionFactoryBean.getRegionName()).isEqualTo("Example");

		this.regionFactoryBean.setRegionName("  ");

		assertThat(this.regionFactoryBean.getRegionName())
			.isEqualTo(GemFireCacheTypeAwareRegionFactoryBean.DEFAULT_SESSION_REGION_NAME);

		this.regionFactoryBean.setRegionName("");

		assertThat(this.regionFactoryBean.getRegionName())
			.isEqualTo(GemFireCacheTypeAwareRegionFactoryBean.DEFAULT_SESSION_REGION_NAME);

		this.regionFactoryBean.setRegionName(null);

		assertThat(this.regionFactoryBean.getRegionName())
			.isEqualTo(GemFireCacheTypeAwareRegionFactoryBean.DEFAULT_SESSION_REGION_NAME);
	}

	@Test
	public void setAndGetServerRegionShortcut() {

		assertThat(this.regionFactoryBean.getServerRegionShortcut())
			.isEqualTo(GemFireCacheTypeAwareRegionFactoryBean.DEFAULT_SERVER_REGION_SHORTCUT);

		this.regionFactoryBean.setServerRegionShortcut(RegionShortcut.LOCAL_PERSISTENT);

		assertThat(this.regionFactoryBean.getServerRegionShortcut()).isEqualTo(RegionShortcut.LOCAL_PERSISTENT);

		this.regionFactoryBean.setServerRegionShortcut(null);

		assertThat(this.regionFactoryBean.getServerRegionShortcut())
			.isEqualTo(GemFireCacheTypeAwareRegionFactoryBean.DEFAULT_SERVER_REGION_SHORTCUT);
	}
}
