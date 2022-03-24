/*
 * Copyright 2015-present the original author or authors.
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

import static org.springframework.data.gemfire.util.RuntimeExceptionFactory.newIllegalArgumentException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

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
import org.apache.geode.cache.query.Index;
import org.apache.geode.pdx.PdxSerializer;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.data.gemfire.GemfireTemplate;
import org.springframework.data.gemfire.GemfireUtils;
import org.springframework.data.gemfire.IndexFactoryBean;
import org.springframework.data.gemfire.RegionAttributesFactoryBean;
import org.springframework.data.gemfire.config.xml.GemfireConstants;
import org.springframework.data.gemfire.util.ArrayUtils;
import org.springframework.data.gemfire.util.RegionUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;
import org.springframework.session.data.gemfire.GemFireOperationsSessionRepository;
import org.springframework.session.data.gemfire.config.annotation.web.http.support.SessionAttributesIndexFactoryBean;
import org.springframework.session.data.gemfire.config.annotation.web.http.support.SessionCacheTypeAwareRegionFactoryBean;
import org.springframework.session.data.gemfire.config.annotation.web.http.support.SpringSessionGemFireConfigurer;
import org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy;
import org.springframework.session.data.gemfire.expiration.config.SessionExpirationTimeoutAwareBeanPostProcessor;
import org.springframework.session.data.gemfire.expiration.support.SessionExpirationPolicyCustomExpiryAdapter;
import org.springframework.session.data.gemfire.serialization.SessionSerializer;
import org.springframework.session.data.gemfire.serialization.data.provider.DataSerializableSessionSerializer;
import org.springframework.session.data.gemfire.serialization.data.support.DataSerializerSessionSerializerAdapter;
import org.springframework.session.data.gemfire.serialization.pdx.provider.PdxSerializableSessionSerializer;
import org.springframework.session.data.gemfire.serialization.pdx.support.ComposablePdxSerializer;
import org.springframework.session.data.gemfire.serialization.pdx.support.PdxSerializerSessionSerializerAdapter;
import org.springframework.session.data.gemfire.support.DeltaAwareDirtyPredicate;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.session.data.gemfire.support.IsDirtyPredicate;
import org.springframework.util.StringUtils;

/**
 * The {@link GemFireHttpSessionConfiguration} class is a Spring {@link Configuration @Configuration} class
 * used to configure and initialize Pivotal GemFire/Apache Geode as a clustered, distributed and replicated
 * {@link javax.servlet.http.HttpSession} provider implementation in Spring {@link Session}.
 *
 * @author John Blum
 * @see java.time.Duration
 * @see org.apache.geode.DataSerializer
 * @see org.apache.geode.cache.Cache
 * @see org.apache.geode.cache.ExpirationAttributes
 * @see org.apache.geode.cache.GemFireCache
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.cache.RegionAttributes
 * @see org.apache.geode.cache.RegionShortcut
 * @see org.apache.geode.cache.client.ClientCache
 * @see org.apache.geode.cache.client.ClientRegionShortcut
 * @see org.apache.geode.cache.client.Pool
 * @see org.apache.geode.cache.query.Index
 * @see org.apache.geode.pdx.PdxSerializer
 * @see org.springframework.beans.factory.config.BeanPostProcessor
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.context.annotation.DependsOn
 * @see org.springframework.context.annotation.Import
 * @see org.springframework.context.annotation.ImportAware
 * @see org.springframework.context.annotation.Profile
 * @see org.springframework.core.annotation.AnnotationAttributes
 * @see org.springframework.core.env.ConfigurableEnvironment
 * @see org.springframework.core.env.Environment
 * @see org.springframework.core.env.PropertiesPropertySource
 * @see org.springframework.core.env.PropertySource
 * @see org.springframework.core.type.AnnotationMetadata
 * @see org.springframework.data.gemfire.CacheFactoryBean
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.data.gemfire.GemfireTemplate
 * @see org.springframework.data.gemfire.IndexFactoryBean
 * @see org.springframework.data.gemfire.RegionAttributesFactoryBean
 * @see org.springframework.session.Session
 * @see org.springframework.session.SessionRepository
 * @see org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration
 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.AbstractGemFireHttpSessionConfiguration
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.support.SessionAttributesIndexFactoryBean
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.support.SessionCacheTypeAwareRegionFactoryBean
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.support.SpringSessionGemFireConfigurer
 * @see org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy
 * @see org.springframework.session.data.gemfire.expiration.config.SessionExpirationTimeoutAwareBeanPostProcessor
 * @see org.springframework.session.data.gemfire.expiration.support.SessionExpirationPolicyCustomExpiryAdapter
 * @see org.springframework.session.data.gemfire.serialization.SessionSerializer
 * @see org.springframework.session.data.gemfire.serialization.data.provider.DataSerializableSessionSerializer
 * @see org.springframework.session.data.gemfire.serialization.data.support.DataSerializerSessionSerializerAdapter
 * @see org.springframework.session.data.gemfire.serialization.pdx.provider.PdxSerializableSessionSerializer
 * @see org.springframework.session.data.gemfire.serialization.pdx.support.PdxSerializerSessionSerializerAdapter
 * @since 1.1.0
 */
