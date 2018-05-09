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

package org.springframework.session.data.gemfire.server;

import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.cache.ExpirationAction;
import org.apache.geode.cache.ExpirationAttributes;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionFactory;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.server.CacheServer;

/**
 * The {@link GemFireServer} class is a Java application class used to launch a Pivotal GemFire Server
 * with a peer {@link Cache}, a {@link CacheServer} and the {@literal ClusteredSpringSessions}
 * {@link RegionShortcut#PARTITION} {@link Region}.
 *
 * @author John Blum
 * @see java.util.Properties
 * @see org.apache.geode.cache.Cache
 * @see org.apache.geode.cache.GemFireCache
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.cache.server.CacheServer
 * @since 2.0.0
 */
public class GemFireServer implements Runnable {

	protected static final Integer GEMFIRE_CACHE_SERVER_PORT =
		Integer.getInteger("spring.session.data.gemfire.cache.server.port", CacheServer.DEFAULT_PORT);

	public static void main(String[] args) {
		newGemFireServer(args).run();
	}

	private final String[] args;

	public static GemFireServer newGemFireServer(String[] args) {
		return new GemFireServer(args);
	}

	protected GemFireServer(String[] args) {
		this.args = Optional.ofNullable(args)
			.orElseThrow(() -> new IllegalArgumentException("GemFireServer process arguments are required"));
	}

	protected String[] getArguments() {
		return this.args;
	}

	@Override
	public void run() {
		run(getArguments());
	}

	@SuppressWarnings("unused")
	protected void run(String[] args) {
		createClusteredSpringSessionsRegion(addCacheServer(gemfireCache(gemfireProperties())));
	}

	protected Properties gemfireProperties() {

		Properties gemfireProperties = new Properties();

		gemfireProperties.setProperty("name", "o.s.s.d.g.server.GemFireServer");
		gemfireProperties.setProperty("jmx-manager", "true");
		//gemfireProperties.setProperty("log-file", "gemfire-server.log");
		gemfireProperties.setProperty("log-level", "error");

		return gemfireProperties;
	}

	protected Cache gemfireCache(Properties gemfireProperties) {
		return new CacheFactory(gemfireProperties).create();
	}

	protected Cache addCacheServer(Cache gemfireCache) {

		try {
			CacheServer cacheServer = gemfireCache.addCacheServer();

			cacheServer.setHostnameForClients("localhost");
			cacheServer.setPort(GEMFIRE_CACHE_SERVER_PORT);
			cacheServer.start();

			return gemfireCache;
		}
		catch (IOException cause) {
			throw new RuntimeException("GemFire CacheServer failed to start", cause);
		}
	}

	protected Cache createClusteredSpringSessionsRegion(Cache gemfireCache) {

		RegionFactory<Object, Object> clusteredSpringSessionsRegion =
			gemfireCache.createRegionFactory(RegionShortcut.PARTITION);

		clusteredSpringSessionsRegion.setEntryIdleTimeout(
			new ExpirationAttributes(Long.valueOf(TimeUnit.MINUTES.toSeconds(30)).intValue(),
				ExpirationAction.INVALIDATE));

		clusteredSpringSessionsRegion.create("ClusteredSpringSessions");

		return gemfireCache;
	}
}
