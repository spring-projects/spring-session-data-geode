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

package org.springframework.session.data.gemfire.config.annotation.web.http;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.apache.geode.DataSerializer;
import org.apache.geode.cache.Cache;
import org.apache.geode.cache.ExpirationAction;
import org.apache.geode.cache.ExpirationAttributes;
import org.apache.geode.cache.GemFireCache;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionAttributes;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.client.Pool;
import org.apache.geode.pdx.PdxSerializer;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.data.gemfire.GemfireTemplate;
import org.springframework.data.gemfire.IndexFactoryBean;
import org.springframework.data.gemfire.IndexType;
import org.springframework.data.gemfire.RegionAttributesFactoryBean;
import org.springframework.data.gemfire.config.xml.GemfireConstants;
import org.springframework.data.gemfire.util.ArrayUtils;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;
import org.springframework.session.data.gemfire.GemFireOperationsSessionRepository;
import org.springframework.session.data.gemfire.config.annotation.web.http.support.GemFireCacheTypeAwareRegionFactoryBean;
import org.springframework.session.data.gemfire.config.annotation.web.http.support.SessionAttributesIndexFactoryBean;
import org.springframework.session.data.gemfire.config.annotation.web.http.support.SpringSessionGemFireConfigurer;
import org.springframework.session.data.gemfire.serialization.SessionSerializer;
import org.springframework.session.data.gemfire.serialization.data.provider.DataSerializableSessionSerializer;
import org.springframework.session.data.gemfire.serialization.data.support.DataSerializerSessionSerializerAdapter;
import org.springframework.session.data.gemfire.serialization.pdx.provider.PdxSerializableSessionSerializer;
import org.springframework.session.data.gemfire.serialization.pdx.support.ComposablePdxSerializer;
import org.springframework.session.data.gemfire.serialization.pdx.support.PdxSerializerSessionSerializerAdapter;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.util.StringUtils;

/**
 * The {@link GemFireHttpSessionConfiguration} class is a Spring {@link Configuration @Configuration} class
 * used to configure and initialize Pivotal GemFire/Apache Geode as a clustered, distributed and replicated
 * {@link javax.servlet.http.HttpSession} provider implementation in Spring {@link Session}.
 *
 * @author John Blum
 * @see org.apache.geode.cache.ExpirationAttributes
 * @see org.apache.geode.cache.GemFireCache
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.cache.RegionAttributes
 * @see org.apache.geode.cache.RegionShortcut
 * @see org.apache.geode.cache.client.ClientRegionShortcut
 * @see org.apache.geode.cache.client.Pool
 * @see org.springframework.beans.factory.BeanClassLoaderAware
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.context.annotation.DependsOn
 * @see org.springframework.context.annotation.ImportAware
 * @see org.springframework.core.annotation.AnnotationAttributes
 * @see org.springframework.core.type.AnnotationMetadata
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.data.gemfire.GemfireTemplate
 * @see org.springframework.data.gemfire.IndexFactoryBean
 * @see org.springframework.data.gemfire.RegionAttributesFactoryBean
 * @see org.springframework.session.Session
 * @see org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration
 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.AbstractGemFireHttpSessionConfiguration
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.support.GemFireCacheTypeAwareRegionFactoryBean
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.support.SessionAttributesIndexFactoryBean
 * @since 1.1.0
 */
@Configuration
@SuppressWarnings("unused")
public class GemFireHttpSessionConfiguration extends AbstractGemFireHttpSessionConfiguration implements ImportAware {

	/**
	 * Default maximum interval in seconds in which a {@link Session} can remain inactive before it expires.
	 */
	public static final int DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS = (int) TimeUnit.MINUTES.toSeconds(30);

	/**
	 * Key and Value class type constraints applied to the {@link Session} {@link Region}.
	 */
	protected static final Class<Object> SESSION_REGION_KEY_CONSTRAINT = Object.class;
	protected static final Class<GemFireSession> SESSION_REGION_VALUE_CONSTRAINT = GemFireSession.class;

	/**
	 * Default {@link ClientRegionShortcut} used to configure the data management policy of the {@link ClientCache}
	 * {@link Region} that will store {@link Session} state.
	 */
	public static final ClientRegionShortcut DEFAULT_CLIENT_REGION_SHORTCUT = ClientRegionShortcut.PROXY;