@Configuration
@org.springframework.context.annotation.PropertySource(name = "SpringSessionProperties",
	value = "classpath:${spring-session-properties-location:spring-session.properties}", ignoreResourceNotFound = true)
@SuppressWarnings({ "rawtypes", "unused" })
public class GemFireHttpSessionConfiguration extends AbstractGemFireHttpSessionConfiguration implements ImportAware {

	/**
	 * Default expose Spring Session using Apache Geode or Pivotal GemFire configuration as {@link Properties}
	 * in Spring's {@link Environment}.
	 */
	public static final boolean DEFAULT_EXPOSE_CONFIGURATION_AS_PROPERTIES = false;

	/**
	 * Indicates whether to employ Apache Geode/Pivotal's DataSerialization framework
	 * for {@link Session} de/serialization.
	 */
	public static final boolean DEFAULT_USE_DATA_SERIALIZATION = false;

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
	 * Default {@link IsDirtyPredicate} strategy interface used to determine whether the users' application
	 * domain objects are dirty or not.
	 */
	public static final IsDirtyPredicate DEFAULT_IS_DIRTY_PREDICATE = DeltaAwareDirtyPredicate.INSTANCE;

	/**
	 * Default {@link RegionShortcut} used to configure the data management policy of the {@link Cache} {@link Region}
	 * that will store {@link Session} state.
	 */
	public static final RegionShortcut DEFAULT_SERVER_REGION_SHORTCUT = RegionShortcut.PARTITION;

	/**
	 * {@link SpringSessionGemFireConfigurer} {@link Class Interface} {@link Method} {@link String Names}
	 */
	public static final String CONFIGURER_GET_CLIENT_REGION_SHORTCUT_METHOD_NAME =
		findByMethodName(SpringSessionGemFireConfigurer.class, "getClientRegionShortcut");

	public static final String CONFIGURER_GET_EXPOSE_CONFIGURATION_IN_PROPERTIES_METHOD_NAME =
		findByMethodName(SpringSessionGemFireConfigurer.class, "getExposeConfigurationAsProperties");

	public static final String CONFIGURER_GET_INDEXABLE_SESSION_ATTRIBUTES_METHOD_NAME =
		findByMethodName(SpringSessionGemFireConfigurer.class, "getIndexableSessionAttributes");

	public static final String CONFIGURER_GET_MAX_INACTIVE_INTERVAL_IN_SECONDS_METHOD_NAME =
		findByMethodName(SpringSessionGemFireConfigurer.class, "getMaxInactiveIntervalInSeconds");

	public static final String CONFIGURER_GET_POOL_NAME_METHOD_NAME =
		findByMethodName(SpringSessionGemFireConfigurer.class, "getPoolName");

	public static final String CONFIGURER_GET_REGION_NAME_METHOD_NAME =
		findByMethodName(SpringSessionGemFireConfigurer.class, "getRegionName");

	public static final String CONFIGURER_GET_SERVER_REGION_SHORTCUT_METHOD_NAME =
		findByMethodName(SpringSessionGemFireConfigurer.class, "getServerRegionShortcut");

	public static final String CONFIGURER_GET_SESSION_EXPIRATION_POLICY_BEAN_NAME_METHOD_NAME =
		findByMethodName(SpringSessionGemFireConfigurer.class, "getSessionExpirationPolicyBeanName");

	public static final String CONFIGURER_GET_SESSION_SERIALIZER_BEAN_NAME_METHOD_NAME =
		findByMethodName(SpringSessionGemFireConfigurer.class, "getSessionSerializerBeanName");

	/**
	 * Name of the connection {@link Pool} used by the client {@link Region} to send {@link Session} state
	 * to the cluster of  Apache Geode servers.
	 */
	public static final String DEFAULT_POOL_NAME = GemfireConstants.DEFAULT_GEMFIRE_POOL_NAME;

	/**
	 * Default name for the {@link SessionExpirationPolicy} bean.
	 */
	public static final String DEFAULT_SESSION_EXPIRATION_POLICY_BEAN_NAME = "";

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

	public static final String SPRING_SESSION_DATA_GEMFIRE_SESSION_SERIALIZER_BEAN_NAME_PROPERTY =
		"spring.session.data.gemfire.session.serializer.bean-name";

	protected static final String SPRING_SESSION_GEMFIRE_PROPERTY_SOURCE =
		GemFireHttpSessionConfiguration.class.getName().concat(".PROPERTY_SOURCE");

