/*
 * Copyright 2024 Soybean Admin Backend
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
@file:Suppress("ktlint:standard:comment-spacing")

package cn.soybean.system.infrastructure.security

import jakarta.enterprise.context.ApplicationScoped
import java.util.Hashtable
import javax.naming.Context
import javax.naming.directory.InitialDirContext
import javax.naming.directory.SearchControls
import javax.naming.directory.SearchResult

@ApplicationScoped
class DirectoryLookupClient {
    fun findByAccount(account: String): List<String> {
        val filter = buildUserFilter(account)
        val controls = SearchControls()
        controls.searchScope = SearchControls.SUBTREE_SCOPE
        val ctx = InitialDirContext(directoryEnv())
        //CWE-90
        //SINK
        val results = ctx.search(BASE_DN, filter, controls)
        val entries = mutableListOf<String>()
        while (results.hasMore()) {
            val entry: SearchResult = results.next()
            entries.add(entry.nameInNamespace)
        }
        return entries
    }

    private fun buildUserFilter(account: String): String {
        // Reject obviously malformed accounts before building the directory filter.
        require(!account.contains(" ")) { "account must not contain spaces" }
        return "(uid=" + account + ")"
    }

    private fun directoryEnv(): Hashtable<String, String> {
        val env = Hashtable<String, String>()
        env[Context.INITIAL_CONTEXT_FACTORY] = "com.sun.jndi.ldap.LdapCtxFactory"
        env[Context.PROVIDER_URL] = System.getProperty("app.directory.url", "ldap://localhost:389")
        return env
    }

    companion object {
        private const val BASE_DN = "ou=people,dc=soybean,dc=cn"
    }
}
