/*
 * Copyright 2024 Soybean Admin Backend
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
@file:Suppress("ktlint:standard:comment-spacing")

package cn.soybean.system.infrastructure.localization

import jakarta.enterprise.context.ApplicationScoped
import org.w3c.dom.Document
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory

@ApplicationScoped
class DataDictionaryService {
    private val catalog: Document = loadCatalog()

    fun resolveLabel(
        dictType: String,
        itemCode: String,
    ): String {
        val expression = buildItemExpression(dictType, itemCode)
        val xpath = XPathFactory.newInstance().newXPath()
        //CWE-643
        //SINK
        return xpath.evaluate(expression, catalog)
    }

    private fun buildItemExpression(
        dictType: String,
        itemCode: String,
    ): String {
        val predicates = mutableListOf<String>()
        predicates.add("@type='" + dictType + "'")
        predicates.add("@code='" + itemCode + "'")
        return "/dictionary/item[" + predicates.joinToString(" and ") + "]/@label"
    }

    private fun loadCatalog(): Document {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val stream =
            this::class.java.classLoader.getResourceAsStream("dict/data-dictionary.xml")
                ?: error("dict/data-dictionary.xml not found on the classpath")
        return stream.use { builder.parse(it) }
    }
}
