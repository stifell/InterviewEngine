package com.interviewengine.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Настраивает HTTP/HTTPS-прокси для исходящих запросов (Gemini API и т.д.).
 *
 * <p>Включается через {@code proxy.enabled: true} в application-local.yaml.
 * Выставляет JVM system properties до инициализации Spring AI клиентов:
 * {@code https.proxyHost / https.proxyPort / http.proxyHost / http.proxyPort}.
 *
 * <p>Локальные адреса (127.*, localhost) автоматически исключаются JVM по умолчанию
 * (java.net.nonProxyHosts), поэтому вызовы к sidecar на 127.0.0.1 не идут через прокси.
 */
@Configuration
@ConditionalOnProperty(name = "proxy.enabled", havingValue = "true")
@ConfigurationProperties(prefix = "proxy")
public class HttpProxyConfiguration {

    private static final Logger log = LoggerFactory.getLogger(HttpProxyConfiguration.class);

    private String host = "127.0.0.1";
    private int port = 10801;

    @PostConstruct
    void applyProxy() {
        System.setProperty("https.proxyHost", host);
        System.setProperty("https.proxyPort", String.valueOf(port));
        System.setProperty("http.proxyHost", host);
        System.setProperty("http.proxyPort", String.valueOf(port));
        // Исключаем localhost — иначе sidecar-вызовы тоже пойдут через прокси
        System.setProperty("http.nonProxyHosts", "localhost|127.*|[::1]");
        log.info("HTTP/HTTPS прокси: {}:{}", host, port);
    }

    public void setHost(String host) { this.host = host; }
    public void setPort(int port)    { this.port = port; }
    public String getHost()          { return host; }
    public int getPort()             { return port; }
}
