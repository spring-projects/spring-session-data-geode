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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.gemfire.util.ArrayUtils.nullSafeArray;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientRegionShortcut;

import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;

/**
 * Unit tests for {@link SpringSessionGemFireConfigurer}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.support.SpringSessionGemFireConfigurer
 * @since 2.1.1
 */
public class SpringSessionGemFireConfigurerUnitTests {

	private SpringSessionGemFireConfigurer newTestConfigurerWithAllOverrides() {

		return new SpringSessionGemFireConfigurer() {

			@Override
			public ClientRegionShortcut getClientRegionShortcut() {
				return ClientRegionShortcut.LOCAL;
			}

			@Override
			public String[] getIndexableSessionAttributes() {
				return new String[] { "fieldOne", "fieldTwo" };
			}

			@Override
			public int getMaxInactiveIntervalInSeconds() {
				return 300;
			}

			@Override
			public String getPoolName() {
				return "MockPool";
			}

			@Override
			public String getRegionName() {
				return "MockRegion";
			}

			@Override
			public RegionShortcut getServerRegionShortcut() {
				return RegionShortcut.REPLICATE;
			}

			@Override
			public String getSessionSerializerBeanName() {
				return "MockSerializer";
			}
		};
	}

	private SpringSessionGemFireConfigurer newTestConfigurerWithNoOverrides() {
		return new SpringSessionGemFireConfigurer() { };
	}

	private SpringSessionGemFireConfigurer newTestConfigurerWithSelectOverrides() {

		return new SpringSessionGemFireConfigurer() {

			@Override
			public ClientRegionShortcut getClientRegionShortcut() {
				return ClientRegionShortcut.CACHING_PROXY;
			}

			@Override
			public int getMaxInactiveIntervalInSeconds() {
				return 600;
			}

			@Override
			public String getPoolName() {
				return "TestPool";
			}

			@Override
			public String getRegionName() {
				return "TestRegion";
			}
		};
	}

	private Method[] filterDeclaredMethods(Method[] methods) {

		List<String> targetMethodNames =
			Arrays.stream(nullSafeArray(SpringSessionGemFireConfigurer.class.getMethods(), Method.class))
				.map(Method::getName)
				.collect(Collectors.toList());

		return Arrays.stream(nullSafeArray(methods, Method.class))
			.filter(method -> targetMethodNames.contains(method.getName()))
			.collect(Collectors.toList())
			.toArray(new Method[0]);
	}

	@Test
	public void classGetDeclaredMethodsOnCustomConfigurerObjectHasAllMethods() {

		SpringSessionGemFireConfigurer testConfigurer = newTestConfigurerWithAllOverrides();

		assertThat(testConfigurer).isNotNull();
		assertThat(testConfigurer.getClientRegionShortcut()).isEqualTo(ClientRegionShortcut.LOCAL);
		assertThat(testConfigurer.getIndexableSessionAttributes()).containsExactly("fieldOne", "fieldTwo");
		assertThat(testConfigurer.getMaxInactiveIntervalInSeconds()).isEqualTo(300);
		assertThat(testConfigurer.getPoolName()).isEqualTo("MockPool");
		assertThat(testConfigurer.getRegionName()).isEqualTo("MockRegion");
		assertThat(testConfigurer.getServerRegionShortcut()).isEqualTo(RegionShortcut.REPLICATE);
		assertThat(testConfigurer.getSessionSerializerBeanName()).isEqualTo("MockSerializer");

		Method[] declaredMethods = filterDeclaredMethods(testConfigurer.getClass().getDeclaredMethods());

		List<String> declaredMethodNames =
			Arrays.stream(declaredMethods).map(Method::getName).sorted().collect(Collectors.toList());

		assertThat(declaredMethods).isNotNull();
		assertThat(declaredMethods).hasSize(7);

		assertThat(declaredMethodNames)
			.containsExactly("getClientRegionShortcut", "getIndexableSessionAttributes",
				"getMaxInactiveIntervalInSeconds", "getPoolName", "getRegionName", "getServerRegionShortcut",
				"getSessionSerializerBeanName");
	}

	@Test
	public void classGetDeclaredMethodsOnCustomConfigurerObjectHasNoMethods() {

		SpringSessionGemFireConfigurer testConfigurer = newTestConfigurerWithNoOverrides();

		assertThat(testConfigurer).isNotNull();
		assertThat(testConfigurer.getClientRegionShortcut())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_CLIENT_REGION_SHORTCUT);
		assertThat(testConfigurer.getIndexableSessionAttributes())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_INDEXABLE_SESSION_ATTRIBUTES);
		assertThat(testConfigurer.getMaxInactiveIntervalInSeconds())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS);
		assertThat(testConfigurer.getPoolName())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_POOL_NAME);
		assertThat(testConfigurer.getRegionName())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_SESSION_REGION_NAME);
		assertThat(testConfigurer.getServerRegionShortcut())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_SERVER_REGION_SHORTCUT);
		assertThat(testConfigurer.getSessionSerializerBeanName())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_SESSION_SERIALIZER_BEAN_NAME);

		Method[] declaredMethods = filterDeclaredMethods(testConfigurer.getClass().getDeclaredMethods());

		assertThat(declaredMethods).isNotNull();
		assertThat(declaredMethods).isEmpty();
	}

	@Test
	public void classGetDeclaredMethodsOnCustomConfigurerObjectOnlyHasOverriddenMethods() {

		SpringSessionGemFireConfigurer testConfigurer = newTestConfigurerWithSelectOverrides();

		assertThat(testConfigurer).isNotNull();
		assertThat(testConfigurer.getClientRegionShortcut()).isEqualTo(ClientRegionShortcut.CACHING_PROXY);
		assertThat(testConfigurer.getIndexableSessionAttributes()).isEmpty();
		assertThat(testConfigurer.getMaxInactiveIntervalInSeconds()).isEqualTo(600);
		assertThat(testConfigurer.getPoolName()).isEqualTo("TestPool");
		assertThat(testConfigurer.getRegionName()).isEqualTo("TestRegion");
		assertThat(testConfigurer.getServerRegionShortcut()).isEqualTo(RegionShortcut.PARTITION);
		assertThat(testConfigurer.getSessionSerializerBeanName())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_SESSION_SERIALIZER_BEAN_NAME);

		Method[] declaredMethods = filterDeclaredMethods(testConfigurer.getClass().getDeclaredMethods());

		List<String> declaredMethodNames =
			Arrays.stream(declaredMethods).map(Method::getName).sorted().collect(Collectors.toList());

		assertThat(declaredMethods).isNotNull();
		assertThat(declaredMethods).hasSize(4);

		assertThat(declaredMethodNames)
			.containsExactly("getClientRegionShortcut", "getMaxInactiveIntervalInSeconds", "getPoolName", "getRegionName");

		assertThat(declaredMethodNames)
			.doesNotContain("getIndexableSessionAttributes", "getServerRegionShortcut", "getSessionSerializerBeanName");
	}
}
