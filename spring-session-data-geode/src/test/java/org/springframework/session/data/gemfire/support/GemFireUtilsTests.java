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

package org.springframework.session.data.gemfire.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.DataPolicy;
import org.apache.geode.cache.GemFireCache;
import org.apache.geode.cache.PartitionAttributes;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionAttributes;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.internal.cache.AbstractRegion;

/**
 * Unit tests for {@link GemFireUtils}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.apache.geode.cache.Cache
 * @see org.apache.geode.cache.GemFireCache
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.cache.client.ClientCache
 * @see org.springframework.session.data.gemfire.support.GemFireUtils
 * @since 1.1.0
 */
public class GemFireUtilsTests {

	@Test
	public void closeNonNullCloseableReturnsTrue() throws IOException {

		Closeable mockCloseable = mock(Closeable.class);

		assertThat(GemFireUtils.close(mockCloseable)).isTrue();

		verify(mockCloseable, times(1)).close();
	}

	@Test
	public void closeNonNullCloseableThrowingIOExceptionReturnsFalse() throws IOException {

		Closeable mockCloseable = mock(Closeable.class);

		doThrow(new IOException("test")).when(mockCloseable).close();

		assertThat(GemFireUtils.close(mockCloseable)).isFalse();

		verify(mockCloseable, times(1)).close();
	}

	@Test
	public void closeNullCloseableReturnsFalse() {
		assertThat(GemFireUtils.close(null)).isFalse();
	}

	@Test
	public void clientCacheIsClient() {
		assertThat(GemFireUtils.isClient(mock(ClientCache.class))).isTrue();
	}

	@Test
	public void genericCacheIsNotClient() {
		assertThat(GemFireUtils.isClient(mock(GemFireCache.class))).isFalse();
	}

	@Test
	public void nullIsNotClient() {
		assertThat(GemFireUtils.isClient(null)).isFalse();
	}

	@Test
	public void peerCacheIsNotClient() {
		assertThat(GemFireUtils.isClient(mock(Cache.class))).isFalse();
	}

	@Test
	public void peerCacheIsPeer() {
		assertThat(GemFireUtils.isPeer(mock(Cache.class))).isTrue();
	}

	@Test
	public void nullIsNotPeer() {
		assertThat(GemFireUtils.isPeer(null)).isFalse();
	}

	@Test
	public void genericCacheIsNotPeer() {
		assertThat(GemFireUtils.isPeer(mock(GemFireCache.class))).isFalse();
	}

	@Test
	public void clientCacheIsNotPeer() {
		assertThat(GemFireUtils.isPeer(mock(ClientCache.class))).isFalse();
	}

	@Test
	public void clientRegionShortcutIsLocal() {

		Arrays.stream(ClientRegionShortcut.values())
			.filter(it -> it.name().toLowerCase().contains("local"))
			.forEach(it -> assertThat(GemFireUtils.isLocal(it)).isTrue());
	}

	@Test
	public void clientRegionShortcutIsNotLocal() {

		Arrays.stream(ClientRegionShortcut.values())
			.filter(it -> !it.name().toLowerCase().contains("local"))
			.forEach(it -> assertThat(GemFireUtils.isLocal(it)).isFalse());
	}

	@Test
	public void clientRegionShortcutIsProxy() {
		assertThat(GemFireUtils.isProxy(ClientRegionShortcut.PROXY)).isTrue();
	}

	@Test
	public void clientRegionShortcutIsNotProxy() {

		Arrays.stream(ClientRegionShortcut.values())
			.filter(it -> !ClientRegionShortcut.PROXY.equals(it))
			.forEach(it -> assertThat(GemFireUtils.isProxy(it)).isFalse());
	}

	@Test
	public void clientRegionWithPoolIsNonLocalClientRegion() {

		ClientCache mockClientCache = mock(ClientCache.class);

		Region mockRegion = mock(Region.class);

		RegionAttributes mockRegionAttributes = mock(RegionAttributes.class);

		when(mockRegion.getAttributes()).thenReturn(mockRegionAttributes);
		when(mockRegion.getRegionService()).thenReturn(mockClientCache);
		when(mockRegionAttributes.getPoolName()).thenReturn("Dead");

		assertThat(GemFireUtils.isNonLocalClientRegion(mockRegion)).isTrue();

		verify(mockRegion, times(1)).getAttributes();
		verify(mockRegion, times(1)).getRegionService();
		verify(mockRegionAttributes, times(1)).getPoolName();
	}

	@Test
	public void clientRegionWithServerProxyIsNonLocalClientRegion() {

		ClientCache mockClientCache = mock(ClientCache.class);

		AbstractRegion mockRegion = mock(AbstractRegion.class);

		RegionAttributes mockRegionAttributes = mock(RegionAttributes.class);

		when(mockRegion.getAttributes()).thenReturn(mockRegionAttributes);
		when(mockRegion.getRegionService()).thenReturn(mockClientCache);
		when(mockRegion.hasServerProxy()).thenReturn(true);
		when(mockRegionAttributes.getPoolName()).thenReturn("  ");

		assertThat(GemFireUtils.isNonLocalClientRegion(mockRegion)).isTrue();

		verify(mockRegion, times(1)).getAttributes();
		verify(mockRegion, times(1)).getRegionService();
		verify(mockRegion, times(1)).hasServerProxy();
		verify(mockRegionAttributes, times(1)).getPoolName();
	}

