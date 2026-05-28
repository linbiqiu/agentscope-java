package com.company.feishuagent.feishu.config;

import com.lark.oapi.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeishuOpenApiClientConfig {

    @Bean
    public Client feishuOpenApiClient(
            @Value("${feishu.websocket.app-id:}") String appId,
            @Value("${feishu.websocket.app-secret:}") String appSecret) {
        return Client.newBuilder(appId, appSecret).build();
    }
}
