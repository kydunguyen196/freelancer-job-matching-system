package com.skillbridge.notification_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.skillbridge.common.events.EventTopics;

@Configuration
public class RabbitMqConfig {

    public static final String NOTIFICATION_QUEUE = "notification-service.events.queue";

    @Bean
    public TopicExchange domainEventsExchange() {
        return new TopicExchange(EventTopics.EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return new Queue(NOTIFICATION_QUEUE, true);
    }

    @Bean
    public Binding proposalCreatedBinding(Queue notificationQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(notificationQueue)
                .to(domainEventsExchange)
                .with(EventTopics.PROPOSAL_CREATED_ROUTING_KEY);
    }

    @Bean
    public Binding proposalAcceptedBinding(Queue notificationQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(notificationQueue)
                .to(domainEventsExchange)
                .with(EventTopics.PROPOSAL_ACCEPTED_ROUTING_KEY);
    }

    @Bean
    public Binding milestoneCompletedBinding(Queue notificationQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(notificationQueue)
                .to(domainEventsExchange)
                .with(EventTopics.MILESTONE_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.skillbridge.common.events");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter rabbitMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(rabbitMessageConverter);
        return factory;
    }
}
