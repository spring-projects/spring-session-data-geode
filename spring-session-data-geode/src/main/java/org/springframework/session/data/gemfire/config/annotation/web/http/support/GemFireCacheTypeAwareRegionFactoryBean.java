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

package org.springframework.session.data.gemfire.config.annotation.web.http.support;

import java.util.Optional;

import org.apache.geode.cache.GemFireCache;
import org.apache.geode.cache.InterestResultPolicy;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionAttributes;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.client.Pool;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.gemfire.GenericRegionFactoryBean;
import org.springframework.data.gemfire.client.ClientRegionFactoryBean;
import org.springframework.data.gemfire.client.Interest;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * The {@link GemFireCacheTypeAwareRegionFactoryBean} class is a Spring {@link FactoryBean}
 * used to construct, configure and initialize the Pivotal GemFire cache {@link Region} used to
 * store and manage Session state.
 *
 * @author John Blum
 * @param <K> the type of keys
 * @param <V> the type of values
 * @see org.apache.geode.cache.GemFireCache
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.cache.RegionAttributes
 * @see org.apache.geode.cache.RegionShortcut
 * @see org.apache.geode.cache.client.ClientRegionShortcut
 * @see org.apache.geode.cache.client.Pool
 * @see org.springframework.beans.factory.BeanFactory
 * @see org.springframework.beans.factory.BeanFactoryAware
 * @see org.springframework.beans.factory.FactoryBean
 * @see org.springframework.beans.factory.InitializingBean
 * @see org.springframework.data.gemfire.GenericRegionFactoryBean
 * @see org.springframework.data.gemfire.client.ClientRegionFactoryBean
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @since 1.1.0
 */
