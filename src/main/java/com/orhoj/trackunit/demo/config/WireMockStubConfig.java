package com.orhoj.trackunit.demo.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
public class WireMockStubConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public WireMockServer hireManagerWireMockServer(@Value("${wiremock.port:8089}") int port) {
        return new WireMockServer(
                WireMockConfiguration.options()
                        .port(port)
                        .http2PlainDisabled(true) // Prevents Jetty h2c upgrade mismatch on localhost
                        .usingFilesUnderClasspath("wiremock")
        );
    }
}
