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

package org.springframework.session.data.gemfire.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.Closeable;
import java.io.IOException;

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

/**
 * Unit tests for {@link GemFireUtils}.
 *
 * @author John Blum
 * @since 1.1.0
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.apache.geode.cache.Cache
 * @see org.apache.geode.cache.GemFireCache
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.cache.client.ClientCache
 * @see org.springframework.session.data.gemfire.support.GemFireUtils
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
	public void peerCacheIsNotClient() {
		assertThat(GemFireUtils.isClient(mock(Cache.class))).isFalse();
	}

	@Test
	public void peerCacheIsPeer() {
		assertThat(GemFireUtils.isPeer(mock(Cache.class))).isTrue();
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

		assertThat(GemFireUtils.isLocal(ClientRegionShortcut.LOCAL)).isTrue();
		assertThat(GemFireUtils.isLocal(ClientRegionShortcut.LOCAL_HEAP_LRU)).isTrue();
		assertThat(GemFireUtils.isLocal(ClientRegionShortcut.LOCAL_OVERFLOW)).isTrue();
		assertThat(GemFireUtils.isLocal(ClientRegionShortcut.LOCAL_PERSISTENT)).isTrue();
		assertThat(GemFireUtils.isLocal(ClientRegionShortcut.LOCAL_PERSISTENT_OVERFLOW)).isTrue();
	}

	@Test
	public void clientRegionShortcutIsNotLocal() {

		assertThat(GemFireUtils.isLocal(ClientRegionShortcut.CACHING_PROXY)).isFalse();
		assertThat(GemFireUtils.isLocal(ClientRegionShortcut.CACHING_PROXY_HEAP_LRU)).isFalse();
		assertThat(GemFireUtils.isLocal(ClientRegionShortcut.CACHING_PROXY_OVERFLOW)).isFalse();
		assertThat(GemFireUtils.isLocal(ClientRegionShortcut.PROXY)).isFalse();
	}

	@Test
	public void clientRegionShortcutIsProxy() {
		assertThat(GemFireUtils.isProxy(ClientRegionShortcut.PROXY)).isTrue();
	}

	@Test
	public void clientRegionShortcutIsNotProxy() {

		assertThat(GemFireUtils.isProxy(ClientRegionShortcut.CACHING_PROXY)).isFalse();
		assertThat(GemFireUtils.isProxy(ClientRegionShortcut.CACHING_PROXY_HEAP_LRU)).isFalse();
		assertThat(GemFireUtils.isProxy(ClientRegionShortcut.CACHING_PROXY_OVERFLOW)).isFalse();
		assertThat(GemFireUtils.isProxy(ClientRegionShortcut.LOCAL)).isFalse();
		assertThat(GemFireUtils.isProxy(ClientRegionShortcut.LOCAL_HEAP_LRU)).isFalse();
		assertThat(GemFireUtils.isProxy(ClientRegionShortcut.LOCAL_OVERFLOW)).isFalse();
		assertThat(GemFireUtils.isProxy(ClientRegionShortcut.LOCAL_PERSISTENT)).isFalse();
		assertThat(GemFireUtils.isProxy(ClientRegionShortcut.LOCAL_PERSISTENT_OVERFLOW)).isFalse();
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

		given(mockRegion.getAttributes()).willReturn(mockRegionAttributes);
		given(mockRegionAttributes.getDataPolicy()).willReturn(DataPolicy.NORMAL);

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

		given(mockRegion.getAttributes()).willReturn(mockRegionAttributes);
		given(mockRegionAttributes.getDataPolicy()).willReturn(DataPolicy.PRELOADED);

		assertThat(GemFireUtils.isProxy(mockRegion)).isFalse();

		verify(mockRegion, times(1)).getAttributes();
		verify(mockRegionAttributes, times(1)).getDataPolicy();
	}

	@Test
	public void replicateRegionIsNotProxy() {

		Region mockRegion = mock(Region.class);

		RegionAttributes mockRegionAttributes = mock(RegionAttributes.class);

		given(mockRegion.getAttributes()).willReturn(mockRegionAttributes);
		given(mockRegionAttributes.getDataPolicy()).willReturn(DataPolicy.REPLICATE);

		assertThat(GemFireUtils.isProxy(mockRegion)).isFalse();

		verify(mockRegion, times(1)).getAttributes();
		verify(mockRegionAttributes, times(1)).getDataPolicy();
	}

	@Test
	public void regionShortcutIsProxy() {

		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_PROXY)).isTrue();
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_PROXY_REDUNDANT)).isTrue();
		assertThat(GemFireUtils.isProxy(RegionShortcut.REPLICATE_PROXY)).isTrue();
	}

	@Test
	public void regionShortcutIsNotProxy() {

		assertThat(GemFireUtils.isProxy(RegionShortcut.LOCAL)).isFalse();
		assertThat(GemFireUtils.isProxy(RegionShortcut.LOCAL_HEAP_LRU)).isFalse();
		assertThat(GemFireUtils.isProxy(RegionShortcut.LOCAL_OVERFLOW)).isFalse();
		assertThat(GemFireUtils.isProxy(RegionShortcut.LOCAL_PERSISTENT)).isFalse();
		assertThat(GemFireUtils.isProxy(RegionShortcut.LOCAL_PERSISTENT_OVERFLOW)).isFalse();
		assertThat(GemFireUtils.isProxy(RegionShortcut.REPLICATE)).isFalse();
		assertThat(GemFireUtils.isProxy(RegionShortcut.REPLICATE_HEAP_LRU)).isFalse();
		assertThat(GemFireUtils.isProxy(RegionShortcut.REPLICATE_OVERFLOW)).isFalse();
		assertThat(GemFireUtils.isProxy(RegionShortcut.REPLICATE_PERSISTENT)).isFalse();
		assertThat(GemFireUtils.isProxy(RegionShortcut.REPLICATE_PERSISTENT_OVERFLOW)).isFalse();
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION)).isFalse();
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_HEAP_LRU)).isFalse();
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_OVERFLOW)).isFalse();
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_PERSISTENT)).isFalse();
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_PERSISTENT_OVERFLOW)).isFalse();
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_REDUNDANT)).isFalse();
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_REDUNDANT_HEAP_LRU)).isFalse();
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_REDUNDANT_OVERFLOW)).isFalse();
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_REDUNDANT_PERSISTENT)).isFalse();
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_REDUNDANT_PERSISTENT_OVERFLOW)).isFalse();
	}

	@Test
	public void toRegionPath() {

		assertThat(GemFireUtils.toRegionPath("A")).isEqualTo("/A");
		assertThat(GemFireUtils.toRegionPath("Example")).isEqualTo("/Example");
		assertThat(GemFireUtils.toRegionPath("/Example")).isEqualTo("//Example");
		assertThat(GemFireUtils.toRegionPath("/")).isEqualTo("//");
		assertThat(GemFireUtils.toRegionPath("")).isEqualTo("/");
	}
}