public class GemFireCacheTypeAwareRegionFactoryBean<K, V>
		implements BeanFactoryAware, FactoryBean<Region<K, V>>, InitializingBean {

	protected static final ClientRegionShortcut DEFAULT_CLIENT_REGION_SHORTCUT =
		GemFireHttpSessionConfiguration.DEFAULT_CLIENT_REGION_SHORTCUT;

	protected static final RegionShortcut DEFAULT_SERVER_REGION_SHORTCUT =
		GemFireHttpSessionConfiguration.DEFAULT_SERVER_REGION_SHORTCUT;

	protected static final String DEFAULT_POOL_NAME =
		GemFireHttpSessionConfiguration.DEFAULT_POOL_NAME;

	protected static final String DEFAULT_SESSION_REGION_NAME =
		GemFireHttpSessionConfiguration.DEFAULT_SESSION_REGION_NAME;

	private BeanFactory beanFactory;

	private ClientRegionShortcut clientRegionShortcut;

	private GemFireCache gemfireCache;

	private Region<K, V> region;

	private RegionAttributes<K, V> regionAttributes;

	private RegionShortcut serverRegionShortcut;

	private String poolName;
	private String regionName;

	/**
	 * Post-construction initialization callback to create, configure and initialize the
	 * Pivotal GemFire cache {@link Region} used to store, replicate (distribute) and manage
	 * Session state. This method intelligently handles both client-server and
	 * peer-to-peer (p2p) Pivotal GemFire supported distributed system topologies.
	 *
	 * @throws Exception if the initialization of the Pivotal GemFire cache {@link Region} fails.
	 * @see org.springframework.session.data.gemfire.support.GemFireUtils#isClient(GemFireCache)
	 * @see #getGemfireCache()
	 * @see #newClientRegion(GemFireCache)
	 * @see #newServerRegion(GemFireCache)
	 */
	public void afterPropertiesSet() throws Exception {

		GemFireCache gemfireCache = getGemfireCache();

		this.region = (GemFireUtils.isClient(gemfireCache) ? newClientRegion(gemfireCache)
				: newServerRegion(gemfireCache));
	}

	/**
	 * Constructs a Pivotal GemFire cache {@link Region} using a peer-to-peer (p2p) GemFire
	 * topology to store and manage Session state in a Pivotal GemFire server cluster accessible
	 * from a Pivotal GemFire cache client.
	 *
	 * @param gemfireCache a reference to the GemFire
	 * {@link org.apache.geode.cache.Cache}.
	 * @return a peer-to-peer-based Pivotal GemFire cache {@link Region} to store and manage
	 * Session state.
	 * @throws Exception if the instantiation, configuration and initialization of the
	 * Pivotal GemFire cache {@link Region} fails.
	 * @see org.springframework.data.gemfire.GenericRegionFactoryBean
	 * @see org.apache.geode.cache.GemFireCache
	 * @see org.apache.geode.cache.Region
	 * @see #getRegionAttributes()
	 * @see #getRegionName()
	 * @see #getServerRegionShortcut()
	 */
	protected Region<K, V> newServerRegion(GemFireCache gemfireCache) throws Exception {

		GenericRegionFactoryBean<K, V> serverRegion = new GenericRegionFactoryBean<K, V>();

		serverRegion.setAttributes(getRegionAttributes());
		serverRegion.setCache(gemfireCache);
		serverRegion.setRegionName(getRegionName());
		serverRegion.setShortcut(getServerRegionShortcut());
		serverRegion.afterPropertiesSet();

		return serverRegion.getObject();
	}

	/**
	 * Constructs a Pivotal GemFire cache {@link Region} using the client-server Pivotal GemFire topology
	 * to store and manage Session state in a Pivotal GemFire server cluster accessible from a
	 * Pivotal GemFire cache client.
	 *
	 * @param gemfireCache a reference to the GemFire
	 * {@link org.apache.geode.cache.Cache}.
	 * @return a client-server-based Pivotal GemFire cache {@link Region} to store and manage
	 * Session state.
	 * @throws Exception if the instantiation, configuration and initialization of the
	 * Pivotal GemFire cache {@link Region} fails.
	 * @see org.springframework.data.gemfire.client.ClientRegionFactoryBean
	 * @see org.apache.geode.cache.GemFireCache
	 * @see org.apache.geode.cache.Region
	 * @see #getClientRegionShortcut()
	 * @see #getRegionAttributes()
	 * @see #getRegionName()
	 * @see #registerInterests(boolean)
	 */
	protected Region<K, V> newClientRegion(GemFireCache gemfireCache) throws Exception {

		ClientRegionFactoryBean<K, V> clientRegion = new ClientRegionFactoryBean<K, V>();

		ClientRegionShortcut shortcut = getClientRegionShortcut();

		clientRegion.setAttributes(getRegionAttributes());
		clientRegion.setBeanFactory(getBeanFactory());
		clientRegion.setCache(gemfireCache);
		clientRegion.setInterests(registerInterests(!GemFireUtils.isLocal(shortcut)));
		clientRegion.setPoolName(getPoolName());
		clientRegion.setRegionName(getRegionName());
		clientRegion.setShortcut(shortcut);
		clientRegion.afterPropertiesSet();

		return clientRegion.getObject();
	}

	/**
	 * Decides whether interests will be registered for all keys. Interests is only registered on
	 * a client and typically only when the client is a (CACHING) PROXY to the server (i.e. non-LOCAL only).
	 *
	 * @param register a boolean value indicating whether interests should be registered.
	 * @return an array of Interests KEY/VALUE registrations.
	 * @see org.springframework.data.gemfire.client.Interest
	 */
	@SuppressWarnings("unchecked")
	protected Interest<K>[] registerInterests(boolean register) {

		return register
			? new Interest[] { new Interest<>("ALL_KEYS", InterestResultPolicy.KEYS) }
			: new Interest[0];
	}

	/**
	 * Returns a reference to the constructed Pivotal GemFire cache {@link Region} used to store
	 * and manage Session state.
	 *
	 * @return the {@link Region} used to store and manage Session state.
	 * @throws Exception if the {@link Region} reference cannot be obtained.
	 * @see org.apache.geode.cache.Region
	 */
	public Region<K, V> getObject() throws Exception {
		return this.region;
	}

	/**
	 * Returns the specific type of Pivotal GemFire cache {@link Region} this factory creates when
	 * initialized or Region.class when uninitialized.
	 *
	 * @return the Pivotal GemFire cache {@link Region} class type constructed by this factory.
	 * @see org.apache.geode.cache.Region
	 * @see java.lang.Class
	 */
	@SuppressWarnings("unuchecked")
	public Class<?> getObjectType() {
		return Optional.ofNullable(this.region).map(Object::getClass).orElse((Class) Region.class);
	}

	/**
	 * Returns true indicating the Pivotal GemFire cache {@link Region} created by this factory is
	 * the sole instance.
	 *
	 * @return true to indicate the Pivotal GemFire cache {@link Region} storing and managing
	 * Sessions is a Singleton.
	 */
	public boolean isSingleton() {
		return true;
	}

	/**
	 * Sets a reference to the Spring {@link BeanFactory} responsible for
	 * creating Pivotal GemFire components.
	 *
	 * @param beanFactory reference to the Spring {@link BeanFactory}
	 * @throws IllegalArgumentException if the {@link BeanFactory} reference is null.
	 * @see org.springframework.beans.factory.BeanFactory
	 */
	public void setBeanFactory(BeanFactory beanFactory) {
		Assert.notNull(beanFactory, "BeanFactory is required");
		this.beanFactory = beanFactory;
	}

	/**
	 * Gets a reference to the Spring {@link BeanFactory} responsible for
	 * creating Pivotal GemFire components.
	 *
	 * @return a reference to the Spring {@link BeanFactory}
	 * @throws IllegalStateException if the {@link BeanFactory} reference
	 * is null.
	 * @see org.springframework.beans.factory.BeanFactory
	 */
	protected BeanFactory getBeanFactory() {
		return Optional.ofNullable(this.beanFactory).orElseThrow(() ->
			new IllegalStateException("A reference to the BeanFactory was not properly configured"));
	}

	/**
	 * Sets the {@link Region} data policy used by the Pivotal GemFire cache client to manage
	 * Session state.
	 *
	 * @param clientRegionShortcut a {@link ClientRegionShortcut} to specify the client
	 * {@link Region} data management policy.
	 * @see org.apache.geode.cache.client.ClientRegionShortcut
	 */
	public void setClientRegionShortcut(ClientRegionShortcut clientRegionShortcut) {
		this.clientRegionShortcut = clientRegionShortcut;
	}

	/**
	 * Returns the {@link Region} data policy used by the Pivotal GemFire cache client to manage
	 * Session state. Defaults to {@link ClientRegionShortcut#PROXY}.
	 *
	 * @return a {@link ClientRegionShortcut} specifying the client {@link Region} data
	 * management policy.
	 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration#DEFAULT_CLIENT_REGION_SHORTCUT
	 * @see org.apache.geode.cache.client.ClientRegionShortcut
	 */
	protected ClientRegionShortcut getClientRegionShortcut() {
		return Optional.ofNullable(this.clientRegionShortcut).orElse(DEFAULT_CLIENT_REGION_SHORTCUT);
	}

	/**
	 * Sets a reference to the Pivotal GemFire cache used to construct the appropriate
	 * {@link Region}.
	 *
	 * @param gemfireCache a reference to the Pivotal GemFire cache.
	 * @throws IllegalArgumentException if the {@link GemFireCache} reference is null.
	 */
	public void setGemfireCache(GemFireCache gemfireCache) {
		this.gemfireCache = Optional.ofNullable(gemfireCache).orElseThrow(() ->
			new IllegalArgumentException("GemFireCache is required"));
	}

	/**
	 * Returns a reference to the Pivotal GemFire cache used to construct the appropriate
	 * {@link Region}.
	 *
	 * @return a reference to the Pivotal GemFire cache.
	 * @throws IllegalStateException if the {@link GemFireCache} reference is null.
	 */
	protected GemFireCache getGemfireCache() {
		return Optional.ofNullable(this.gemfireCache).orElseThrow(() ->
			new IllegalStateException("A reference to the GemFireCache was not properly configured"));
	}

	/**
	 * Sets the name of the Pivotal GemFire {@link Pool} used by the client Region for managing Sessions
	 * during cache operations involving the server.
	 *
	 * @param poolName the name of a Pivotal GemFire {@link Pool}.
	 * @see Pool#getName()
	 */
	public void setPoolName(final String poolName) {
		this.poolName = poolName;
	}

	/**
	 * Returns the name of the Pivotal GemFire {@link Pool} used by the client Region for managing Sessions
	 * during cache operations involving the server.
	 *
	 * @return the name of a Pivotal GemFire {@link Pool}.
	 * @see Pool#getName()
	 */
	protected String getPoolName() {
		return Optional.ofNullable(this.poolName).filter(StringUtils::hasText).orElse(DEFAULT_POOL_NAME);
	}

	/**
	 * Sets the Pivotal GemFire {@link RegionAttributes} used to configure the Pivotal GemFire cache
	 * {@link Region} used to store and manage Session state.
	 *
	 * @param regionAttributes the Pivotal GemFire {@link RegionAttributes} used to configure the
	 * Pivotal GemFire cache {@link Region}.
	 * @see org.apache.geode.cache.RegionAttributes
	 */
	public void setRegionAttributes(RegionAttributes<K, V> regionAttributes) {
		this.regionAttributes = regionAttributes;
	}

	/**
	 * Returns the Pivotal GemFire {@link RegionAttributes} used to configure the Pivotal GemFire cache
	 * {@link Region} used to store and manage Session state.
	 *
	 * @return the Pivotal GemFire {@link RegionAttributes} used to configure the Pivotal GemFire cache
	 * {@link Region}.
	 * @see org.apache.geode.cache.RegionAttributes
	 */
	protected RegionAttributes<K, V> getRegionAttributes() {
		return this.regionAttributes;
	}

	/**
	 * Sets the name of the Pivotal GemFire cache {@link Region} use to store and manage Session
	 * state.
	 *
	 * @param regionName a String specifying the name of the Pivotal GemFire cache {@link Region}.
	 */
	public void setRegionName(final String regionName) {
		this.regionName = regionName;
	}

	/**
	 * Returns the configured name of the Pivotal GemFire cache {@link Region} use to store and
	 * manage Session state. Defaults to "ClusteredSpringSessions"
	 *
	 * @return a String specifying the name of the Pivotal GemFire cache {@link Region}.
	 * @see org.apache.geode.cache.Region#getName()
	 */
	protected String getRegionName() {
		return Optional.ofNullable(this.regionName).filter(StringUtils::hasText).orElse(DEFAULT_SESSION_REGION_NAME);
	}

	/**
	 * Sets the {@link Region} data policy used by the Pivotal GemFire peer cache to manage
	 * Session state.
	 *
	 * @param serverRegionShortcut a {@link RegionShortcut} to specify the peer
	 * {@link Region} data management policy.
	 * @see org.apache.geode.cache.RegionShortcut
	 */
	public void setServerRegionShortcut(RegionShortcut serverRegionShortcut) {
		this.serverRegionShortcut = serverRegionShortcut;
	}

	/**
	 * Returns the {@link Region} data policy used by the Pivotal GemFire peer cache to manage
	 * Session state. Defaults to {@link RegionShortcut#PARTITION}.
	 *
	 * @return a {@link RegionShortcut} specifying the peer {@link Region} data management
	 * policy.
	 * @see org.apache.geode.cache.RegionShortcut
	 */
	protected RegionShortcut getServerRegionShortcut() {
		return Optional.ofNullable(this.serverRegionShortcut).orElse(DEFAULT_SERVER_REGION_SHORTCUT);
	}
}
