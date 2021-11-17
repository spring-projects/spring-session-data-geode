/*
 * Copyright 2020 the original author or authors.
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

package org.springframework.session.data.gemfire.config.annotation.web.http;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Properties;

import jakarta.servlet.http.HttpSession;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.client.Pool;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.config.annotation.web.http.support.SpringSessionGemFireConfigurer;
import org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy;
import org.springframework.session.data.gemfire.serialization.SessionSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;

/**
 * Add this {@link Annotation annotation} to a Spring application defined {@code @Configuration} {@link Class}
 * exposing the {@link SessionRepositoryFilter} as a bean named {@literal springSessionRepositoryFilter}
 * to back the {@link HttpSession} with either Apache Geode or Pivotal GemFire.
 *
 * In order to use this {@link Annotation annotation}, a single Apache Geode / Pivotal GemFire {@link ClientCache}
 * or {@link Cache Peer Cache} instance must be provided.
 *
 * The most common use case is to use Apache Geode or Pivotal GemFire's client/server topology, where your
 * Spring Session enabled application uses a {@link ClientCache} to manage {@link Session} state in a cluster
 * of dedicated Apache Geode or Pivotal GemFire servers.
 *
 * For example:
 *
 * <pre>
 * <code>
 * {@literal @ClientCacheApplication(subscriptionEnabled = true)}
 * {@literal @EnableGemFireHttpSession(poolName = "DEFAULT"}
 * public class ClientCacheHttpSessionConfiguration {
 *
 * }
 * </code>
 * </pre>
 *
 * Alternatively, though less common (and not recommended), you can use Spring Session with Apache Geode
 * or Pivotal GemFire in the embedded {@link Cache Peer Cache} scenario, where your Spring Session enabled application
 * is technically a {@literal peer} in the Apache Geode or Pivotal GemFire cluster.
 *
 * For example:
 *
 * <pre>
 * <code>
 * {@literal @PeerCacheApplication}
 * {@literal @EnableGemFireHttpSession}
 * public class PeerCacheHttpSessionConfiguration {
 *
 * }
 * </code>
 * </pre>
 *
 * More advanced configurations can extend {@link GemFireHttpSessionConfiguration} instead.
 *
 * @author John Blum
 * @see java.lang.annotation.Annotation
 * @see java.util.Properties
 * @see jakarta.servlet.http.HttpSession
 * @see org.apache.geode.cache.Cache
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.cache.client.ClientCache
 * @see org.apache.geode.cache.client.Pool
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.context.annotation.Import
 * @see org.springframework.core.env.Environment
 * @see org.springframework.session.Session
 * @see org.springframework.session.config.annotation.web.http.EnableSpringHttpSession
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.support.SpringSessionGemFireConfigurer
 * @see org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy
 * @see org.springframework.session.data.gemfire.serialization.SessionSerializer
 * @since 1.1.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Configuration
@Import(GemFireHttpSessionConfiguration.class)
public @interface EnableGemFireHttpSession {

	/**
	 * Defines the {@link ClientCache} {@link Region} data management policy.
	 *
	 * Defaults to {@link ClientRegionShortcut#PROXY}.
	 *
	 * Use the {@literal spring.session.data.gemfire.cache.client.region.shortcut} in Spring Boot
	 * {@literal application.properties}.
	 *
	 * @return a {@link ClientRegionShortcut} used to configure the {@link ClientCache} {@link Region}
	 * data management policy.
	 * @see org.apache.geode.cache.client.ClientRegionShortcut
	 */
	ClientRegionShortcut clientRegionShortcut() default ClientRegionShortcut.PROXY;

	/**
	 * Determines whether the configuration for Spring Session using Apache Geode or Pivotal GemFire should be exposed
	 * in the Spring {@link Environment} as {@link Properties}.
	 *
	 * Currently, users may configure Spring Session for Apache Geode or Pivotal GemFire using attributes on this
	 * {@link Annotation}, using the well-known and documented {@link Properties}
	 * (e.g. {@literal spring.session.data.gemfire.session.expiration.max-inactive-interval-seconds})
	 * or using the {@link SpringSessionGemFireConfigurer} declared as a bean in the Spring application context.
	 *
	 * The {@link Properties} that are exposed will use the well-known property {@link String names} that are documented
	 * in this {@link Annotation Annotation's} attributes.
	 *
	 * The values of the resulting {@link Properties} follows the precedence as outlined in the documentation:
	 * first any {@link SpringSessionGemFireConfigurer} bean defined takes precedence, followed by explicit
	 * {@link Properties} declared in Spring Boot {@literal application.properties} and finally, this
	 * {@link Annotation Annotation's} attribute values.
	 *
	 * Defaults to {@literal false}.
	 *
	 * Use {@literal spring.session.data.gemfire.session.configuration.expose} in Spring Boot
	 * {@literal application.properties}.
	 *
	 * @return a boolean value indicating whether to expose the configuration of Spring Session using Apache Geode
	 * or Pivotal GemFire in the Spring {@link Environment} as {@link Properties}.
	 */
	boolean exposeConfigurationAsProperties() default GemFireHttpSessionConfiguration.DEFAULT_EXPOSE_CONFIGURATION_AS_PROPERTIES;

	/**
	 * Identifies the {@link Session} attributes by name that will be indexed for query operations.
	 *
	 * For instance, find all {@link Session Sessions} in Apache Geode or Pivotal GemFire having attribute A
	 * defined with value X.
	 *
	 * Defaults to empty {@link String} array.
	 *
	 * Use the {@literal spring.session.data.gemfire.session.attributes.indexed}
	 * in Spring Boot {@literal application.properties}.
	 *
	 * @return an array of {@link String Strings} identifying the names of {@link Session} attributes to index.
	 */
	String[] indexableSessionAttributes() default {};

	/**
	 * Defines the maximum interval in seconds that a {@link Session} can remain inactive before it expires.
	 *
	 * Defaults to {@literal 1800} seconds, or {@literal 30} minutes.
	 *
	 * Use the {@literal spring.session.data.gemfire.session.expiration.max-inactive-interval-seconds} in Spring Boot
	 * {@literal application.properties}.
	 *
	 * @return an integer value defining the maximum inactive interval in seconds before the {@link Session} expires.
	 */
	int maxInactiveIntervalInSeconds() default 1800;

	/**
	 * Specifies the name of the specific {@link Pool} used by the {@link ClientCache} {@link Region}
	 * (i.e. {@literal ClusteredSpringSessions}) when performing cache data access operations.
	 *
	 * This is attribute is only used in the client/server topology.
	 *
	 * Defaults to {@literal gemfirePool}.
	 *
	 * Use the {@literal spring.session.data.gemfire.cache.client.pool.name} in Spring Boot
	 * {@literal application.properties}.
	 *
	 * @return the name of the {@link Pool} used by the {@link ClientCache} {@link Region}
	 * to send {@link Session} state to the cluster of servers.
	 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration#DEFAULT_POOL_NAME
	 */
	String poolName() default GemFireHttpSessionConfiguration.DEFAULT_POOL_NAME;

	/**
	 * Defines the {@link String name} of the (client)cache {@link Region} used to store {@link Session} state.
	 *
	 * Defaults to {@literal ClusteredSpringSessions}.
	 *
	 * Use the {@literal spring.session.data.gemfire.session.region.name} in Spring Boot
	 * {@literal application.properties}.
	 *
	 * @return a {@link String} specifying the name of the (client)cache {@link Region}
	 * used to store {@link Session} state.
	 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration#DEFAULT_SESSION_REGION_NAME
	 */
	String regionName() default GemFireHttpSessionConfiguration.DEFAULT_SESSION_REGION_NAME;

	/**
	 * Defines the {@link Cache} {@link Region} data management policy.
	 *
	 * Defaults to {@link RegionShortcut#PARTITION}.
	 *
	 * Use the {@literal spring.session.data.gemfire.cache.server.region.shortcut} in Spring Boot
	 * {@literal application.properties}.
	 *
	 * @return a {@link RegionShortcut} used to specify and configure the {@link Cache} {@link Region}
	 * data management policy.
	 * @see org.apache.geode.cache.RegionShortcut
	 */
	RegionShortcut serverRegionShortcut() default RegionShortcut.PARTITION;

	/**
	 * Defines the name of the bean referring to the {@link SessionExpirationPolicy} used to configure
	 * the {@link Session} expiration logic and strategy.
	 *
	 * The {@link Object bean} referred to by its {@link String name} must be of type {@link SessionExpirationPolicy}.
	 *
	 * Defaults to unset.

	 * Use the {@literal spring.session.data.gemfire.session.expiration.bean-name} in Spring Boot
	 * {@literal application.properties}.
	 *
	 * @return a {@link String} containing the bean name of the configured {@link SessionExpirationPolicy}.
	 * @see org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy
	 */
	String sessionExpirationPolicyBeanName() default GemFireHttpSessionConfiguration.DEFAULT_SESSION_EXPIRATION_POLICY_BEAN_NAME;

	/**
	 * Defines the name of the bean referring to the {@link SessionSerializer} used to serialize {@link Session} state
	 * between client and server or to disk when persisting or overflowing {@link Session} state.
	 *
	 * The {@link Object bean} referred to by its {@link String name} must be of type {@link SessionSerializer}.
	 *
	 * Defaults to {@literal SessionPdxSerializer}.
	 *
	 * Use the {@literal spring.session.data.gemfire.session.serializer.bean-name} in Spring Boot
	 * {@literal application.properties}.
	 *
	 * @return a {@link String} containing the bean name of the configured {@link SessionSerializer}.
	 * @see org.springframework.session.data.gemfire.serialization.pdx.provider.PdxSerializableSessionSerializer
	 * @see org.springframework.session.data.gemfire.serialization.SessionSerializer
	 */
	String sessionSerializerBeanName() default GemFireHttpSessionConfiguration.DEFAULT_SESSION_SERIALIZER_BEAN_NAME;

}
