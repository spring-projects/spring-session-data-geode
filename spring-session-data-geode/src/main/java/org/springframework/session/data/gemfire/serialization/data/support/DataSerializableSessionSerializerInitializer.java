/*
 * Copyright 2019 the original author or authors.
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

package org.springframework.session.data.gemfire.serialization.data.support;

import java.util.Optional;
import java.util.Properties;

import org.apache.geode.DataSerializable;
import org.apache.geode.DataSerializer;
import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheClosedException;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.cache.Declarable;
import org.apache.geode.cache.GemFireCache;

import org.springframework.lang.Nullable;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.serialization.data.provider.DataSerializableSessionSerializer;
import org.springframework.session.data.gemfire.support.GemFireOperationsSessionRepositorySupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Register the custom Spring Session {@link DataSerializableSessionSerializer} with Apache Geode/Pivotal GemFire's
 * DataSerialization framework as the {@link DataSerializer} used to handle de/serialization of the {@link Session},
 * the {@link Session} Attributes and any application domain model objects contained in the {@link Session}
 * (if necessary).
 *
 * @author John Blum
 * @see java.util.Properties
 * @see org.apache.geode.DataSerializable
 * @see org.apache.geode.DataSerializer
 * @see org.apache.geode.cache.Cache
 * @see org.apache.geode.cache.CacheFactory
 * @see org.apache.geode.cache.Declarable
 * @see org.apache.geode.cache.GemFireCache
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.springframework.session.data.gemfire.serialization.data.provider.DataSerializableSessionSerializer
 * @since 2.1.1
 */
@SuppressWarnings("unused")
public class DataSerializableSessionSerializerInitializer implements Declarable {

	private volatile GemFireCache gemfireCache;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * Factory method used to construct a new instance of {@link DataSerializableSessionSerializerInitializer}
	 * initialized with the given, non-required {@link GemFireCache}.
	 *
	 * @param gemfireCache reference to the {@link GemFireCache} instance.
	 * @return a new {@link DataSerializableSessionSerializerInitializer} initialized with the given, non-required
	 * {@link GemFireCache}.
	 * @see org.apache.geode.cache.GemFireCache
	 * @see #DataSerializableSessionSerializerInitializer(GemFireCache)
	 */
	public static DataSerializableSessionSerializerInitializer of(@Nullable GemFireCache gemfireCache) {
		return new DataSerializableSessionSerializerInitializer(gemfireCache);
	}

	/**
	 * Default constructor used to construct a new, un-initialized instance of
	 * {@link DataSerializableSessionSerializerInitializer}.
	 *
	 * For use in Apache Geode/Pivotal GemFire {@literal cache.xml}.
	 */
	public DataSerializableSessionSerializerInitializer() {
		this(null);
	}

	/**
	 * Constructs a new instance of {@link DataSerializableSessionSerializerInitializer} initialized with the given,
	 * non-required {@link GemFireCache}.
	 *
	 * This constructor is meant to be used programmatically and users are encouraged to provide a reference to
	 * the configured and initialized {@link GemFireCache} instance if available.
	 *
	 * @param gemfireCache reference to the {@link GemFireCache} instance.  {@link GemFireCache} may be {@literal null}
	 * in to order to "lazy" initialize or resolve the cache.
	 * @see org.apache.geode.cache.GemFireCache
	 */
	public DataSerializableSessionSerializerInitializer(@Nullable GemFireCache gemfireCache) {
		this.gemfireCache = gemfireCache;
	}

	/**
	 * Returns an {@link Optional} reference to the {@link GemFireCache}.
	 *
	 * The {@link GemFireCache} instance may be resolved lazily when the {@link #initialize(Cache, Properties)} method
	 * is called, such as when processing {@literal cache.xml}.
	 *
	 * @return an {@link Optional} reference to the {@link GemFireCache}.
	 * @see org.apache.geode.cache.GemFireCache
	 * @see java.util.Optional
	 */
	protected Optional<GemFireCache> getGemFireCache() {
		return Optional.ofNullable(this.gemfireCache);
	}

	/**
	 * Returns a reference to the configured {@link Logger} used to capture log events and messages.
	 *
	 * @return a reference to the configured {@link Logger} used for logging.
	 * @see org.slf4j.Logger
	 */
	protected Logger getLogger() {
		return this.logger;
	}

	/**
	 * @see #doInitialization()
	 */
	@Override
	public void initialize(Cache cache, Properties parameters) {

		this.gemfireCache = cache;

		doInitialization();
	}

	/**
	 * Resolves the {@link GemFireCache} instance, configures Spring Session (for Apache Geode/Pivotal GemFire) to
	 * enable and use the DataSerialization framework and finally, registers the {@link DataSerializer DataSerializers}
	 * used by Spring Session and required by Apache Geode/Pivotal GemFire to de/serialize the {@link Session} objects
	 * as {@link DataSerializable} {@link Class types}.
	 *
	 * @see #resolveGemFireCache()
	 * @see #configureUseDataSerialization()
	 * @see org.springframework.session.data.gemfire.serialization.data.provider.DataSerializableSessionSerializer#register()
	 */
	public void doInitialization() {

		resolveGemFireCache();
		registerDataSerializableSessionSerializer();
		configureUseDataSerialization();
	}

	/**
	 * Resolves a reference to the {@link GemFireCache}.
	 *
	 * @return a resolved reference to the {@link GemFireCache}.
	 * @throws CacheClosedException if the {@link GemFireCache} cannot be resolved.
	 * @see org.apache.geode.cache.GemFireCache
	 */
	protected GemFireCache resolveGemFireCache() {
		return getGemFireCache().orElseGet(CacheFactory::getAnyInstance);
	}

	/**
	 * Registers the {@link DataSerializableSessionSerializer} with Apache Geode/Pivotal GemFire
	 * in order to properly handle the Spring Session types.
	 *
	 * @see org.springframework.session.data.gemfire.serialization.data.provider.DataSerializableSessionSerializer#register()
	 */
	protected void registerDataSerializableSessionSerializer() {
		DataSerializableSessionSerializer.register();
	}

	/**
	 * Configures Spring Session (for Apache Geode/Pivotal GemFire) to "use" Apache Geode/Pivotal GemFire's
	 * DataSerialization framework and Delta capable {@link DataSerializable} Session objects.
	 *
	 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSession
	 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSessionAttributes
	 */
	protected void configureUseDataSerialization() {
		InitializingGemFireOperationsSessionRepository.INSTANCE.setUseDataSerialization(true);
	}

	static final class InitializingGemFireOperationsSessionRepository
			extends GemFireOperationsSessionRepositorySupport {

		static final InitializingGemFireOperationsSessionRepository INSTANCE =
			new InitializingGemFireOperationsSessionRepository();

		boolean isDataSerializationConfigured() {
			return isUsingDataSerialization();
		}
	}
}
