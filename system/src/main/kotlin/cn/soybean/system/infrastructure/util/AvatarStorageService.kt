/*
 * Copyright 2024 Soybean Admin Backend
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
@file:Suppress("ktlint:standard:comment-spacing")

package cn.soybean.system.infrastructure.util

import jakarta.enterprise.context.ApplicationScoped
import java.io.File

@ApplicationScoped
class AvatarStorageService {
    fun loadAvatar(fileName: String): ByteArray {
        val target = resolveAvatarPath(fileName)
        //CWE-22
        //SINK
        return target.readBytes()
    }

    private fun resolveAvatarPath(fileName: String): File {
        require(fileName.isNotBlank()) { "fileName must not be blank" }
        return File(avatarBaseDir(), fileName)
    }

    private fun avatarBaseDir(): String = System.getProperty("app.avatar.dir", "/var/soybean/avatars")
}
