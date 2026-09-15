/*
 * Copyright 2024 Soybean Admin Backend
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
@file:Suppress("ktlint:standard:comment-spacing")

package cn.soybean.system.application.bootstrap

import cn.soybean.domain.event.DomainEventPublisher
import cn.soybean.domain.system.entity.SystemApiKeyEntity
import cn.soybean.interfaces.rest.util.Ip2RegionUtil
import cn.soybean.system.application.event.ApiEndpointEvent
import cn.soybean.system.infrastructure.security.ApiKeyCache
import cn.soybean.system.infrastructure.web.ApiEndpointDynamicFeature.Companion.apiEndpoints
import com.github.yitter.contract.IdGeneratorOptions
import com.github.yitter.idgen.YitIdHelper
import io.quarkus.hibernate.reactive.panache.Panache
import io.quarkus.runtime.StartupEvent
import io.quarkus.vertx.VertxContextSupport
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.sstore.LocalSessionStore
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import java.time.LocalDateTime

@ApplicationScoped
class SystemBootStrapRecorder(
    private val eventPublisher: DomainEventPublisher,
    private val apiKeyCache: ApiKeyCache,
) {
    fun onStart(
        @Observes ev: StartupEvent,
    ) {
        initApiEndpoints()

        initApiKeyCache()

        initYitterIdGenerator()

        Ip2RegionUtil.initialize()
    }

    private fun initYitterIdGenerator() {
        val options = IdGeneratorOptions()
        YitIdHelper.setIdGenerator(options)
    }

    private fun initApiKeyCache() {
        VertxContextSupport.subscribeAndAwait {
            Panache.withSession {
                SystemApiKeyEntity.listAll().map { apiKey ->
                    apiKey.forEach {
                        apiKeyCache.set(it.apiKey, it)
                    }
                }
            }
        }
    }

    private fun initApiEndpoints() {
        val now = LocalDateTime.now()
        apiEndpoints.forEach { it.createTime = now }
        eventPublisher.publish(ApiEndpointEvent(apiEndpoints))
    }

    fun configureWebSession(
        @Observes router: Router,
        vertx: Vertx,
    ) {
        val sessionStore = LocalSessionStore.create(vertx)
        val sessionHandler = SessionHandler.create(sessionStore)
        //CWE-614
        //SINK
        sessionHandler.setCookieSecureFlag(false)
        //CWE-1004
        //SINK
        sessionHandler.setCookieHttpOnlyFlag(false)
        router.route().handler(sessionHandler)
    }
}
