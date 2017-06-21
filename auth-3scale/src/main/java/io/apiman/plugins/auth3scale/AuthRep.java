/*
 * Copyright 2016 JBoss Inc
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
package io.apiman.plugins.auth3scale;

import io.apiman.gateway.engine.beans.ApiRequest;
import io.apiman.gateway.engine.beans.ApiResponse;
import io.apiman.gateway.engine.policy.IPolicyContext;
import io.apiman.gateway.engine.vertx.polling.fetchers.threescale.beans.Auth3ScaleBean;
import io.apiman.gateway.engine.vertx.polling.fetchers.threescale.beans.AuthTypeEnum;
import io.apiman.gateway.engine.vertx.polling.fetchers.threescale.beans.BackendConfiguration;
import io.apiman.gateway.engine.vertx.polling.fetchers.threescale.beans.RateLimitingStrategy;
import io.apiman.plugins.auth3scale.authrep.AbstractAuth;
import io.apiman.plugins.auth3scale.authrep.AbstractRep;
import io.apiman.plugins.auth3scale.authrep.AuthRepFactory;
import io.apiman.plugins.auth3scale.authrep.IAuthStrategyFactory;
import io.apiman.plugins.auth3scale.authrep.apikey.ApiKeyAuthRepFactory;
import io.apiman.plugins.auth3scale.authrep.appid.AppIdAuthRepFactory;
import io.apiman.plugins.auth3scale.authrep.strategies.BatchedStrategyFactory;
import io.apiman.plugins.auth3scale.authrep.strategies.StandardStrategyFactory;
import io.apiman.plugins.auth3scale.ratelimit.IAuth;
import io.apiman.plugins.auth3scale.ratelimit.IRep;
import io.apiman.plugins.auth3scale.util.report.batchedreporter.BatchedReporter;
import io.apiman.plugins.auth3scale.util.report.batchedreporter.BatchedReporterOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Marc Savy {@literal <msavy@redhat.com>}
 */
public class AuthRep {

    private Map<AuthTypeEnum, AuthRepFactory> authTypeFactory = new HashMap<>();
    private Map<RateLimitingStrategy, IAuthStrategyFactory> strategyFactoryMap = new HashMap<>();
    private BatchedReporter batchedReporter;

    public AuthRep(IPolicyContext context) {
        batchedReporter = new BatchedReporter()
                .start(context, new BatchedReporterOptions());

        // API Key
        ApiKeyAuthRepFactory apiKeyFactory = new ApiKeyAuthRepFactory();
        authTypeFactory.put(AuthTypeEnum.API_KEY, apiKeyFactory);

        // App Id
        AppIdAuthRepFactory appIdFactory = new AppIdAuthRepFactory();
        authTypeFactory.put(AuthTypeEnum.APP_ID, appIdFactory);

        // Add different RL strategies (bad name... TODO better name!).
        // Standard naive rate limiting.
        strategyFactoryMap.put(RateLimitingStrategy.STANDARD, new StandardStrategyFactory());
        strategyFactoryMap.put(RateLimitingStrategy.BATCHED_HYBRID, new BatchedStrategyFactory(batchedReporter));
    }

    public IAuth getAuth(Auth3ScaleBean config, ApiRequest request, IPolicyContext context) {
        BackendConfiguration backendConfig = getBackendConfig(config);
        AbstractAuth authStrategy = getAuthStrategy(config, request, context);
        return authTypeFactory.get(backendConfig.getAuthType())
                .createAuth(config, request, context, authStrategy);
    }

    public IRep getRep(Auth3ScaleBean config, ApiResponse response, ApiRequest request, IPolicyContext context) {
        BackendConfiguration contentConfig = config.getThreescaleConfig().getProxyConfig().getBackendConfig();
        AbstractRep repStrategy = getRepStrategy(config, request, response, context);
        return authTypeFactory.get(contentConfig.getAuthType())
                .createRep(config, response, request, context, repStrategy);
    }

    private BackendConfiguration getBackendConfig(Auth3ScaleBean config) {
        return config.getThreescaleConfig().getProxyConfig().getBackendConfig();
    }

    private AbstractAuth getAuthStrategy(Auth3ScaleBean config, ApiRequest request, IPolicyContext context) {
        return strategyFactoryMap.get(config.getRateLimitingStrategy())
                .getAuthStrategy(config, request, context);
    }

    private AbstractRep getRepStrategy(Auth3ScaleBean config, ApiRequest request, ApiResponse response, IPolicyContext context) {
        return strategyFactoryMap.get(config.getRateLimitingStrategy())
                .getRepStrategy(config, request, response, context);
    }
}