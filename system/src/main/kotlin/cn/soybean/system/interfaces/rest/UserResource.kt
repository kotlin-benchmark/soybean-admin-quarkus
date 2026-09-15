/*
 * Copyright 2024 Soybean Admin Backend
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
@file:Suppress("ktlint:standard:comment-spacing")

package cn.soybean.system.interfaces.rest

import cn.soybean.eventsourcing.AggregateEventStore
import cn.soybean.infrastructure.config.consts.AppConstants
import cn.soybean.infrastructure.persistence.QueryBuilder
import cn.soybean.infrastructure.security.LoginHelper
import cn.soybean.interfaces.rest.dto.response.PageResult
import cn.soybean.interfaces.rest.response.ResponseEntity
import cn.soybean.shared.domain.aggregate.AggregateEventEntity
import cn.soybean.system.application.command.user.DeleteUserCommand
import cn.soybean.system.application.query.user.PageUserQuery
import cn.soybean.system.application.query.user.service.UserQueryService
import cn.soybean.system.application.service.UserService
import cn.soybean.system.interfaces.rest.dto.query.user.UserQuery
import cn.soybean.system.interfaces.rest.dto.request.ValidationGroups
import cn.soybean.system.interfaces.rest.dto.request.user.UserRequest
import cn.soybean.system.interfaces.rest.dto.request.user.toCreateUserCommand
import cn.soybean.system.interfaces.rest.dto.request.user.toUpdateUserCommand
import cn.soybean.system.interfaces.rest.dto.response.user.UserResponse
import io.quarkus.hibernate.reactive.panache.common.WithSession
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.quarkus.security.PermissionsAllowed
import io.smallrye.mutiny.Uni
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.groups.ConvertGroup
import jakarta.ws.rs.BeanParam
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.tags.Tag

@Path("/user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Users", description = "Operations related to users")
class UserResource(
    private val aggregateEventStore: AggregateEventStore,
    private val userQueryService: UserQueryService,
    private val userService: UserService,
    private val loginHelper: LoginHelper,
) {
    @PermissionsAllowed("${AppConstants.APP_PERM_ACTION_PREFIX}user.list")
    @GET
    @WithSession
    @Operation(summary = "用户列表", description = "获取用户列表")
    fun getUserList(
        @Parameter @BeanParam queryParam: UserQuery,
    ): Uni<ResponseEntity<PageResult<UserResponse>>> {
        val queryBuilder = QueryBuilder(loginHelper.getTenantId())
        queryParam.accountName?.let { queryBuilder.addLikeCondition("accountName", it) }
        queryParam.gender?.let { queryBuilder.addCondition("gender", it) }
        queryParam.nickName?.let { queryBuilder.addLikeCondition("nickName", it) }
        queryParam.phoneNumber?.let { queryBuilder.addCondition("phoneNumber", it) }
        queryParam.email?.let { queryBuilder.addCondition("email", it) }
        queryParam.status?.let { queryBuilder.addCondition("status", it) }
        val (query, params) = queryBuilder.buildParameters()
        return userQueryService
            .handle(PageUserQuery(query, params, queryParam.ofPage()))
            .map { ResponseEntity.ok(it) }
    }

    @PermissionsAllowed("${AppConstants.APP_PERM_ACTION_PREFIX}user.create")
    @POST
    @WithTransaction
    @Operation(summary = "创建用户", description = "创建用户信息")
    fun createUser(
        @Valid @ConvertGroup(to = ValidationGroups.OnCreate::class) @NotNull req: UserRequest,
    ): Uni<ResponseEntity<Boolean>> =
        userService.createUser(req.toCreateUserCommand(), loginHelper.getTenantId()).map { ResponseEntity.ok(it) }

    @PermissionsAllowed("${AppConstants.APP_PERM_ACTION_PREFIX}user.update")
    @PUT
    @WithTransaction
    @Operation(summary = "更新用户", description = "更新用户信息")
    fun updateUser(
        @Valid @ConvertGroup(to = ValidationGroups.OnUpdate::class) @NotNull req: UserRequest,
    ): Uni<ResponseEntity<Boolean>> =
        userService.updateUser(req.toUpdateUserCommand(), loginHelper.getTenantId()).map { ResponseEntity.ok(it) }

    @PermissionsAllowed("${AppConstants.APP_PERM_ACTION_PREFIX}user.delete")
    @DELETE
    @WithTransaction
    @Operation(summary = "删除用户", description = "删除用户信息")
    fun deleteUser(
        @Valid @NotEmpty(message = "{validation.delete.id.NotEmpty}") ids: Set<String>,
    ): Uni<ResponseEntity<Boolean>> =
        userService.deleteUser(DeleteUserCommand(ids), loginHelper.getTenantId()).map { (isSuccess, message) ->
            when {
                isSuccess -> ResponseEntity.ok(true)
                else -> ResponseEntity.fail(message, false)
            }
        }

    @PermissionsAllowed("${AppConstants.APP_PERM_ACTION_PREFIX}user.list")
    @GET
    @Path("/activity")
    @Operation(summary = "用户活动记录", description = "根据聚合类型查询领域事件活动记录")
    fun getUserActivity(
        //CWE-943
        //SOURCE
        @QueryParam("aggregateType") aggregateType: String,
    ): Uni<ResponseEntity<List<AggregateEventEntity>>> =
        aggregateEventStore
            .findEventsByAggregateType(aggregateType)
            .map { ResponseEntity.ok(it) }

    @jakarta.inject.Inject
    lateinit var directoryLookupClient: cn.soybean.system.infrastructure.security.DirectoryLookupClient

    @PermissionsAllowed("${AppConstants.APP_PERM_ACTION_PREFIX}user.list")
    @GET
    @Path("/directory")
    @Operation(summary = "目录查询", description = "根据账号在企业目录中查询用户")
    fun lookupDirectory(
        //CWE-90
        //SOURCE
        @QueryParam("account") account: String,
    ): Uni<ResponseEntity<List<String>>> =
        Uni.createFrom().item(ResponseEntity.ok(directoryLookupClient.findByAccount(account)))

    @jakarta.inject.Inject
    lateinit var avatarStorageService: cn.soybean.system.infrastructure.util.AvatarStorageService

    @PermissionsAllowed("${AppConstants.APP_PERM_ACTION_PREFIX}user.list")
    @GET
    @Path("/avatar")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(summary = "用户头像", description = "根据文件名读取用户头像文件")
    fun getAvatar(
        //CWE-22
        //SOURCE
        @QueryParam("fileName") fileName: String,
    ): Uni<ByteArray> =
        Uni.createFrom().item(avatarStorageService.loadAvatar(fileName))

    @PermissionsAllowed("${AppConstants.APP_PERM_ACTION_PREFIX}user.list")
    @GET
    @Path("/exportSignature")
    @Operation(summary = "导出签名", description = "为用户导出数据生成签名令牌")
    fun getExportSignature(
        @QueryParam("data") data: String,
    ): Uni<ResponseEntity<String>> =
        Uni.createFrom().item(
            ResponseEntity.ok(
                cn.soybean.system.infrastructure.util.SignUtil
                    .signExportToken(data),
            ),
        )
}
