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

import java.util.Optional;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.client.Pool;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.data.gemfire.config.annotation.support.CacheTypeAwareRegionFactoryBean;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.util.StringUtils;

/**
 * The {@link SessionCacheTypeAwareRegionFactoryBean} class is a Spring {@link FactoryBean} used to construct,
 * configure and initialize the Apache Geode/Pivotal GemFire cache {@link Region} used to store and manage
 * Session state.
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
 * @see org.springframework.beans.factory.FactoryBean
 * @see org.springframework.beans.factory.InitializingBean
 * @see org.springframework.context.SmartLifecycle
 * @see org.springframework.data.gemfire.GenericRegionFactoryBean
 * @see org.springframework.data.gemfire.client.ClientRegionFactoryBean
 * @see org.springframework.data.gemfire.support.AbstractFactoryBeanSupport
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @since 1.1.0
 */
public class SessionCacheTypeAwareRegionFactoryBean<K, V> extends CacheTypeAwareRegionFactoryBean<K, V> {

	protected static final ClientRegionShortcut DEFAULT_CLIENT_REGION_SHORTCUT =
		GemFireHttpSessionConfiguration.DEFAULT_CLIENT_REGION_SHORTCUT;

	protected static final RegionShortcut DEFAULT_SERVER_REGION_SHORTCUT =
		GemFireHttpSessionConfiguration.DEFAULT_SERVER_REGION_SHORTCUT;

	protected static final String DEFAULT_POOL_NAME =
		GemFireHttpSessionConfiguration.DEFAULT_POOL_NAME;

	protected static final String DEFAULT_SESSION_REGION_NAME =
		GemFireHttpSessionConfiguration.DEFAULT_SESSION_REGION_NAME;

	private String regionName;

	/**
	 * Returns the {@link Region} data management policy used by the Apache Geode/Pivotal GemFire {@link ClientCache}
	 * to manage {@link Session} state.
	 *
	 * Defaults to {@link ClientRegionShortcut#PROXY}.
	 *
	 * @return a {@link ClientRegionShortcut} specifying the client {@link Region} data management policy
	 * used to manage {@link Session} state.
	 * @see org.apache.geode.cache.client.ClientRegionShortcut
	 */
	@Override
	public ClientRegionShortcut getClientRegionShortcut() {

		return Optional.ofNullable(super.getClientRegionShortcut())
			.orElse(DEFAULT_CLIENT_REGION_SHORTCUT);
	}

	/**
	 * Returns the name of the Pivotal GemFire {@link Pool} used by the client Region for managing Sessions
	 * during cache operations involving the server.
	 *
	 * @return the name of a Pivotal GemFire {@link Pool}.
	 * @see Pool#getName()
	 */
	@Override
	protected Optional<String> getPoolName() {
		return Optional.of(super.getPoolName().orElse(DEFAULT_POOL_NAME));
	}

	/**
	 * Sets the {@link String name} of the {@link Region} used to store and manage {@link Session} state.
	 *
	 * @param regionName {@link String} containing the name of the {@link Region} used to store
	 * and manage {@link Session} state.
	 */
	public void setRegionName(String regionName) {
		this.regionName = regionName;
	}

	/**
	 * Returns the configured {@link String name} of the {@link Region} used to store and manage {@link Session} state.
	 *
	 * Defaults to {@literal ClusteredSpringSessions}.
	 *
	 * @return a {@link String} containing the name of the {@link Region} used to store
	 * and manage {@link Session} state.
	 * @see org.apache.geode.cache.Region#getName()
	 */
	protected String getRegionName() {

		return Optional.ofNullable(this.regionName)
			.filter(StringUtils::hasText)
			.orElse(DEFAULT_SESSION_REGION_NAME);
	}

	/**
	 * Resolves the {@link String name} of the {@link Region} used to manage {@link Session} state.
	 *
	 * @return the {@link String name} of the {@link Region} used to manage {@link Session} state.
	 * @see #getRegionName()
	 */
	@Override
	public String resolveRegionName() {
		return getRegionName();
	}

	/**
	 * Returns the {@link Region} data management policy used by the Apache Geode/Pivotal GemFire peer {@link Cache}
	 * to manage {@link Session} state.
	 *
	 * Defaults to {@link RegionShortcut#PARTITION}.
	 *
	 * @return a {@link RegionShortcut} specifying the peer {@link Region} data management policy
	 * to manage {@link Session} state.
	 * @see org.apache.geode.cache.RegionShortcut
	 */
	@Override
	public RegionShortcut getServerRegionShortcut() {

		return Optional.ofNullable(super.getServerRegionShortcut())
			.orElse(DEFAULT_SERVER_REGION_SHORTCUT);
	}
}