	/**
	 * Defaults names of all {@link Session} attributes that will be indexed by Apache Geode.
	 */
	public static final String[] DEFAULT_INDEXABLE_SESSION_ATTRIBUTES = {};

	private boolean exposeConfigurationAsProperties = DEFAULT_EXPOSE_CONFIGURATION_AS_PROPERTIES;
	private boolean usingDataSerialization = DEFAULT_USE_DATA_SERIALIZATION;

	private int maxInactiveIntervalInSeconds = DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS;

	private ClientRegionShortcut clientRegionShortcut = DEFAULT_CLIENT_REGION_SHORTCUT;

	private IsDirtyPredicate dirtyPredicate = DEFAULT_IS_DIRTY_PREDICATE;

	private RegionShortcut serverRegionShortcut = DEFAULT_SERVER_REGION_SHORTCUT;

	private String poolName = DEFAULT_POOL_NAME;

	private String sessionExpirationPolicyBeanName = DEFAULT_SESSION_EXPIRATION_POLICY_BEAN_NAME;

	private String sessionRegionName = DEFAULT_SESSION_REGION_NAME;

	private String sessionSerializerBeanName = DEFAULT_SESSION_SERIALIZER_BEAN_NAME;

	private String[] indexableSessionAttributes = DEFAULT_INDEXABLE_SESSION_ATTRIBUTES;

	private static @NonNull String findByMethodName(@NonNull Class<?> type, @NonNull String methodName) {

		return Arrays.stream(type.getDeclaredMethods())
			.map(Method::getName)
			.filter(declaredMethodName -> declaredMethodName.startsWith(methodName))
			.findFirst()
			.orElseThrow(() -> newIllegalArgumentException("No method with name [%1$s] was found on class [%2$s]",
				methodName, type.getName()));
	}

	private static Optional<String> safeFindByMethodName(@NonNull Class<?> type, @NonNull String methodName) {

		try {
			return Optional.of(findByMethodName(type, methodName));
		}
		catch (Throwable ignore) {
			return Optional.empty();
		}
	}

	private static boolean isOverriddenMethodPresent(@Nullable Object target, @Nullable String methodName) {

		return Optional.ofNullable(target)
			.map(Object::getClass)
			.flatMap(targetType -> safeFindByMethodName(targetType, methodName))
			.isPresent();
	}

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
	public ClientRegionShortcut getClientRegionShortcut() {

		return this.clientRegionShortcut != null
			? this.clientRegionShortcut
			: DEFAULT_CLIENT_REGION_SHORTCUT;
	}

	/**
	 * Sets whether to expose the configuration of Spring Session using Apache Geode or Pivotal GemFire
	 * as {@link Properties} in the Spring {@link Environment}.
	 *
	 * @param exposeConfigurationAsProperties boolean indicating whether to expose the configuration
	 * of Spring Session using Apache Geode or Pivotal GemFire as {@link Properties} in the Spring {@link Environment}.
	 *
	 * @see EnableGemFireHttpSession#exposeConfigurationAsProperties()
	 */
	public void setExposeConfigurationAsProperties(boolean exposeConfigurationAsProperties) {
		this.exposeConfigurationAsProperties = exposeConfigurationAsProperties;
	}

