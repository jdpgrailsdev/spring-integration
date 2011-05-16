/*
 * Copyright 2002-2010 the original author or authors.
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

package org.springframework.integration.config.xml;

import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.integration.channel.NullChannel;
import org.springframework.integration.context.IntegrationContextUtils;

/**
 * A {@link BeanFactoryPostProcessor} implementation that provides default beans for the error handling and task
 * scheduling if those beans have not already been explicitly defined within the registry. It also registers a single
 * null channel with the bean name "nullChannel".
 * 
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 */
class DefaultConfiguringBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

	private static final String ERROR_LOGGER_BEAN_NAME = "_org.springframework.integration.errorLogger";

	private Log logger = LogFactory.getLog(this.getClass());

	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		if (beanFactory instanceof BeanDefinitionRegistry) {
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
			this.registerNullChannel(registry);
			this.registerErrorChannelIfNecessary(registry);
			this.registerTaskSchedulerIfNecessary(registry);
			this.registerMessageIdGenerator(registry);
		}
		else if (logger.isWarnEnabled()) {
			logger.warn("BeanFactory is not a BeanDefinitionRegistry. The default '"
					+ IntegrationContextUtils.ERROR_CHANNEL_BEAN_NAME + "' and '"
					+ IntegrationContextUtils.TASK_SCHEDULER_BEAN_NAME + "' cannot be configured.");
		}
	}
	
	private void registerMessageIdGenerator(BeanDefinitionRegistry registry){
		String listenerClassName = "org.springframework.integration.config.IdGeneratorConfigurer";
		String[] definitionNames = registry.getBeanDefinitionNames();
		for (String definitionName : definitionNames) {
			BeanDefinition definition = registry.getBeanDefinition(definitionName);
			if (listenerClassName.equals(definition.getBeanClassName())){
				logger.warn(listenerClassName + " is already registered and will be used");
				return;
			}
		}	
		BeanDefinitionReaderUtils.registerWithGeneratedName(new RootBeanDefinition(listenerClassName), registry);
	}

	/**
	 * Register a null channel in the given BeanDefinitionRegistry. The bean name is defined by the constant
	 * {@link IntegrationContextUtils#NULL_CHANNEL_BEAN_NAME}.
	 */
	private void registerNullChannel(BeanDefinitionRegistry registry) {
		if (registry.isBeanNameInUse(IntegrationContextUtils.NULL_CHANNEL_BEAN_NAME)) {
			BeanDefinition bDef = registry.getBeanDefinition(IntegrationContextUtils.NULL_CHANNEL_BEAN_NAME);
			if (bDef.getBeanClassName().equals(NullChannel.class.getName())) {
				return;
			}
			else {
				throw new IllegalStateException("The bean name '" + IntegrationContextUtils.NULL_CHANNEL_BEAN_NAME
						+ "' is reserved.");
			}
		}
		else {
			RootBeanDefinition nullChannelDef = new RootBeanDefinition();
			nullChannelDef.setBeanClassName(IntegrationNamespaceUtils.BASE_PACKAGE + ".channel.NullChannel");
			BeanDefinitionHolder nullChannelHolder = new BeanDefinitionHolder(nullChannelDef,
					IntegrationContextUtils.NULL_CHANNEL_BEAN_NAME);
			BeanDefinitionReaderUtils.registerBeanDefinition(nullChannelHolder, registry);
		}
	}

	/**
	 * Register an error channel in the given BeanDefinitionRegistry if not yet present. The bean name for which this is
	 * checking is defined by the constant {@link IntegrationContextUtils#ERROR_CHANNEL_BEAN_NAME}.
	 */
	private void registerErrorChannelIfNecessary(BeanDefinitionRegistry registry) {
		if (!registry.isBeanNameInUse(IntegrationContextUtils.ERROR_CHANNEL_BEAN_NAME)) {
			if (logger.isInfoEnabled()) {
				logger
						.info("No bean named '"
								+ IntegrationContextUtils.ERROR_CHANNEL_BEAN_NAME
								+ "' has been explicitly defined. Therefore, a default PublishSubscribeChannel will be created.");
			}
			RootBeanDefinition errorChannelDef = new RootBeanDefinition();
			errorChannelDef.setBeanClassName(IntegrationNamespaceUtils.BASE_PACKAGE
					+ ".channel.PublishSubscribeChannel");
			BeanDefinitionHolder errorChannelHolder = new BeanDefinitionHolder(errorChannelDef,
					IntegrationContextUtils.ERROR_CHANNEL_BEAN_NAME);
			BeanDefinitionReaderUtils.registerBeanDefinition(errorChannelHolder, registry);
			BeanDefinitionBuilder loggingHandlerBuilder = BeanDefinitionBuilder
					.genericBeanDefinition(IntegrationNamespaceUtils.BASE_PACKAGE + ".handler.LoggingHandler");
			loggingHandlerBuilder.addConstructorArgValue("ERROR");
			BeanDefinitionBuilder loggingEndpointBuilder = BeanDefinitionBuilder
					.genericBeanDefinition(IntegrationNamespaceUtils.BASE_PACKAGE + ".endpoint.EventDrivenConsumer");
			loggingEndpointBuilder.addConstructorArgReference(IntegrationContextUtils.ERROR_CHANNEL_BEAN_NAME);
			loggingEndpointBuilder.addConstructorArgValue(loggingHandlerBuilder.getBeanDefinition());
			BeanComponentDefinition componentDefinition = new BeanComponentDefinition(loggingEndpointBuilder
					.getBeanDefinition(), ERROR_LOGGER_BEAN_NAME);
			BeanDefinitionReaderUtils.registerBeanDefinition(componentDefinition, registry);
		}
	}

	/**
	 * Register a TaskScheduler in the given BeanDefinitionRegistry if not yet present. The bean name for which this is
	 * checking is defined by the constant {@link IntegrationContextUtils#TASK_SCHEDULER_BEAN_NAME}.
	 */
	private void registerTaskSchedulerIfNecessary(BeanDefinitionRegistry registry) {
		if (!registry.isBeanNameInUse(IntegrationContextUtils.TASK_SCHEDULER_BEAN_NAME)) {
			if (logger.isInfoEnabled()) {
				logger
						.info("No bean named '"
								+ IntegrationContextUtils.TASK_SCHEDULER_BEAN_NAME
								+ "' has been explicitly defined. Therefore, a default ThreadPoolTaskScheduler will be created.");
			}
			BeanDefinitionBuilder schedulerBuilder = BeanDefinitionBuilder
					.genericBeanDefinition("org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler");
			schedulerBuilder.addPropertyValue("poolSize", 10);
			schedulerBuilder.addPropertyValue("threadNamePrefix", "task-scheduler-");
			schedulerBuilder.addPropertyValue("rejectedExecutionHandler", new CallerRunsPolicy());
			BeanDefinitionBuilder errorHandlerBuilder = BeanDefinitionBuilder
					.genericBeanDefinition(IntegrationNamespaceUtils.BASE_PACKAGE
							+ ".channel.MessagePublishingErrorHandler");
			errorHandlerBuilder.addPropertyReference("defaultErrorChannel",
					IntegrationContextUtils.ERROR_CHANNEL_BEAN_NAME);
			schedulerBuilder.addPropertyValue("errorHandler", errorHandlerBuilder.getBeanDefinition());
			BeanComponentDefinition schedulerComponent = new BeanComponentDefinition(schedulerBuilder
					.getBeanDefinition(), IntegrationContextUtils.TASK_SCHEDULER_BEAN_NAME);
			BeanDefinitionReaderUtils.registerBeanDefinition(schedulerComponent, registry);
		}
	}

}
