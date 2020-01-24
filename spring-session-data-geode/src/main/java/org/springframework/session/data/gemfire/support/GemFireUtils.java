/*
 * Copyright 2019 the original author or authors.
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
package org.springframework.session.data.gemfire.support;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.DataPolicy;
import org.apache.geode.cache.GemFireCache;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionAttributes;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientRegionShortcut;

import org.springframework.data.gemfire.util.CacheUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * {@link GemFireUtils} is an abstract, extensible utility class for working with Apache Geode and Pivotal GemFire
 * objects and types.
 *
 * @author John Blum
 * @see org.apache.geode.cache.Cache
 * @see org.apache.geode.cache.GemFireCache
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.cache.client.ClientCache
 * @since 1.1.0
 */
public abstract class GemFireUtils {

	/**
	 * Null-safe method to close the given {@link Closeable} object.
	 *
	 * @param obj the {@link Closeable} object to close.
	 * @return true if the {@link Closeable} object is not null and was successfully
	 * closed, otherwise return false.
	 * @see java.io.Closeable
	 */
	public static boolean close(Closeable obj) {

		if (obj != null) {
			try {
				obj.close();
				return true;
			}
			catch (IOException ignore) { }
		}

		return false;
	}

	/**
	 * Determines whether the Pivotal GemFire cache is a client.
	 *
	 * @param gemfireCache a reference to the Pivotal GemFire cache.
	 * @return a boolean value indicating whether the Pivotal GemFire cache is a client.
	 * @see org.apache.geode.cache.client.ClientCache
	 * @see org.apache.geode.cache.GemFireCache
	 */
	public static boolean isClient(@Nullable GemFireCache gemfireCache) {
		return CacheUtils.isClient(gemfireCache);
	}

	/**
	 * Determines whether the Pivotal GemFire cache is a peer.
	 *
	 * @param gemFireCache a reference to the Pivotal GemFire cache.
	 * @return a boolean value indicating whether the Pivotal GemFire cache is a peer.
	 * @see org.apache.geode.cache.Cache
	 * @see org.apache.geode.cache.GemFireCache
	 */
	public static boolean isPeer(@Nullable GemFireCache gemFireCache) {
		return gemFireCache instanceof Cache && !isClient(gemFireCache);
	}

	/**
	 * Determines whether the given {@link ClientRegionShortcut} is local only.
	 *
	 * @param shortcut {@link ClientRegionShortcut} to evaluate.
	 * @return a boolean value indicating whether the {@link ClientRegionShortcut} is local or not.
	 * @see org.apache.geode.cache.client.ClientRegionShortcut
	 */
	public static boolean isLocal(@Nullable ClientRegionShortcut shortcut) {
		return shortcut != null && shortcut.name().toLowerCase().contains("local");
	}

	/**
	 * Determines whether the given {@link Region} is a non-local, client {@link Region}, a {@link Region}
	 * for which a corresponding server {@link Region} exists.
	 *
	 * @param region {@link Region} to evaluate.
	 * @return a boolean value indicating whether the given {@link Region} is a non-local, client {@link Region},
	 * a {@link Region} for which a corresponding server {@link Region} exists.
	 * @see org.apache.geode.cache.Region
	 * @see #isPoolConfiguredOrHasServerProxy(Region)
	 */
	public static boolean isNonLocalClientRegion(@Nullable Region<?, ?> region) {

		return Optional.ofNullable(region)
			.filter(GemFireUtils::isPoolConfiguredOrHasServerProxy)
			.map(Region::getRegionService)
			.filter(GemFireCache.class::isInstance)
			.map(GemFireCache.class::cast)
			.filter(GemFireUtils::isClient)
			.isPresent();
	}

	private static boolean isPoolConfiguredOrHasServerProxy(@Nullable Region<?, ?> region) {
		return isPoolConfigured(region) || hasServerProxy(region);
	}

	private static boolean isPoolConfigured(@Nullable Region<?, ?> region) {

		return Optional.ofNullable(region)
			.map(Region::getAttributes)
			.map(RegionAttributes::getPoolName)
			.filter(StringUtils::hasText)
			.isPresent();
	}

	private static boolean hasServerProxy(@Nullable Region<?, ?> region) {

		//return region instanceof AbstractRegion && ((AbstractRegion) region).hasServerProxy();

		return Optional.ofNullable(region)
			.map(Object::getClass)
			.map(regionType -> ReflectionUtils.findMethod(regionType, "hasServerProxy"))
			.map(hasServerProxyMethod -> ReflectionUtils.invokeMethod(hasServerProxyMethod, region))
			.map(Boolean.TRUE::equals)
			.orElse(false);
	}

	/**
	 * Determines whether the given {@link ClientRegionShortcut} is a proxy-based shortcut.
	 *
	 * "Proxy"-based {@link Region Regions} keep no local state.
	 *
	 * @param shortcut {@link ClientRegionShortcut} to evaluate.
	 * @return a boolean value indicating whether the {@link ClientRegionShortcut} refers to a Proxy-based shortcut.
	 * @see org.apache.geode.cache.client.ClientRegionShortcut
	 */
	public static boolean isProxy(ClientRegionShortcut shortcut) {
		return ClientRegionShortcut.PROXY.equals(shortcut);
	}

	/**
	 * Determines whether the given {@link Region} is a {@literal PROXY}.
	 *
	 * @param region {@link Region} to evaluate as a {@literal PROXY}; must not be {@literal null}.
	 * @return a boolean value indicating whether the {@link Region} is a {@literal PROXY}.
	 * @see org.apache.geode.cache.DataPolicy
	 * @see org.apache.geode.cache.Region
	 */
	@SuppressWarnings("rawtypes")
	public static boolean isProxy(Region<?, ?> region) {

		RegionAttributes regionAttributes = region.getAttributes();

		DataPolicy regionDataPolicy = regionAttributes.getDataPolicy();

		return DataPolicy.EMPTY.equals(regionDataPolicy)
			|| Optional.ofNullable(regionDataPolicy)
				.filter(DataPolicy.PARTITION::equals)
				.map(it -> regionAttributes.getPartitionAttributes())
				.filter(partitionAttributes -> partitionAttributes.getLocalMaxMemory() <= 0)
				.isPresent();
	}

	/**
	 * Determines whether the {@link RegionShortcut} is a Proxy-based shortcut.
	 *
	 * "Proxy"-based {@link Region Regions} keep no local state.
	 *
	 * @param shortcut {@link RegionShortcut} to evaluate.
	 * @return a boolean value indicating whether the {@link RegionShortcut} refers to a Proxy-based shortcut.
	 * @see org.apache.geode.cache.RegionShortcut
	 */
	public static boolean isProxy(RegionShortcut shortcut) {

		switch (shortcut) {
			case PARTITION_PROXY:
			case PARTITION_PROXY_REDUNDANT:
			case REPLICATE_PROXY:
				return true;
			default:
				return false;
		}
	}
}