	/**
	 * Determines whether the configuration for Spring Session using Apache Geode or Pivotal GemFire should be exposed
	 * in the Spring {@link org.springframework.core.env.Environment} as {@link Properties}.
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
	 * or Pivotal GemFire in the Spring {@link org.springframework.core.env.Environment} as {@link Properties}.
	 */
	public boolean isExposeConfigurationAsProperties() {
		return this.exposeConfigurationAsProperties;
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
	public String[] getIndexableSessionAttributes() {

		return this.indexableSessionAttributes != null
			? this.indexableSessionAttributes
			: DEFAULT_INDEXABLE_SESSION_ATTRIBUTES;
	}

	/**
	 * Configures the {@link IsDirtyPredicate} strategy interface, as a bean from the Spring context, used to
	 * determine whether the users' application domain objects are dirty or not.
	 *
	 * @param dirtyPredicate {@link IsDirtyPredicate} strategy interface bean used to determine whether
	 * the users' application domain objects are dirty or not.
	 * @see org.springframework.session.data.gemfire.support.IsDirtyPredicate
	 */
	@Autowired(required = false)
	public void setIsDirtyPredicate(IsDirtyPredicate dirtyPredicate) {
		this.dirtyPredicate = dirtyPredicate;
	}

	/**
	 * Returns the configured {@link IsDirtyPredicate} strategy interface bean, declared in the Spring context,
	 * used to determine whether the users' application domain objects are dirty or not.
	 *
	 * Defaults to {@link GemFireHttpSessionConfiguration#DEFAULT_IS_DIRTY_PREDICATE}.
	 *
	 * @return the configured {@link IsDirtyPredicate} strategy interface bean used to determine whether
	 * the users' application domain objects are dirty or not.
	 * @see org.springframework.session.data.gemfire.support.IsDirtyPredicate
	 */
	public IsDirtyPredicate getIsDirtyPredicate() {

		return this.dirtyPredicate != null
			? this.dirtyPredicate
			: DEFAULT_IS_DIRTY_PREDICATE;
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
	public int getMaxInactiveIntervalInSeconds() {
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
	public String getPoolName() {

		return StringUtils.hasText(this.poolName)
			? this.poolName
			: DEFAULT_POOL_NAME;
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
	public RegionShortcut getServerRegionShortcut() {

		return this.serverRegionShortcut != null
			? this.serverRegionShortcut
			: DEFAULT_SERVER_REGION_SHORTCUT;
	}

	/**
	 * Sets the {@link String name} of the bean configured in the Spring application context implementing
	 * the {@link SessionExpirationPolicy} for {@link Session} expiration.
	 *
	 * @param sessionExpirationPolicyBeanName {@link String} containing the name of the bean configured in
	 * the Spring application context implementing the {@link SessionExpirationPolicy} for {@link Session} expiration.
	 */
	public void setSessionExpirationPolicyBeanName(String sessionExpirationPolicyBeanName) {
		this.sessionExpirationPolicyBeanName = sessionExpirationPolicyBeanName;
	}

	/**
	 * Returns an {@link Optional} {@link String name} of the bean configured in the Spring application context
	 * implementing the {@link SessionExpirationPolicy} for {@link Session} expiration.
	 *
	 * @return an {@link Optional} {@link String name} of the bean configured in the Spring application context
	 * implementing the {@link SessionExpirationPolicy} for {@link Session} expiration.
	 */
	public Optional<String> getSessionExpirationPolicyBeanName() {

		return Optional.ofNullable(this.sessionExpirationPolicyBeanName)
			.filter(StringUtils::hasText);
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
	public String getSessionRegionName() {

		return StringUtils.hasText(this.sessionRegionName)
			? this.sessionRegionName
			: DEFAULT_SESSION_REGION_NAME;
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
	public String getSessionSerializerBeanName() {

		return StringUtils.hasText(this.sessionSerializerBeanName)
			? this.sessionSerializerBeanName
			: DEFAULT_SESSION_SERIALIZER_BEAN_NAME;
	}

	/**
	 * Set whether to use Apache Geode / Pivotal GemFire's DataSerialization framework
	 * for {@link Session} de/serialization.
	 *
	 * @param useDataSerialization boolean value indicating whether to use Apache Geode
	 * / Pivotal GemFire's DataSerialization framework for {@link Session} de/serialization.
	 */
	private void setUseDataSerialization(boolean useDataSerialization) {
		this.usingDataSerialization = useDataSerialization;
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
		return this.usingDataSerialization || SESSION_DATA_SERIALIZER_BEAN_NAME.equals(getSessionSerializerBeanName());
	}

	/**
	 * Callback with the {@link AnnotationMetadata} of the class containing {@link Import @Import} annotation
	 * that imported this {@link Configuration @Configuration} class.
	 *
	 * The {@link Configuration @Configuration} class should also be annotated with {@link EnableGemFireHttpSession}.
	 *
	 * @param importMetadata {@link AnnotationMetadata} of the application class importing
	 * this {@link Configuration} class.
	 * @see org.springframework.core.type.AnnotationMetadata
	 * @see #applySpringSessionGemFireConfigurer()
	 * @see #exposeSpringSessionGemFireConfiguration()
	 */
	public void setImportMetadata(AnnotationMetadata importMetadata) {

		AnnotationAttributes enableGemFireHttpSessionAttributes =
			AnnotationAttributes.fromMap(importMetadata.getAnnotationAttributes(
				EnableGemFireHttpSession.class.getName()));

		// Apply configuration from {@link EnableGemFireHttpSession} annotation
		// and well-known, documented {@link Properties}.
		configureClientRegionShortcut(enableGemFireHttpSessionAttributes);
		configureExposeConfigurationAsProperties(enableGemFireHttpSessionAttributes);
		configureIndexedSessionAttributes(enableGemFireHttpSessionAttributes);
		configureMaxInactiveIntervalInSeconds(enableGemFireHttpSessionAttributes);
		configurePoolName(enableGemFireHttpSessionAttributes);
		configureServerRegionShortcut(enableGemFireHttpSessionAttributes);
		configureSessionExpirationPolicyBeanName(enableGemFireHttpSessionAttributes);
		configureSessionRegionName(enableGemFireHttpSessionAttributes);
		configureSessionSerializerBeanName(enableGemFireHttpSessionAttributes);

		// Apply configuration from {@link SpringSessionGemFireConfigurer}.
		applySpringSessionGemFireConfigurer();

		// Expose configuration as {@link Properties} in the Spring {@link Environment}
		// if {@link EnableGemFireHttpSession#exposeConfigurationAsProperties} is set to {@literal true}.
		exposeSpringSessionGemFireConfiguration();
	}

	private void configureClientRegionShortcut(AnnotationAttributes enableGemFireHttpSessionAttributes) {

		ClientRegionShortcut defaultClientRegionShortcut =
			enableGemFireHttpSessionAttributes.getEnum("clientRegionShortcut");

		setClientRegionShortcut(resolveProperty(clientRegionShortcutPropertyName(),
			ClientRegionShortcut.class, defaultClientRegionShortcut));
	}

	private void configureExposeConfigurationAsProperties(AnnotationAttributes enableGemFireHttpSessionAttributes) {

		boolean defaultExposeConfigurationAsProperties = Boolean.TRUE
			.equals(enableGemFireHttpSessionAttributes.getBoolean("exposeConfigurationAsProperties"));

		setExposeConfigurationAsProperties(resolveProperty(exposeConfigurationAsPropertiesPropertyName(),
			defaultExposeConfigurationAsProperties));
	}

	private void configureIndexedSessionAttributes(AnnotationAttributes enableGemFireHttpSessionAttributes) {

		String[] defaultIndexedSessionAttributes =
			enableGemFireHttpSessionAttributes.getStringArray("indexableSessionAttributes");

		setIndexableSessionAttributes(resolveProperty(indexedSessionAttributesPropertyName(),
			resolveProperty(indexableSessionAttributesPropertyName(), defaultIndexedSessionAttributes)));
	}

	private void configureMaxInactiveIntervalInSeconds(AnnotationAttributes enableGemFireHttpSessionAttributes) {

		Integer defaultMaxInactiveIntervalInSeconds =
			enableGemFireHttpSessionAttributes.getNumber("maxInactiveIntervalInSeconds").intValue();

		setMaxInactiveIntervalInSeconds(resolveProperty(maxInactiveIntervalInSecondsPropertyName(),
			defaultMaxInactiveIntervalInSeconds));
	}

	private void configurePoolName(AnnotationAttributes enableGemFireHttpSessionAttributes) {

		String defaultPoolName = enableGemFireHttpSessionAttributes.getString("poolName");

		setPoolName(resolveProperty(poolNamePropertyName(), defaultPoolName));
	}

	private void configureServerRegionShortcut(AnnotationAttributes enableGemFireHttpSessionAttributes) {

		RegionShortcut defaultServerRegionShortcut =
			enableGemFireHttpSessionAttributes.getEnum("serverRegionShortcut");

		setServerRegionShortcut(resolveProperty(serverRegionShortcutPropertyName(), RegionShortcut.class,
			defaultServerRegionShortcut));
	}

	private void configureSessionExpirationPolicyBeanName(AnnotationAttributes enableGemFireHttpSessionAttributes) {

		String defaultSessionExpirationPolicyBeanName =
			enableGemFireHttpSessionAttributes.getString("sessionExpirationPolicyBeanName");

		setSessionExpirationPolicyBeanName(resolveProperty(sessionExpirationPolicyBeanNamePropertyName(),
			defaultSessionExpirationPolicyBeanName));
	}

	private void configureSessionRegionName(AnnotationAttributes enableGemFireHttpSessionAttributes) {

		String defaultSessionRegionName = enableGemFireHttpSessionAttributes.getString("regionName");

		setSessionRegionName(resolveProperty(sessionRegionNamePropertyName(), defaultSessionRegionName));
	}

	private void configureSessionSerializerBeanName(AnnotationAttributes enableGemFireHttpSessionAttributes) {

		String defaultSessionSerializerBeanName =
			enableGemFireHttpSessionAttributes.getString("sessionSerializerBeanName");

		setSessionSerializerBeanName(resolveProperty(sessionSerializerBeanNamePropertyName(),
			defaultSessionSerializerBeanName));
	}

	/**
	 * Applies configuration from a single {@link SpringSessionGemFireConfigurer} bean
	 * declared in the Spring {@link ApplicationContext}.
	 *
	 * @see org.springframework.session.data.gemfire.config.annotation.web.http.support.SpringSessionGemFireConfigurer
	 * @see #resolveSpringSessionGemFireConfigurer()
	 */
	void applySpringSessionGemFireConfigurer() {

		resolveSpringSessionGemFireConfigurer()
			.map(this::applyClientRegionShortcut)
			.map(this::applyExposeConfigurationAsProperties)
			.map(this::applyIndexableSessionAttributes)
			.map(this::applyMaxInactiveIntervalInSeconds)
			.map(this::applyPoolName)
			.map(this::applyServerRegionShortcut)
			.map(this::applySessionExpirationPolicyBeanName)
			.map(this::applySessionRegionName)
			.map(this::applySessionSerializerBeanName);
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

	private <T> SpringSessionGemFireConfigurer applySpringSessionGemFireConfigurerConfiguration(
			@Nullable SpringSessionGemFireConfigurer configurer, @NonNull String methodName,
			@NonNull Function<SpringSessionGemFireConfigurer, T> getter, @NonNull Consumer<T> setter) {

		Optional.ofNullable(configurer)
			.filter(it -> isOverriddenMethodPresent(configurer, methodName))
			.map(getter)
			.ifPresent(setter);

		return configurer;
	}

	private SpringSessionGemFireConfigurer applyClientRegionShortcut(SpringSessionGemFireConfigurer configurer) {

		return applySpringSessionGemFireConfigurerConfiguration(configurer,
			CONFIGURER_GET_CLIENT_REGION_SHORTCUT_METHOD_NAME,
				SpringSessionGemFireConfigurer::getClientRegionShortcut, this::setClientRegionShortcut);
	}

	private SpringSessionGemFireConfigurer applyExposeConfigurationAsProperties(SpringSessionGemFireConfigurer configurer) {

		return applySpringSessionGemFireConfigurerConfiguration(configurer,
			CONFIGURER_GET_EXPOSE_CONFIGURATION_IN_PROPERTIES_METHOD_NAME,
				SpringSessionGemFireConfigurer::getExposeConfigurationAsProperties, this::setExposeConfigurationAsProperties);
	}

	private SpringSessionGemFireConfigurer applyIndexableSessionAttributes(SpringSessionGemFireConfigurer configurer) {

		return applySpringSessionGemFireConfigurerConfiguration(configurer,
			CONFIGURER_GET_INDEXABLE_SESSION_ATTRIBUTES_METHOD_NAME,
				SpringSessionGemFireConfigurer::getIndexableSessionAttributes, this::setIndexableSessionAttributes);
	}

	private SpringSessionGemFireConfigurer applyMaxInactiveIntervalInSeconds(SpringSessionGemFireConfigurer configurer) {

		return applySpringSessionGemFireConfigurerConfiguration(configurer,
			CONFIGURER_GET_MAX_INACTIVE_INTERVAL_IN_SECONDS_METHOD_NAME,
				SpringSessionGemFireConfigurer::getMaxInactiveIntervalInSeconds, this::setMaxInactiveIntervalInSeconds);
	}

	private SpringSessionGemFireConfigurer applyPoolName(SpringSessionGemFireConfigurer configurer) {

		return applySpringSessionGemFireConfigurerConfiguration(configurer,
			CONFIGURER_GET_POOL_NAME_METHOD_NAME,
				SpringSessionGemFireConfigurer::getPoolName, this::setPoolName);
	}

	private SpringSessionGemFireConfigurer applyServerRegionShortcut(SpringSessionGemFireConfigurer configurer) {

		return applySpringSessionGemFireConfigurerConfiguration(configurer,
			CONFIGURER_GET_SERVER_REGION_SHORTCUT_METHOD_NAME,
				SpringSessionGemFireConfigurer::getServerRegionShortcut, this::setServerRegionShortcut);
	}

	private SpringSessionGemFireConfigurer applySessionExpirationPolicyBeanName(SpringSessionGemFireConfigurer configurer) {

		return applySpringSessionGemFireConfigurerConfiguration(configurer,
			CONFIGURER_GET_SESSION_EXPIRATION_POLICY_BEAN_NAME_METHOD_NAME,
				SpringSessionGemFireConfigurer::getSessionExpirationPolicyBeanName, this::setSessionExpirationPolicyBeanName);
	}

	private SpringSessionGemFireConfigurer applySessionRegionName(SpringSessionGemFireConfigurer configurer) {

		return applySpringSessionGemFireConfigurerConfiguration(configurer,
			CONFIGURER_GET_REGION_NAME_METHOD_NAME,
				SpringSessionGemFireConfigurer::getRegionName, this::setSessionRegionName);
	}

	private SpringSessionGemFireConfigurer applySessionSerializerBeanName(SpringSessionGemFireConfigurer configurer) {

		return applySpringSessionGemFireConfigurerConfiguration(configurer,
			CONFIGURER_GET_SESSION_SERIALIZER_BEAN_NAME_METHOD_NAME,
				SpringSessionGemFireConfigurer::getSessionSerializerBeanName, this::setSessionSerializerBeanName);
	}

	/**
	 * Exposes the configuration of Spring Session using either Apache Geode or Pivotal GemFire as {@link Properties}
	 * in the Spring {@link Environment}.
	 *
	 * @see #isExposeConfigurationAsProperties()
	 */
	void exposeSpringSessionGemFireConfiguration() {

		if (isExposeConfigurationAsProperties()) {

			Optional.ofNullable(getEnvironment())
				.filter(ConfigurableEnvironment.class::isInstance)
				.map(ConfigurableEnvironment.class::cast)
				.map(ConfigurableEnvironment::getPropertySources)
				.map(propertySources -> {

					Properties springSessionGemFireProperties = new Properties();

					PropertySource springSessionGemFirePropertySource =
						new PropertiesPropertySource(SPRING_SESSION_GEMFIRE_PROPERTY_SOURCE,
							springSessionGemFireProperties);

					propertySources.addFirst(springSessionGemFirePropertySource);

					return springSessionGemFireProperties;
				})
				.ifPresent(properties -> {

					properties.setProperty(clientRegionShortcutPropertyName(),
						getClientRegionShortcut().name());

					properties.setProperty(exposeConfigurationAsPropertiesPropertyName(),
						String.valueOf(isExposeConfigurationAsProperties()));

					// TODO: deprecate and remove indexableSessionAttributes
					properties.setProperty(indexableSessionAttributesPropertyName(),
						StringUtils.arrayToCommaDelimitedString(getIndexableSessionAttributes()));

					properties.setProperty(indexedSessionAttributesPropertyName(),
						StringUtils.arrayToCommaDelimitedString(getIndexableSessionAttributes()));

					properties.setProperty(maxInactiveIntervalInSecondsPropertyName(),
						String.valueOf(getMaxInactiveIntervalInSeconds()));

					properties.setProperty(poolNamePropertyName(), getPoolName());

					properties.setProperty(sessionRegionNamePropertyName(), getSessionRegionName());

					properties.setProperty(serverRegionShortcutPropertyName(),
						getServerRegionShortcut().name());

					getSessionExpirationPolicyBeanName()
						.ifPresent(it -> properties.setProperty(sessionExpirationPolicyBeanNamePropertyName(), it));

					properties.setProperty(sessionSerializerBeanNamePropertyName(), getSessionSerializerBeanName());

				});
		}
	}

	@PostConstruct
	public void initGemFire() {
		getBeanFactory().registerAlias(getSessionSerializerBeanName(), SESSION_SERIALIZER_BEAN_ALIAS);
	}

	@Bean
	BeanPostProcessor sessionExpirationTimeoutAwareBeanPostProcessor() {

		Duration expirationTimeout = Duration.ofSeconds(getMaxInactiveIntervalInSeconds());

		return new SessionExpirationTimeoutAwareBeanPostProcessor(expirationTimeout);
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

	private Optional<SessionExpirationPolicy> resolveSessionExpirationPolicy() {

		Optional<String> sessionExpirationPolicyBeanName = getSessionExpirationPolicyBeanName();

		if (sessionExpirationPolicyBeanName.isPresent()) {
			if (getApplicationContext().containsBean(sessionExpirationPolicyBeanName.get())) {
				return Optional.of(getApplicationContext()
					.getBean(sessionExpirationPolicyBeanName.get(), SessionExpirationPolicy.class));
			}
			else {

				String logMessage = "No Bean with name [{}] and type [{}] was configured;"
					+ " Defaulting to Expiration policy configured for Region [{}]";

				getLogger().warn(logMessage, sessionExpirationPolicyBeanName.get(),
					SessionExpirationPolicy.class.getName(), getSessionRegionName());
			}
		}

		return Optional.empty();
	}

	private SessionSerializer resolveSessionSerializer() {
		return getApplicationContext().getBean(SESSION_SERIALIZER_BEAN_ALIAS, SessionSerializer.class);
	}

	private boolean isDataSerializerSessionSerializerAdapterPresent() {

		String[] beanNames =
			getApplicationContext().getBeanNamesForType(DataSerializerSessionSerializerAdapter.class);

		return !ArrayUtils.isEmpty(beanNames);
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

			setUseDataSerialization(true);
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

	/**
	 * {@link SessionSerializer} bean implemented with Apache Geode/Pivotal GemFire DataSerialization framework.
	 *
	 * @return a DataSerialization {@link SessionSerializer} implementation.
	 * @see org.springframework.session.data.gemfire.serialization.data.provider.DataSerializableSessionSerializer
	 * @see org.springframework.session.data.gemfire.serialization.SessionSerializer
	 */
	@Bean(SESSION_DATA_SERIALIZER_BEAN_NAME)
	public Object sessionDataSerializer() {
		return new DataSerializableSessionSerializer();
	}

	/**
	 * {@link SessionSerializer} bean implemented with Apache Geode/Pivotal GemFire PDX serialization framework.
	 *
	 * @return a PDX serialization {@link SessionSerializer} implementation.
	 * @see org.springframework.session.data.gemfire.serialization.pdx.provider.PdxSerializableSessionSerializer
	 * @see org.springframework.session.data.gemfire.serialization.SessionSerializer
	 */
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
	 * @return a {@link SessionCacheTypeAwareRegionFactoryBean} used to configure and initialize
	 * the cache {@link Region} used to store and manage {@link Session} state.
	 * @see org.apache.geode.cache.GemFireCache
	 * @see org.apache.geode.cache.RegionAttributes
	 * @see #getClientRegionShortcut()
	 * @see #getPoolName()
	 * @see #getServerRegionShortcut()
	 * @see #getSessionRegionName()
	 */
	@Bean(name = DEFAULT_SESSION_REGION_NAME)
	public SessionCacheTypeAwareRegionFactoryBean<Object, Session> sessionRegion(GemFireCache gemfireCache,
			@Qualifier("sessionRegionAttributes") RegionAttributes<Object, Session> sessionRegionAttributes) {

		SessionCacheTypeAwareRegionFactoryBean<Object, Session> sessionRegion =
			new SessionCacheTypeAwareRegionFactoryBean<>();

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
	@SuppressWarnings({ "unchecked" })
	public RegionAttributesFactoryBean sessionRegionAttributes(GemFireCache gemfireCache) {

		RegionAttributesFactoryBean regionAttributes = new RegionAttributesFactoryBean();

		regionAttributes.setKeyConstraint(SESSION_REGION_KEY_CONSTRAINT);
		regionAttributes.setValueConstraint(SESSION_REGION_VALUE_CONSTRAINT);

		if (isExpirationAllowed(gemfireCache)) {

			regionAttributes.setStatisticsEnabled(true);

			regionAttributes.setEntryIdleTimeout(new ExpirationAttributes(
				Math.max(getMaxInactiveIntervalInSeconds(), 0), ExpirationAction.INVALIDATE));

			resolveSessionExpirationPolicy()
				.map(SessionExpirationPolicyCustomExpiryAdapter::new)
				.ifPresent(regionAttributes::setCustomEntryIdleTimeout);
		}
		else {
			getLogger().info("Expiration is not allowed on Regions with a data management policy of {}",
				GemfireUtils.isClient(gemfireCache) ? getClientRegionShortcut() : getServerRegionShortcut());
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

		sessionRepository.setIsDirtyPredicate(getIsDirtyPredicate());
		sessionRepository.setMaxInactiveIntervalInSeconds(getMaxInactiveIntervalInSeconds());
		sessionRepository.setUseDataSerialization(isUsingDataSerialization());

		return sessionRepository;
	}

	/**
	 * Defines an OQL {@link Index} bean on the {@link GemFireCache} {@link Region} storing and managing
	 * {@link Session Sessions}, specifically on the {@literal principalName} property for quick lookup
	 * of {@link Session Sessions} by {@literal principalName}.
	 *
	 * @param gemfireCache reference to the {@link GemFireCache}.
	 * @return a {@link IndexFactoryBean} to create an OQL {@link Index} on the {@literal principalName} property
	 * for {@link Session Sessions} stored in the {@link GemFireCache} {@link Region}.
	 * @see org.springframework.data.gemfire.IndexFactoryBean
	 * @see org.apache.geode.cache.GemFireCache
	 */
	@Bean
	@DependsOn(DEFAULT_SESSION_REGION_NAME)
	@Profile("!disable-spring-session-data-gemfire-indexes")
	public IndexFactoryBean principalNameIndex(GemFireCache gemfireCache) {

		IndexFactoryBean principalNameIndex = new IndexFactoryBean();

		principalNameIndex.setCache(gemfireCache);
		principalNameIndex.setName("principalNameIndex");
		principalNameIndex.setExpression("principalName");
		principalNameIndex.setFrom(RegionUtils.toRegionPath(getSessionRegionName()));
		principalNameIndex.setOverride(true);

		return principalNameIndex;
	}

	/**
	 * Defines an OQL {@link Index} bean on the {@link GemFireCache} {@link Region} storing and managing
	 * {@link Session Sessions}, specifically on all {@link Session} attributes for quick lookup and queries
	 * on {@link Session} attribute {@link String names} with a given {@link Object value}.
	 *
	 * @param gemfireCache reference to the {@link GemFireCache}.
	 * @return a {@link IndexFactoryBean} to create an OQL {@link Index} on attributes of {@link Session Sessions}
	 * stored in the {@link GemFireCache} {@link Region}.
	 * @see org.springframework.data.gemfire.IndexFactoryBean
	 * @see org.apache.geode.cache.GemFireCache
	 */
	@Bean
	@DependsOn(DEFAULT_SESSION_REGION_NAME)
	@Profile("!disable-spring-session-data-gemfire-indexes")
	public SessionAttributesIndexFactoryBean sessionAttributesIndex(GemFireCache gemfireCache) {

		SessionAttributesIndexFactoryBean sessionAttributesIndex = new SessionAttributesIndexFactoryBean();

		sessionAttributesIndex.setGemFireCache(gemfireCache);
		sessionAttributesIndex.setIndexableSessionAttributes(getIndexableSessionAttributes());
		sessionAttributesIndex.setRegionName(getSessionRegionName());

		return sessionAttributesIndex;
	}
}
