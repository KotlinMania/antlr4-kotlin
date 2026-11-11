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

    internal var mutableDispatchMethods: MutableList<DispatchMethod>? = null
    internal var mutableInterfaces: MutableList<OutputModelObject>? = null
    internal var mutableExtensionMembers: MutableList<OutputModelObject>? = null

    internal val mutableAttrs: OrderedHashSet<Decl> = OrderedHashSet()
    internal val mutableCtorAttrs: OrderedHashSet<Decl> = OrderedHashSet()
    internal val mutableGetters: OrderedHashSet<Decl> = OrderedHashSet()

    internal var mutableTokenDecls: MutableList<Decl>? = null
    internal var mutableTokenTypeDecls: MutableList<Decl>? = null
    internal var mutableTokenListDecls: MutableList<Decl>? = null
    internal var mutableRuleContextDecls: MutableList<Decl>? = null
    internal var mutableRuleContextListDecls: MutableList<Decl>? = null
    internal var mutableAttributeDecls: MutableList<Decl>? = null

    @ModelElement
    val dispatchMethods: List<DispatchMethod>?
        get() = mutableDispatchMethods

    @ModelElement
    val interfaces: List<OutputModelObject>?
        get() = mutableInterfaces

    @ModelElement
    val extensionMembers: List<OutputModelObject>?
        get() = mutableExtensionMembers

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
        get() = mutableTokenDecls

    val tokenTypeDecls: List<Decl>?
        get() = mutableTokenTypeDecls

    val tokenListDecls: List<Decl>?
        get() = mutableTokenListDecls

    val ruleContextDecls: List<Decl>?
        get() = mutableRuleContextDecls

    val ruleContextListDecls: List<Decl>?
        get() = mutableRuleContextListDecls

    val attributeDecls: List<Decl>?
        get() = mutableAttributeDecls

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
        val methods = ArrayList<DispatchMethod>()
        if (ownerFactory.grammar.tool.genListener) {
            methods.add(ListenerDispatchMethod(ownerFactory, true))
            methods.add(ListenerDispatchMethod(ownerFactory, false))
        }
        if (ownerFactory.grammar.tool.genVisitor) {
            methods.add(VisitorDispatchMethod(ownerFactory))
        }
        mutableDispatchMethods = methods
    }

    fun addDecl(d: Decl) {
        d.ctx = this
        if (d is ContextGetterDecl) {
            mutableGetters.add(d)
        } else {
            mutableAttrs.add(d)
        }
        when (d) {
            is TokenTypeDecl -> {
                var list = mutableTokenTypeDecls
                if (list == null) {
                    list = ArrayList()
                    mutableTokenTypeDecls = list
                }
                list.add(d)
            }

            is TokenListDecl -> {
                var list = mutableTokenListDecls
                if (list == null) {
                    list = ArrayList()
                    mutableTokenListDecls = list
                }
                list.add(d)
            }

            is TokenDecl -> {
                var list = mutableTokenDecls
                if (list == null) {
                    list = ArrayList()
                    mutableTokenDecls = list
                }
                list.add(d)
            }

            is RuleContextListDecl -> {
                var list = mutableRuleContextListDecls
                if (list == null) {
                    list = ArrayList()
                    mutableRuleContextListDecls = list
                }
                list.add(d)
            }

            is RuleContextDecl -> {
                var list = mutableRuleContextDecls
                if (list == null) {
                    list = ArrayList()
                    mutableRuleContextDecls = list
                }
                list.add(d)
            }

            is AttributeDecl -> {
                var list = mutableAttributeDecls
                if (list == null) {
                    list = ArrayList()
                    mutableAttributeDecls = list
                }
                list.add(d)
            }
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
        var list = mutableInterfaces
        if (list == null) {
            list = ArrayList()
            mutableInterfaces = list
        }
        list.add(value)
    }

    fun addExtensionMember(member: OutputModelObject) {
        var list = mutableExtensionMembers
        if (list == null) {
            list = ArrayList()
            mutableExtensionMembers = list
        }
        list.add(member)
    }

    fun isEmpty(): Boolean = mutableAttrs.isEmpty()
}