	@Test
	public void clientRegionWithNoPoolAndNoServerProxyIsNotNonLocalClientRegion() {

		ClientCache mockClientCache = mock(ClientCache.class);

		AbstractRegion mockRegion = mock(AbstractRegion.class);

		RegionAttributes mockRegionAttributes = mock(RegionAttributes.class);

		when(mockRegion.getAttributes()).thenReturn(mockRegionAttributes);
		when(mockRegion.getRegionService()).thenReturn(mockClientCache);
		when(mockRegion.hasServerProxy()).thenReturn(false);
		when(mockRegionAttributes.getPoolName()).thenReturn(null);

		assertThat(GemFireUtils.isNonLocalClientRegion(mockRegion)).isFalse();

		verify(mockRegion, times(1)).getAttributes();
		verify(mockRegion, never()).getRegionService();
		verify(mockRegion, times(1)).hasServerProxy();
		verify(mockRegionAttributes, times(1)).getPoolName();
	}

	@Test
	public void nonClientRegionWithPoolIsNotNonLocalClientRegion() {

		GemFireCache mockGemFireCache = mock(GemFireCache.class);

		Region mockRegion = mock(Region.class);

		RegionAttributes mockRegionAttributes = mock(RegionAttributes.class);

		when(mockRegion.getAttributes()).thenReturn(mockRegionAttributes);
		when(mockRegion.getRegionService()).thenReturn(mockGemFireCache);
		when(mockRegionAttributes.getPoolName()).thenReturn("Car");

		assertThat(GemFireUtils.isNonLocalClientRegion(mockRegion)).isFalse();

		verify(mockRegion, times(1)).getAttributes();
		verify(mockRegion, times(1)).getRegionService();
		verify(mockRegionAttributes, times(1)).getPoolName();
	}

	@Test
	public void peerRegionWithServerProxyIsNotNonLocalClientRegion() {

		Cache mockPeerCache = mock(Cache.class);

		AbstractRegion mockRegion = mock(AbstractRegion.class);

		RegionAttributes mockRegionAttributes = mock(RegionAttributes.class);

		when(mockRegion.getAttributes()).thenReturn(mockRegionAttributes);
		when(mockRegion.getRegionService()).thenReturn(mockPeerCache);
		when(mockRegion.hasServerProxy()).thenReturn(true);
		when(mockRegionAttributes.getPoolName()).thenReturn("");

		assertThat(GemFireUtils.isNonLocalClientRegion(mockRegion)).isFalse();

		verify(mockRegion, times(1)).getAttributes();
		verify(mockRegion, times(1)).getRegionService();
		verify(mockRegionAttributes, times(1)).getPoolName();
	}

	@Test
	public void emptyRegionIsProxy() {

		Region mockRegion = mock(Region.class);

		RegionAttributes mockRegionAttributes = mock(RegionAttributes.class);

		when(mockRegion.getAttributes()).thenReturn(mockRegionAttributes);
		when(mockRegionAttributes.getDataPolicy()).thenReturn(DataPolicy.EMPTY);

		assertThat(GemFireUtils.isProxy(mockRegion)).isTrue();

		verify(mockRegion, times(1)).getAttributes();
		verify(mockRegionAttributes, times(1)).getDataPolicy();
	}

	@Test
	public void partitionRegionWithNoLocalMaxMemoryIsProxy() {

		Region mockRegion = mock(Region.class);

		RegionAttributes mockRegionAttributes = mock(RegionAttributes.class);

		PartitionAttributes mockPartitionAttributes = mock(PartitionAttributes.class);

		when(mockRegion.getAttributes()).thenReturn(mockRegionAttributes);
		when(mockRegionAttributes.getDataPolicy()).thenReturn(DataPolicy.PARTITION);
		when(mockRegionAttributes.getPartitionAttributes()).thenReturn(mockPartitionAttributes);
		when(mockPartitionAttributes.getLocalMaxMemory()).thenReturn(0);

		assertThat(GemFireUtils.isProxy(mockRegion)).isTrue();

		verify(mockRegion, times(1)).getAttributes();
		verify(mockRegionAttributes, times(1)).getDataPolicy();
		verify(mockRegionAttributes, times(1)).getPartitionAttributes();
		verify(mockPartitionAttributes, times(1)).getLocalMaxMemory();
	}

