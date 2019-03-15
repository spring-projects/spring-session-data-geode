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

package org.springframework.session.data.gemfire.config.annotation.web.http.support;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.client.Pool;

import org.springframework.session.Session;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.session.data.gemfire.serialization.SessionSerializer;

/**
 * The {@link SpringSessionGemFireConfigurer} interface defines a contract for programmatically controlling
 * the configuration of either Apache Geode or Pivotal GemFire as a (HTTP) {@link Session} state management provider
 * in Spring Session.
 *
 * @author John Blum
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @since 1.0.0
 */
@SuppressWarnings("unused")
public interface SpringSessionGemFireConfigurer {

	/**
	 * Defines the {@link ClientCache} {@link Region} data management policy.
	 *
	 * Defaults to {@link ClientRegionShortcut#PROXY}.
	 *
	 * @return a {@link ClientRegionShortcut} used to configure the {@link ClientCache} {@link Region}
	 * data management policy.
	 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration#DEFAULT_CLIENT_REGION_SHORTCUT
	 * @see org.apache.geode.cache.client.ClientRegionShortcut
	 */
	default ClientRegionShortcut getClientRegionShortcut() {
		return GemFireHttpSessionConfiguration.DEFAULT_CLIENT_REGION_SHORTCUT;
	}

	/**
	 * Identifies the {@link Session} attributes by name that will be indexed for query operations.
	 *
	 * For instance, find all {@link Session Sessions} in Apache Geode or Pivotal GemFire having attribute A
	 * defined with value X.
	 *
	 * Defaults to empty {@link String} array.
	 *
	 * @return an array of {@link String Strings} identifying the names of {@link Session} attributes to index.
	 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration#DEFAULT_INDEXABLE_SESSION_ATTRIBUTES
	 */
	default String[] getIndexableSessionAttributes() {
		return GemFireHttpSessionConfiguration.DEFAULT_INDEXABLE_SESSION_ATTRIBUTES;
	}

	/**
	 * Defines the maximum interval in seconds that a {@link Session} can remain inactive before it expires.
	 *
	 * Defaults to {@literal 1800} seconds, or {@literal 30} minutes.
	 *
	 * @return an integer value defining the maximum inactive interval in seconds before the {@link Session} expires.
	 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration#DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS
	 */
	default int getMaxInactiveIntervalInSeconds() {
		return GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS;
	}

	/**
	 * Specifies the name of the specific {@link Pool} used by the {@link ClientCache} {@link Region}
	 * (i.e. {@literal ClusteredSpringSessions}) when performing cache data access operations.
	 *
	 * This is attribute is only used in the client/server topology.
	 *
	 * Defaults to {@literal gemfirePool}.
	 *
	 * @return the name of the {@link Pool} used by the {@link ClientCache} {@link Region}
	 * to send {@link Session} state to the cluster of servers.
	 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration#DEFAULT_POOL_NAME
	 * @see org.apache.geode.cache.client.Pool#getName()
	 */
	default String getPoolName() {
		return GemFireHttpSessionConfiguration.DEFAULT_POOL_NAME;
	}

	/**
	 * Defines the {@link String name} of the (client)cache {@link Region} used to store {@link Session} state.
	 *
	 * Defaults to {@literal ClusteredSpringSessions}.
	 *
	 * @return a {@link String} specifying the name of the (client)cache {@link Region}
	 * used to store {@link Session} state.
	 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration#DEFAULT_SESSION_REGION_NAME
	 * @see org.apache.geode.cache.Region#getName()
	 */
	default String getRegionName() {
		return GemFireHttpSessionConfiguration.DEFAULT_SESSION_REGION_NAME;
	}

	/**
	 * Defines the {@link Cache} {@link Region} data management policy.
	 *
	 * Defaults to {@link RegionShortcut#PARTITION}.
	 *
	 * @return a {@link RegionShortcut} used to specify and configure the {@link Cache} {@link Region}
	 * data management policy.
	 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration#DEFAULT_SERVER_REGION_SHORTCUT
	 * @see org.apache.geode.cache.RegionShortcut
	 */
	default RegionShortcut getServerRegionShortcut() {
		return GemFireHttpSessionConfiguration.DEFAULT_SERVER_REGION_SHORTCUT;
	}

	/**
	 * Defines the bean name of the {@link SessionSerializer} used to serialize {@link Session} state
	 * between client and server or to disk when persisting or overflowing {@link Session} state.
	 *
	 * The bean referred to by its name must be of type {@link SessionSerializer}.
	 *
	 * Defaults to {@literal SessionPdxSerializer}.
	 *
	 * @return a {@link String} containing the bean name of the configured {@link SessionSerializer}.
	 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration#DEFAULT_SESSION_SERIALIZER_BEAN_NAME
	 * @see org.springframework.session.data.gemfire.serialization.pdx.provider.PdxSerializableSessionSerializer
	 * @see org.springframework.session.data.gemfire.serialization.SessionSerializer
	 */
	default String getSessionSerializerBeanName() {
		return GemFireHttpSessionConfiguration.DEFAULT_SESSION_SERIALIZER_BEAN_NAME;
	}
}
