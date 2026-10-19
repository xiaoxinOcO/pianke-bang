package com.campus.config;

import com.campus.mq.CampusMqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import com.campus.mq.CampusMqPublisher;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "campus.mq.enabled", havingValue = "true")
public class CampusRabbitMqConfig {

    @Bean
    MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    DirectExchange campusDirectExchange() {
        return new DirectExchange(CampusMqConstants.EXCHANGE, true, false);
    }

    @Bean
    Queue chatPushQueue() {
        return QueueBuilder.durable(CampusMqConstants.QUEUE_CHAT_PUSH).build();
    }

    @Bean
    Queue auditEventQueue() {
        return QueueBuilder.durable(CampusMqConstants.QUEUE_AUDIT_EVENT).build();
    }

    @Bean
    Queue walletLockQueue() {
        return QueueBuilder.durable(CampusMqConstants.QUEUE_WALLET_LOCK).build();
    }

    @Bean
    Queue walletRefundQueue() {
        return QueueBuilder.durable(CampusMqConstants.QUEUE_WALLET_REFUND).build();
    }

    @Bean
    Queue walletIncomeQueue() {
        return QueueBuilder.durable(CampusMqConstants.QUEUE_WALLET_INCOME).build();
    }

    @Bean
    Queue walletCompensateQueue() {
        return QueueBuilder.durable(CampusMqConstants.QUEUE_WALLET_COMPENSATE).build();
    }

    @Bean
    Binding chatPushBinding(Queue chatPushQueue, DirectExchange campusDirectExchange) {
        return BindingBuilder.bind(chatPushQueue).to(campusDirectExchange).with(CampusMqConstants.RK_TASK_STATUS);
    }

    @Bean
    Binding auditLogBinding(Queue auditEventQueue, DirectExchange campusDirectExchange) {
        return BindingBuilder.bind(auditEventQueue).to(campusDirectExchange).with(CampusMqConstants.RK_AUDIT_LOG);
    }

    @Bean
    Binding walletLockBinding(Queue walletLockQueue, DirectExchange campusDirectExchange) {
        return BindingBuilder.bind(walletLockQueue).to(campusDirectExchange).with(CampusMqConstants.RK_WALLET_LOCK);
    }

    @Bean
    Binding walletRefundBinding(Queue walletRefundQueue, DirectExchange campusDirectExchange) {
        return BindingBuilder.bind(walletRefundQueue).to(campusDirectExchange).with(CampusMqConstants.RK_WALLET_REFUND);
    }

    @Bean
    Binding walletIncomeBinding(Queue walletIncomeQueue, DirectExchange campusDirectExchange) {
        return BindingBuilder.bind(walletIncomeQueue).to(campusDirectExchange).with(CampusMqConstants.RK_WALLET_INCOME);
    }

    @Bean
    Binding walletCompensateBinding(Queue walletCompensateQueue, DirectExchange campusDirectExchange) {
        return BindingBuilder.bind(walletCompensateQueue).to(campusDirectExchange).with(CampusMqConstants.RK_WALLET_COMPENSATE);
    }

    @Bean
    CampusMqPublisher campusMqPublisher(org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate,
                                        CampusMqProperties mqProperties) {
        return new CampusMqPublisher(rabbitTemplate, mqProperties);
    }
}
