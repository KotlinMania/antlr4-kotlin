/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model.decl

import io.github.kotlinmania.antlr4.misc.OrderedHashSet
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory
import io.github.kotlinmania.antlr4.tooling.codegen.model.DispatchMethod
import io.github.kotlinmania.antlr4.tooling.codegen.model.ListenerDispatchMethod
import io.github.kotlinmania.antlr4.tooling.codegen.model.ModelElement
import io.github.kotlinmania.antlr4.tooling.codegen.model.OutputModelObject
import io.github.kotlinmania.antlr4.tooling.codegen.model.VisitorDispatchMethod
import io.github.kotlinmania.antlr4.tooling.tool.Attribute
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/** Declaration model for a generated `ParserRuleContext` subclass corresponding to a parser [Rule]. */
open class StructDecl protected constructor(
    factory: OutputModelFactory,
    r: Rule,
    resolvedName: String,
    val derivedFromName: String,
    includeRuleDispatchMethods: Boolean,
) : Decl(factory, resolvedName) {
    var provideCopyFrom: Boolean = r.hasAltSpecificContexts()

    internal val mutableDispatchMethods: MutableList<DispatchMethod> = ArrayList()
    internal val mutableInterfaces: MutableList<OutputModelObject> = ArrayList()
    internal val mutableExtensionMembers: MutableList<OutputModelObject> = ArrayList()

    internal val mutableAttrs: OrderedHashSet<Decl> = OrderedHashSet()
    internal val mutableCtorAttrs: OrderedHashSet<Decl> = OrderedHashSet()
    internal val mutableGetters: OrderedHashSet<Decl> = OrderedHashSet()

    internal val mutableTokenDecls: MutableList<Decl> = ArrayList()
    internal val mutableTokenTypeDecls: MutableList<Decl> = ArrayList()
    internal val mutableTokenListDecls: MutableList<Decl> = ArrayList()
    internal val mutableRuleContextDecls: MutableList<Decl> = ArrayList()
    internal val mutableRuleContextListDecls: MutableList<Decl> = ArrayList()
    internal val mutableAttributeDecls: MutableList<Decl> = ArrayList()

    @ModelElement
    val dispatchMethods: List<DispatchMethod>?
        get() = mutableDispatchMethods.ifEmpty { null }

    @ModelElement
    val interfaces: List<OutputModelObject>?
        get() = mutableInterfaces.ifEmpty { null }

    @ModelElement
    val extensionMembers: List<OutputModelObject>?
        get() = mutableExtensionMembers.ifEmpty { null }

    @ModelElement
    val attrs: List<Decl>
        get() = mutableAttrs.elements()

    @ModelElement
    val ctorAttrs: List<Decl>
        get() = mutableCtorAttrs.elements()

    @ModelElement
    val getters: List<Decl>
        get() = mutableGetters.elements()

    val tokenDecls: List<Decl>?
        get() = mutableTokenDecls.ifEmpty { null }

    val tokenTypeDecls: List<Decl>?
        get() = mutableTokenTypeDecls.ifEmpty { null }

    val tokenListDecls: List<Decl>?
        get() = mutableTokenListDecls.ifEmpty { null }

    val ruleContextDecls: List<Decl>?
        get() = mutableRuleContextDecls.ifEmpty { null }

    val ruleContextListDecls: List<Decl>?
        get() = mutableRuleContextListDecls.ifEmpty { null }

    val attributeDecls: List<Decl>?
        get() = mutableAttributeDecls.ifEmpty { null }

    constructor(
        factory: OutputModelFactory,
        r: Rule,
        name: String? = null,
    ) : this(
        factory = factory,
        r = r,
        resolvedName = name ?: factory.generator.target.getRuleFunctionContextStructName(r),
        derivedFromName = r.name,
        includeRuleDispatchMethods = true,
    )

    init {
        if (includeRuleDispatchMethods && !r.hasAltSpecificContexts()) {
            populateDefaultDispatchMethods(factory)
        }
    }

    internal fun populateDefaultDispatchMethods(ownerFactory: OutputModelFactory) {
        mutableDispatchMethods.clear()
        if (ownerFactory.grammar.tool.genListener) {
            mutableDispatchMethods.add(ListenerDispatchMethod(ownerFactory, true))
            mutableDispatchMethods.add(ListenerDispatchMethod(ownerFactory, false))
        }
        if (ownerFactory.grammar.tool.genVisitor) {
            mutableDispatchMethods.add(VisitorDispatchMethod(ownerFactory))
        }
    }

    fun addDecl(d: Decl) {
        d.ctx = this
        if (d is ContextGetterDecl) {
            mutableGetters.add(d)
        } else {
            mutableAttrs.add(d)
        }
        when (d) {
            is TokenTypeDecl -> mutableTokenTypeDecls.add(d)
            is TokenListDecl -> mutableTokenListDecls.add(d)
            is TokenDecl -> mutableTokenDecls.add(d)
            is RuleContextListDecl -> mutableRuleContextListDecls.add(d)
            is RuleContextDecl -> mutableRuleContextDecls.add(d)
            is AttributeDecl -> mutableAttributeDecls.add(d)
        }
    }

    fun addDecl(a: Attribute) {
        addDecl(AttributeDecl(requireNotNull(factory), a))
    }

    fun addDecls(attrList: List<Attribute>) {
        for (a in attrList) {
            addDecl(a)
        }
    }

    fun implementInterface(value: OutputModelObject) {
        mutableInterfaces.add(value)
    }

    fun addExtensionMember(member: OutputModelObject) {
        mutableExtensionMembers.add(member)
    }

    fun isEmpty(): Boolean = mutableAttrs.isEmpty()
}
