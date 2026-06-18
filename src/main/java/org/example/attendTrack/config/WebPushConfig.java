package org.example.garant.config;

import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.Security;

@Configuration
public class WebPushConfig {

    /**
     * PushService is only created when VAPID keys are configured.
     * If keys are absent, push notifications are silently skipped.
     */
    @Bean
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${vapid.public-key:}')")
    public PushService pushService(
            @Value("${vapid.public-key}") String publicKey,
            @Value("${vapid.private-key}") String privateKey,
            @Value("${vapid.subject}") String subject) throws Exception {

        Security.addProvider(new BouncyCastleProvider());
        return new PushService(publicKey, privateKey, subject);
    }
}
