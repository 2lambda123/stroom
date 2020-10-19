/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.spring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import stroom.node.server.StroomPropertyService;
import stroom.security.SecurityContext;
import stroom.security.server.AuthenticationService;
import stroom.security.server.AuthenticationServiceClients;
import stroom.security.server.ContentSecurityConfig;
import stroom.security.server.ContentSecurityFilter;
import stroom.security.server.JWTService;
import stroom.security.server.SecurityConfig;
import stroom.security.server.SecurityFilter;

/**
 * The authentication providers are configured manually because the method
 * signature of the
 *
 * @Override configure() method doesn't allow us to pass the @Components we need
 * to.
 */

/**
 * Exclude other configurations that might be found accidentally during a
 * component scan as configurations should be specified explicitly.
 */
@Configuration
@ComponentScan(basePackages = {"stroom.security.server"}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class),})
public class SecurityConfiguration {
    public static final String PROD_SECURITY = "PROD_SECURITY";
    public static final String MOCK_SECURITY = "MOCK_SECURITY";

    @Bean(name = "securityConfig")
    public SecurityConfig securityConfig(final StroomPropertyService stroomPropertyService) {
        final SecurityConfig securityConfig = new SecurityConfig();
        securityConfig.setAuthenticationServiceUrl(stroomPropertyService.getProperty("stroom.auth.authentication.service.url"));
        securityConfig.setAdvertisedStroomUrl(stroomPropertyService.getProperty("stroom.advertisedUrl"));
        securityConfig.setAuthenticationRequired(stroomPropertyService.getBooleanProperty("stroom.authentication.required", true));
        securityConfig.setClientId(stroomPropertyService.getProperty("stroom.auth.clientId"));
        securityConfig.setClientSecret(stroomPropertyService.getProperty("stroom.auth.clientSecret"));
        return securityConfig;
    }

    @Bean(name = "securityFilter")
    public SecurityFilter securityFilter(
            final SecurityConfig securityConfig,
            final JWTService jwtService,
            final AuthenticationServiceClients authenticationServiceClients,
            final AuthenticationService authenticationService,
            final SecurityContext securityContext) {
        return new SecurityFilter(
                securityConfig,
                jwtService,
                authenticationServiceClients,
                authenticationService,
                securityContext);
    }

    @Bean(name = "contentSecurityConfig")
    public ContentSecurityConfig contentSecurityConfig(final StroomPropertyService stroomPropertyService) {
        final ContentSecurityConfig config = new ContentSecurityConfig();
        config.setContentSecurityPolicy(stroomPropertyService.getProperty("stroom.security.web.content.securityPolicy"));
        config.setContentTypeOptions(stroomPropertyService.getProperty("stroom.security.web.content.typeOptions"));
        config.setFrameOptions(stroomPropertyService.getProperty("stroom.security.web.content.frameOptions"));
        config.setXssProtection(stroomPropertyService.getProperty("stroom.security.web.content.xssProtection"));
        return config;
    }

    @Bean(name = "contentSecurityFilter")
    public ContentSecurityFilter contentSecurityFilter(
            final ContentSecurityConfig contentSecurityConfig) {
        return new ContentSecurityFilter(contentSecurityConfig);
    }
}