	/**
	 * Default {@link RegionShortcut} used to configure the data management policy of the {@link Cache} {@link Region}
	 * that will store {@link Session} state.
	 */
	public static final RegionShortcut DEFAULT_SERVER_REGION_SHORTCUT = RegionShortcut.PARTITION;

	/**
	 * Name of the connection {@link Pool} used by the client {@link Region} to send {@link Session} state
	 * to the cluster of  Apache Geode servers.
	 */
	public static final String DEFAULT_POOL_NAME = GemfireConstants.DEFAULT_GEMFIRE_POOL_NAME;

	/**
	 * Default name of (Client)Cache {@link Region} used to store {@link Session} state.
	 */
	public static final String DEFAULT_SESSION_REGION_NAME = "ClusteredSpringSessions";

	/**
	 * Set of defaults for {@link Session} serialization.
	 */
	public static final String SESSION_DATA_SERIALIZER_BEAN_NAME = "SessionDataSerializer";
	public static final String SESSION_PDX_SERIALIZER_BEAN_NAME = "SessionPdxSerializer";
	public static final String SESSION_SERIALIZER_BEAN_ALIAS = "SessionSerializerRegisteredBeanAlias";

	public static final String DEFAULT_SESSION_SERIALIZER_BEAN_NAME = SESSION_PDX_SERIALIZER_BEAN_NAME;

	/**
	 * Defaults names of all {@link Session} attributes that will be indexed by Apache Geode.
	 */
	public static final String[] DEFAULT_INDEXABLE_SESSION_ATTRIBUTES = {};

	private int maxInactiveIntervalInSeconds = DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS;

	private ClientRegionShortcut clientRegionShortcut = DEFAULT_CLIENT_REGION_SHORTCUT;

	private RegionShortcut serverRegionShortcut = DEFAULT_SERVER_REGION_SHORTCUT;

	private String poolName = DEFAULT_POOL_NAME;

	private String sessionRegionName = DEFAULT_SESSION_REGION_NAME;

	private String sessionSerializerBeanName = DEFAULT_SESSION_SERIALIZER_BEAN_NAME;

	private String[] indexableSessionAttributes = DEFAULT_INDEXABLE_SESSION_ATTRIBUTES;

	/**
	 * Gets the {@link ClientRegionShortcut} used to configure the data management policy of the {@link ClientCache}
	 * {@link Region} that will store {@link Session} state.
	 *
	 * Defaults to {@link ClientRegionShortcut#PROXY}.
	 *
	 * @param shortcut {@link ClientRegionShortcut} used to configure the data management policy
	 * of the {@link ClientCache} {@link Region}.
	 * @see EnableGemFireHttpSession#clientRegionShortcut()
	 * @see org.apache.geode.cache.client.ClientRegionShortcut
	 */
	public void setClientRegionShortcut(ClientRegionShortcut shortcut) {
		this.clientRegionShortcut = shortcut;
	}

	/**
	 * Gets the {@link ClientRegionShortcut} used to configure the data management policy of the {@link ClientCache}
	 * {@link Region} that will store {@link Session} state.
	 *
	 * Defaults to {@link ClientRegionShortcut#PROXY}.
	 *
	 * @return the {@link ClientRegionShortcut} used to configure the data management policy
	 * of the {@link ClientCache} {@link Region}.
	 * @see org.apache.geode.cache.client.ClientRegionShortcut
	 */
	protected ClientRegionShortcut getClientRegionShortcut() {
		return Optional.ofNullable(this.clientRegionShortcut).orElse(DEFAULT_CLIENT_REGION_SHORTCUT);
	}

	/**
 	* Sets the names of all {@link Session} attributes that will be indexed.
	 *
	 * @param indexableSessionAttributes an array of {@link String Strings} containing the names
	 * of all {@link Session} attributes for which an Index will be created.
	 * @see EnableGemFireHttpSession#indexableSessionAttributes()
	 */
	public void setIndexableSessionAttributes(String[] indexableSessionAttributes) {
		this.indexableSessionAttributes = indexableSessionAttributes;
	}