	@Test
	public void partitionRegionWithNegativeLocalMaxMemoryIsProxy() {

		Region mockRegion = mock(Region.class);

		RegionAttributes mockRegionAttributes = mock(RegionAttributes.class);

		PartitionAttributes mockPartitionAttributes = mock(PartitionAttributes.class);

		when(mockRegion.getAttributes()).thenReturn(mockRegionAttributes);
		when(mockRegionAttributes.getDataPolicy()).thenReturn(DataPolicy.PARTITION);
		when(mockRegionAttributes.getPartitionAttributes()).thenReturn(mockPartitionAttributes);
		when(mockPartitionAttributes.getLocalMaxMemory()).thenReturn(-1);

		assertThat(GemFireUtils.isProxy(mockRegion)).isTrue();

		verify(mockRegion, times(1)).getAttributes();
		verify(mockRegionAttributes, times(1)).getDataPolicy();
		verify(mockRegionAttributes, times(1)).getPartitionAttributes();
		verify(mockPartitionAttributes, times(1)).getLocalMaxMemory();
	}

	@Test
	public void normalRegionIsNotProxy() {

		Region mockRegion = mock(Region.class);

		RegionAttributes mockRegionAttributes = mock(RegionAttributes.class);

		when(mockRegion.getAttributes()).thenReturn(mockRegionAttributes);
		when(mockRegionAttributes.getDataPolicy()).thenReturn(DataPolicy.NORMAL);

		assertThat(GemFireUtils.isProxy(mockRegion)).isFalse();

		verify(mockRegion, times(1)).getAttributes();
		verify(mockRegionAttributes, times(1)).getDataPolicy();
	}

	@Test
	public void partitionRegionWithLocalMaxMemoryIsNotProxy() {

		Region mockRegion = mock(Region.class);

		RegionAttributes mockRegionAttributes = mock(RegionAttributes.class);

		PartitionAttributes mockPartitionAttributes = mock(PartitionAttributes.class);

		when(mockRegion.getAttributes()).thenReturn(mockRegionAttributes);
		when(mockRegionAttributes.getDataPolicy()).thenReturn(DataPolicy.PARTITION);
		when(mockRegionAttributes.getPartitionAttributes()).thenReturn(mockPartitionAttributes);
		when(mockPartitionAttributes.getLocalMaxMemory()).thenReturn(1);

		assertThat(GemFireUtils.isProxy(mockRegion)).isFalse();

		verify(mockRegion, times(1)).getAttributes();
		verify(mockRegionAttributes, times(1)).getDataPolicy();
		verify(mockRegionAttributes, times(1)).getPartitionAttributes();
		verify(mockPartitionAttributes, times(1)).getLocalMaxMemory();
	}

	@Test
	public void partitionRegionWithNoPartitionAttributesIsNotProxy() {

		Region mockRegion = mock(Region.class);

		RegionAttributes mockRegionAttributes = mock(RegionAttributes.class);

		when(mockRegion.getAttributes()).thenReturn(mockRegionAttributes);
		when(mockRegionAttributes.getDataPolicy()).thenReturn(DataPolicy.PARTITION);
		when(mockRegionAttributes.getPartitionAttributes()).thenReturn(null);

		assertThat(GemFireUtils.isProxy(mockRegion)).isFalse();

		verify(mockRegion, times(1)).getAttributes();
		verify(mockRegionAttributes, times(1)).getDataPolicy();
		verify(mockRegionAttributes, times(1)).getPartitionAttributes();
	}

	@Test
	public void preloadedRegionIsNotProxy() {

		Region mockRegion = mock(Region.class);

		RegionAttributes mockRegionAttributes = mock(RegionAttributes.class);

		when(mockRegion.getAttributes()).thenReturn(mockRegionAttributes);
		when(mockRegionAttributes.getDataPolicy()).thenReturn(DataPolicy.PRELOADED);

		assertThat(GemFireUtils.isProxy(mockRegion)).isFalse();

		verify(mockRegion, times(1)).getAttributes();
		verify(mockRegionAttributes, times(1)).getDataPolicy();
	}

	@Test
	public void replicateRegionIsNotProxy() {

		Region mockRegion = mock(Region.class);

		RegionAttributes mockRegionAttributes = mock(RegionAttributes.class);

		when(mockRegion.getAttributes()).thenReturn(mockRegionAttributes);
		when(mockRegionAttributes.getDataPolicy()).thenReturn(DataPolicy.REPLICATE);

		assertThat(GemFireUtils.isProxy(mockRegion)).isFalse();

		verify(mockRegion, times(1)).getAttributes();
		verify(mockRegionAttributes, times(1)).getDataPolicy();
	}

	@Test
	public void regionShortcutIsProxy() {

		Arrays.stream(RegionShortcut.values())
			.filter(it -> it.name().toLowerCase().contains("proxy"))
			.forEach(it -> assertThat(GemFireUtils.isProxy(it)).isTrue());
	}

	@Test
	public void regionShortcutIsNotProxy() {

		Arrays.stream(RegionShortcut.values())
			.filter(it -> !it.name().toLowerCase().contains("proxy"))
			.forEach(it -> assertThat(GemFireUtils.isProxy(it)).isFalse());
	}
}