	/**
	 * Get the names of all {@link Session} attributes that will be indexed.
	 *
	 * @return an array of {@link String Strings} containing the names of all {@link Session} attributes
	 * for which an Index will be created. Defaults to an empty array if unspecified.
	 */
	protected String[] getIndexableSessionAttributes() {
		return Optional.ofNullable(this.indexableSessionAttributes).orElse(DEFAULT_INDEXABLE_SESSION_ATTRIBUTES);
	}

	/**
	 * Sets the maximum interval in seconds in which a {@link Session} can remain inactive before it expires.
	 *
	 * @param maxInactiveIntervalInSeconds integer value specifying the maximum interval in seconds
	 * that a {@link Session} can remain inactive before it expires.
	 * @see EnableGemFireHttpSession#maxInactiveIntervalInSeconds()
	 */
	public void setMaxInactiveIntervalInSeconds(int maxInactiveIntervalInSeconds) {
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	/**
	 * Gets the maximum interval in seconds in which a {@link Session} can remain inactive before it expires.
	 *
	 * @return an integer value specifying the maximum interval in seconds that a {@link Session} can remain inactive
	 * before it expires.
	 */
	protected int getMaxInactiveIntervalInSeconds() {
		return this.maxInactiveIntervalInSeconds;
	}

	/**
	 * Sets the name of the {@link Pool} used by the client {@link Region} to send {@link Session}
	 * to the cluster of servers during cache operations.
	 *
	 * @param poolName {@link String} containing the name of a {@link Pool}.
	 * @see EnableGemFireHttpSession#poolName()
	 */
	public void setPoolName(String poolName) {
		this.poolName = poolName;
	}

	/**
	 * Returns the name of the {@link Pool} used by the client {@link Region} to send {@link Session}
	 * to the cluster of servers during cache operations.
	 *
	 * @return a {@link String} containing the name of a {@link Pool}.
	 * @see org.apache.geode.cache.client.Pool#getName()
	 */
	protected String getPoolName() {
		return Optional.ofNullable(this.poolName).filter(StringUtils::hasText).orElse(DEFAULT_POOL_NAME);
	}

	/**
	 * Sets the {@link RegionShortcut} used to configure the data management policy of the {@link Cache} {@link Region}
	 * that will store {@link Session} state.
	 *
	 * Defaults to {@link RegionShortcut#PARTITION}.
	 *
	 * @param shortcut {@link RegionShortcut} used to configure the data management policy
	 * of the {@link Cache} {@link Region}.
	 * @see EnableGemFireHttpSession#serverRegionShortcut()
	 * @see org.apache.geode.cache.RegionShortcut
	 */
	public void setServerRegionShortcut(RegionShortcut shortcut) {
		this.serverRegionShortcut = shortcut;
	}

	/**
	 * Gets the {@link RegionShortcut} used to configure the data management policy of the {@link Cache} {@link Region}
	 * that will store {@link Session} state.
	 *
	 * Defaults to {@link RegionShortcut#PARTITION}.
	 *
	 * @return the {@link RegionShortcut} used to configure the data management policy
	 * of the {@link Cache} {@link Region}.
	 * @see org.apache.geode.cache.RegionShortcut
	 */
	protected RegionShortcut getServerRegionShortcut() {
		return Optional.ofNullable(this.serverRegionShortcut).orElse(DEFAULT_SERVER_REGION_SHORTCUT);
	}

	/**
	 * Sets the name of the (Client)Cache {@link Region} used to store {@link Session} state.
	 *
	 * @param sessionRegionName {@link String} specifying the name of the (Client)Cache {@link Region}
	 * used to store {@link Session} state.
	 * @see EnableGemFireHttpSession#regionName()
	 */
	public void setSessionRegionName(String sessionRegionName) {
		this.sessionRegionName = sessionRegionName;
	}

	/**
	 * Returns the name of the (Client)Cache {@link Region} used to store {@link Session} state.
	 *
	 * @return a {@link String} specifying the name of the (Client)Cache {@link Region}
	 * used to store {@link Session} state.
	 * @see org.apache.geode.cache.Region#getName()
	 */
	protected String getSessionRegionName() {
		return Optional.ofNullable(this.sessionRegionName).filter(StringUtils::hasText)
			.orElse(DEFAULT_SESSION_REGION_NAME);
	}

	/**
	 * Sets the {@link String bean name} of the Spring bean declared in the Spring application context
	 * defining the serialization strategy for serializing the {@link Session}.
	 *
	 * The serialization strategy and bean referred to by its name must be an implementation of
	 * {@link SessionSerializer}.
	 *
	 * Defaults to {@literal SessionDataSerializer}.
	 *
	 * @param sessionSerializerBeanName {@link String bean name} of the {@link SessionSerializer} used to
	 * serialize the {@link Session}.
	 * @see org.springframework.session.data.gemfire.serialization.data.provider.DataSerializableSessionSerializer
	 * @see org.springframework.session.data.gemfire.serialization.SessionSerializer
	 */
	public void setSessionSerializerBeanName(String sessionSerializerBeanName) {
		this.sessionSerializerBeanName = sessionSerializerBeanName;
	}

	/**
	 * Returns the configured {@link String bean name} of the Spring bean declared in the Spring application context
	 * defining the serialization strategy for serializing the {@link Session}.
	 *
	 * The serialization strategy and bean referred to by its name must be an implementation of
	 * {@link SessionSerializer}.
	 *
	 * Defaults to {@literal SessionDataSerializer}.
	 *
	 * @return the {@link String bean name} of the {@link SessionSerializer} used to serialize the {@link Session}.
	 * @see org.springframework.session.data.gemfire.serialization.data.provider.DataSerializableSessionSerializer
	 * @see org.springframework.session.data.gemfire.serialization.SessionSerializer
	 */
	protected String getSessionSerializerBeanName() {
		return Optional.ofNullable(this.sessionSerializerBeanName).filter(StringUtils::hasText)
			.orElse(DEFAULT_SESSION_SERIALIZER_BEAN_NAME);
	}

	/**
	 * Determine whether the configured serialization strategy is using Apache Geode / Pivotal GemFire's
	 * DataSerialization framework.
	 *
	 * @return a boolean value indicating whether the configured serialization strategy is using Apache Geode
	 * / Pivotal GemFire's DataSerialization framework.
	 * @see #getSessionSerializerBeanName()
	 */
	protected boolean isUsingDataSerialization() {
		return SESSION_DATA_SERIALIZER_BEAN_NAME.equals(getSessionSerializerBeanName());
	}

	/**
	 * Callback with the {@link AnnotationMetadata} of the class containing {@link Import @Import} annotation
	 * that imported this {@link Configuration @Configuration} class.
	 *
	 * @param importMetadata {@link AnnotationMetadata} of the application class importing
	 * this {@link Configuration} class.
	 * @see org.springframework.core.type.AnnotationMetadata
	 */
	public void setImportMetadata(AnnotationMetadata importMetadata) {

		AnnotationAttributes enableGemFireHttpSessionAttributes =
			AnnotationAttributes.fromMap(importMetadata.getAnnotationAttributes(
				EnableGemFireHttpSession.class.getName()));

		ClientRegionShortcut defaultClientRegionShortcut =
			enableGemFireHttpSessionAttributes.getEnum("clientRegionShortcut");

		setClientRegionShortcut(resolveProperty(clientRegionShortcutPropertyName(),
			ClientRegionShortcut.class, defaultClientRegionShortcut));

		String[] defaultIndexableSessionAttributes =
			enableGemFireHttpSessionAttributes.getStringArray("indexableSessionAttributes");

		setIndexableSessionAttributes(resolveProperty(indexableSessionAttributesPropertyName(),
			defaultIndexableSessionAttributes));

		Integer defaultMaxInactiveIntervalInSeconds =
			enableGemFireHttpSessionAttributes.getNumber("maxInactiveIntervalInSeconds").intValue();

		setMaxInactiveIntervalInSeconds(resolveProperty(maxInactiveIntervalInSecondsPropertyName(),
			defaultMaxInactiveIntervalInSeconds));

		String defaultPoolName = enableGemFireHttpSessionAttributes.getString("poolName");

		setPoolName(resolveProperty(poolNamePropertyName(), defaultPoolName));

		String defaultSessionRegionName = enableGemFireHttpSessionAttributes.getString("regionName");

		setSessionRegionName(resolveProperty(sessionRegionNamePropertyName(), defaultSessionRegionName));

		RegionShortcut defaultServerRegionShortcut =
			enableGemFireHttpSessionAttributes.getEnum("serverRegionShortcut");

		setServerRegionShortcut(resolveProperty(serverRegionShortcutPropertyName(),
			RegionShortcut.class, defaultServerRegionShortcut));

		String defaultSessionSerializerBeanName =
			enableGemFireHttpSessionAttributes.getString("sessionSerializerBeanName");

		setSessionSerializerBeanName(resolveProperty(sessionSerializerBeanNamePropertyName(),
			defaultSessionSerializerBeanName));

		applySpringSessionGemFireConfigurer();
	}

	private Optional<SpringSessionGemFireConfigurer> resolveSpringSessionGemFireConfigurer() {

		try {
			return Optional.of(getApplicationContext().getBean(SpringSessionGemFireConfigurer.class));
		}
		catch (BeansException cause) {

			if (isCauseBecauseNoSpringSessionGemFireConfigurerPresent(cause)) {
				return Optional.empty();
			}

			throw cause;
		}
	}

	private boolean isCauseBecauseNoSpringSessionGemFireConfigurerPresent(Exception cause) {
		return (!(cause instanceof NoUniqueBeanDefinitionException) && cause instanceof NoSuchBeanDefinitionException);
	}

	private void applySpringSessionGemFireConfigurer() {

		resolveSpringSessionGemFireConfigurer().ifPresent(configurer -> {
			setClientRegionShortcut(configurer.getClientRegionShortcut());
			setIndexableSessionAttributes(configurer.getIndexableSessionAttributes());
			setMaxInactiveIntervalInSeconds(configurer.getMaxInactiveIntervalInSeconds());
			setPoolName(configurer.getPoolName());
			setServerRegionShortcut(configurer.getServerRegionShortcut());
			setSessionRegionName(configurer.getRegionName());
			setSessionSerializerBeanName(configurer.getSessionSerializerBeanName());
		});
	}

	@PostConstruct
	public void init() {
		getBeanFactory().registerAlias(getSessionSerializerBeanName(), SESSION_SERIALIZER_BEAN_ALIAS);
	}

	@Bean
	BeanPostProcessor sessionSerializerConfigurationBeanPostProcessor() {

		return new BeanPostProcessor() {

			@Override
			public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {

				if (bean instanceof CacheFactoryBean) {

					SessionSerializer sessionSerializer = resolveSessionSerializer();

					configureSerialization((CacheFactoryBean) bean, sessionSerializer);
				}

				return bean;
			}
		};
	}

	private SessionSerializer resolveSessionSerializer() {
		return getApplicationContext().getBean(SESSION_SERIALIZER_BEAN_ALIAS, SessionSerializer.class);
	}

	private boolean isDataSerializerSessionSerializerAdapterPresent() {
		return !ArrayUtils.isEmpty(getApplicationContext()
			.getBeanNamesForType(DataSerializerSessionSerializerAdapter.class));
	}

	@SuppressWarnings("unchecked")
	private void configureSerialization(CacheFactoryBean cacheFactoryBean, SessionSerializer sessionSerializer) {

		if (sessionSerializer instanceof DataSerializer) {
			if (sessionSerializer instanceof DataSerializableSessionSerializer) {
				DataSerializableSessionSerializer.register();
			}
			else {
				DataSerializer.register(sessionSerializer.getClass());
			}
		}
		else if (sessionSerializer instanceof PdxSerializer) {
			cacheFactoryBean.setPdxSerializer(ComposablePdxSerializer.compose(
				(PdxSerializer) sessionSerializer, cacheFactoryBean.getPdxSerializer()));
		}
		else {
			Optional.ofNullable(sessionSerializer)
				.filter(it -> !isDataSerializerSessionSerializerAdapterPresent())
				.ifPresent(serializer ->
					cacheFactoryBean.setPdxSerializer(ComposablePdxSerializer.compose(
						new PdxSerializerSessionSerializerAdapter<>(sessionSerializer),
							cacheFactoryBean.getPdxSerializer()))
				);
		}
	}

	@Bean(SESSION_DATA_SERIALIZER_BEAN_NAME)
	public Object sessionDataSerializer() {
		return new DataSerializableSessionSerializer();
	}

	@Bean(SESSION_PDX_SERIALIZER_BEAN_NAME)
	public Object sessionPdxSerializer() {
		return new PdxSerializableSessionSerializer();
	}

	/**
	 * Defines the {@link Region} used to store and manage {@link Session} state in either a client-server
	 * or peer-to-peer (p2p) topology.
	 *
	 * @param gemfireCache reference to the {@link GemFireCache}.
	 * @param sessionRegionAttributes {@link RegionAttributes} used to configure the {@link Region}.
	 * @return a {@link GemFireCacheTypeAwareRegionFactoryBean} used to configure and initialize
	 * the cache {@link Region} used to store and manage {@link Session} state.
	 * @see org.apache.geode.cache.GemFireCache
	 * @see org.apache.geode.cache.RegionAttributes
	 * @see #getClientRegionShortcut()
	 * @see #getPoolName()
	 * @see #getServerRegionShortcut()
	 * @see #getSessionRegionName()
	 */
	@Bean(name = DEFAULT_SESSION_REGION_NAME)
	public GemFireCacheTypeAwareRegionFactoryBean<Object, Session> sessionRegion(GemFireCache gemfireCache,
			@Qualifier("sessionRegionAttributes") RegionAttributes<Object, Session> sessionRegionAttributes) {

		GemFireCacheTypeAwareRegionFactoryBean<Object, Session> sessionRegion =
			new GemFireCacheTypeAwareRegionFactoryBean<>();

		sessionRegion.setAttributes(sessionRegionAttributes);
		sessionRegion.setCache(gemfireCache);
		sessionRegion.setClientRegionShortcut(getClientRegionShortcut());
		sessionRegion.setPoolName(getPoolName());
		sessionRegion.setRegionName(getSessionRegionName());
		sessionRegion.setServerRegionShortcut(getServerRegionShortcut());

		return sessionRegion;
	}

	/**
	 * Defines a {@link RegionAttributes} used to configure and initialize the cache {@link Region}
	 * used to store {@link Session} state.
	 *
	 * Expiration is also configured for the {@link Region} on the basis that the cache {@link Region}
	 * is a not a proxy on either the client or server.
	 *
	 * @param gemfireCache reference to the {@link GemFireCache}.
	 * @return an instance of {@link RegionAttributes} used to configure and initialize cache {@link Region}
	 * used to store and manage {@link Session} state.
	 * @see org.springframework.data.gemfire.RegionAttributesFactoryBean
	 * @see org.apache.geode.cache.GemFireCache
	 * @see org.apache.geode.cache.PartitionAttributes
	 * @see #isExpirationAllowed(GemFireCache)
	 */
	@Bean
	@SuppressWarnings({ "unchecked", "deprecation" })
	public RegionAttributesFactoryBean sessionRegionAttributes(GemFireCache gemfireCache) {

		RegionAttributesFactoryBean regionAttributes = new RegionAttributesFactoryBean();

		regionAttributes.setKeyConstraint(SESSION_REGION_KEY_CONSTRAINT);
		regionAttributes.setValueConstraint(SESSION_REGION_VALUE_CONSTRAINT);

		if (isExpirationAllowed(gemfireCache)) {
			regionAttributes.setStatisticsEnabled(true);
			regionAttributes.setEntryIdleTimeout(
				new ExpirationAttributes(Math.max(getMaxInactiveIntervalInSeconds(), 0), ExpirationAction.INVALIDATE));
		}

		return regionAttributes;
	}

	/**
	 * Determines whether expiration configuration is allowed to be set on the cache {@link Region}
	 * used to store and manage {@link Session} state.
	 *
	 * @param gemfireCache reference to the {@link GemFireCache}.
	 * @return a boolean indicating if a {@link Region} can be configured for {@link Region} entry
	 * idle-timeout expiration.
	 * @see GemFireUtils#isClient(GemFireCache)
	 * @see GemFireUtils#isProxy(ClientRegionShortcut)
	 * @see GemFireUtils#isProxy(RegionShortcut)
	 */
	boolean isExpirationAllowed(GemFireCache gemfireCache) {

		return !(GemFireUtils.isClient(gemfireCache)
			? GemFireUtils.isProxy(getClientRegionShortcut())
			: GemFireUtils.isProxy(getServerRegionShortcut()));
	}

	/**
	 * Defines a {@link GemfireTemplate} bean used to interact with the (Client)Cache {@link Region}
	 * used to store {@link Session} state.
	 *
	 * @param gemfireCache reference to the single {@link GemFireCache} instance used by the {@link GemfireTemplate}
	 * to perform cache {@link Region} data access operations.
	 * @return a {@link GemfireTemplate} used to interact with the (Client)Cache {@link Region}
	 * used to store {@link Session} state.
	 * @see org.springframework.data.gemfire.GemfireTemplate
	 * @see org.apache.geode.cache.GemFireCache
	 * @see org.apache.geode.cache.Region
	 * @see #getSessionRegionName()
	 */
	@Bean
	@DependsOn(DEFAULT_SESSION_REGION_NAME)
	public GemfireTemplate sessionRegionTemplate(GemFireCache gemfireCache) {
		return new GemfireTemplate(gemfireCache.getRegion(getSessionRegionName()));
	}

	/**
	 * Defines the {@link SessionRepository} bean used to interact with Apache Geode or Pivotal GemFire
	 * as the Spring Session provider.
	 *
	 * @param gemfireOperations instance of {@link GemfireOperations} used to manage {@link Session} state
	 * in Apache Geode or Pivotal GemFire.
	 * @return a {@link GemFireOperationsSessionRepository} for managing (clustering/replicating) {@link Session} state
	 * in Apache Geode or Pivotal GemFire.
	 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository
	 * @see org.springframework.data.gemfire.GemfireOperations
	 */
	@Bean
	public GemFireOperationsSessionRepository sessionRepository(
		@Qualifier("sessionRegionTemplate") GemfireOperations gemfireOperations) {

		GemFireOperationsSessionRepository sessionRepository =
			new GemFireOperationsSessionRepository(gemfireOperations);

		sessionRepository.setMaxInactiveIntervalInSeconds(getMaxInactiveIntervalInSeconds());
		sessionRepository.setUseDataSerialization(isUsingDataSerialization());

		return sessionRepository;
	}

	/**
	 * Defines a Pivotal GemFire Index bean on the Pivotal GemFire cache {@link Region} storing and managing Sessions,
	 * specifically on the 'principalName' property for quick lookup of Sessions by 'principalName'.
	 *
	 * @param gemfireCache a reference to the Pivotal GemFire cache.
	 * @return a {@link IndexFactoryBean} to create an Pivotal GemFire Index on the 'principalName' property
	 * for Sessions stored in the Pivotal GemFire cache {@link Region}.
	 * @see org.springframework.data.gemfire.IndexFactoryBean
	 * @see org.apache.geode.cache.GemFireCache
	 */
	@Bean
	@DependsOn(DEFAULT_SESSION_REGION_NAME)
	public IndexFactoryBean principalNameIndex(GemFireCache gemfireCache) {

		IndexFactoryBean principalNameIndex = new IndexFactoryBean();

		principalNameIndex.setCache(gemfireCache);
		principalNameIndex.setName("principalNameIndex");
		principalNameIndex.setExpression("principalName");
		principalNameIndex.setFrom(GemFireUtils.toRegionPath(getSessionRegionName()));
		principalNameIndex.setOverride(true);
		principalNameIndex.setType(IndexType.HASH);

		return principalNameIndex;
	}

	/**
	 * Defines a Pivotal GemFire Index bean on the Pivotal GemFire cache {@link Region} storing and managing Sessions,
	 * specifically on all Session attributes for quick lookup and queries on Session attribute names
	 * with a given value.
	 *
	 * @param gemfireCache a reference to the Pivotal GemFire cache.
	 * @return a {@link IndexFactoryBean} to create an Pivotal GemFire Index on attributes of Sessions
	 * stored in the Pivotal GemFire cache {@link Region}.
	 * @see org.springframework.data.gemfire.IndexFactoryBean
	 * @see org.apache.geode.cache.GemFireCache
	 */
	@Bean
	@DependsOn(DEFAULT_SESSION_REGION_NAME)
	public SessionAttributesIndexFactoryBean sessionAttributesIndex(GemFireCache gemfireCache) {

		SessionAttributesIndexFactoryBean sessionAttributesIndex = new SessionAttributesIndexFactoryBean();

		sessionAttributesIndex.setGemFireCache(gemfireCache);
		sessionAttributesIndex.setIndexableSessionAttributes(getIndexableSessionAttributes());
		sessionAttributesIndex.setRegionName(getSessionRegionName());

		return sessionAttributesIndex;
	}
}
