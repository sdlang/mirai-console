/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.console.command.descriptor

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.descriptor.AbstractCommandValueParameter.UserDefinedType.Companion.createOptional
import net.mamoe.mirai.console.command.descriptor.AbstractCommandValueParameter.UserDefinedType.Companion.createRequired
import net.mamoe.mirai.console.command.descriptor.ArgumentAcceptance.Companion.isAcceptable
import net.mamoe.mirai.console.command.parse.CommandValueArgument
import net.mamoe.mirai.console.command.resolve.ResolvedCommandCall
import net.mamoe.mirai.console.internal.data.classifierAsKClass
import net.mamoe.mirai.console.internal.data.classifierAsKClassOrNull
import net.mamoe.mirai.console.internal.data.typeOf0
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf

/**
 * 指令签名. 表示指令定义的需要的参数.
 *
 * @see AbstractCommandSignature
 */
@ExperimentalCommandDescriptors
public interface CommandSignature {
    /**
     * 接收者参数, 为 [CommandSender] 子类
     */
    @ConsoleExperimentalApi
    public val receiverParameter: CommandReceiverParameter<out CommandSender>?

    /**
     * 形式 值参数.
     */
    public val valueParameters: List<AbstractCommandValueParameter<*>>

    /**
     * 调用这个指令.
     */
    public suspend fun call(resolvedCommandCall: ResolvedCommandCall)
}

/**
 * 来自 [KFunction] 反射得到的 [CommandSignature]
 *
 * @see CommandSignatureFromKFunctionImpl
 */
@ConsoleExperimentalApi
@ExperimentalCommandDescriptors
public interface CommandSignatureFromKFunction : CommandSignature {
    public val originFunction: KFunction<*>
}

/**
 * @see CommandSignatureImpl
 * @see CommandSignatureFromKFunctionImpl
 */
@ExperimentalCommandDescriptors
public abstract class AbstractCommandSignature : CommandSignature {
    override fun toString(): String {
        val receiverParameter = receiverParameter
        return if (receiverParameter == null) {
            "CommandSignatureVariant(${valueParameters.joinToString()})"
        } else {
            "CommandSignatureVariant($receiverParameter, ${valueParameters.joinToString()})"
        }
    }
}

@ExperimentalCommandDescriptors
public open class CommandSignatureImpl(
    override val receiverParameter: CommandReceiverParameter<out CommandSender>?,
    override val valueParameters: List<AbstractCommandValueParameter<*>>,
    private val onCall: suspend CommandSignatureImpl.(resolvedCommandCall: ResolvedCommandCall) -> Unit,
) : CommandSignature, AbstractCommandSignature() {
    override suspend fun call(resolvedCommandCall: ResolvedCommandCall) {
        return onCall(resolvedCommandCall)
    }
}

@ConsoleExperimentalApi
@ExperimentalCommandDescriptors
public open class CommandSignatureFromKFunctionImpl(
    override val receiverParameter: CommandReceiverParameter<out CommandSender>?,
    override val valueParameters: List<AbstractCommandValueParameter<*>>,
    override val originFunction: KFunction<*>,
    private val onCall: suspend CommandSignatureFromKFunctionImpl.(resolvedCommandCall: ResolvedCommandCall) -> Unit,
) : CommandSignatureFromKFunction, AbstractCommandSignature() {
    override suspend fun call(resolvedCommandCall: ResolvedCommandCall) {
        return onCall(resolvedCommandCall)
    }
}


/**
 * Inherited instances must be [CommandValueParameter] or [CommandReceiverParameter]
 */
@ExperimentalCommandDescriptors
public interface CommandParameter<T : Any?> {
    public val name: String?

    public val isOptional: Boolean

    /**
     * Reified type of [T]
     */
    public val type: KType
}

@ExperimentalCommandDescriptors
public abstract class AbstractCommandParameter<T> : CommandParameter<T> {
    override fun toString(): String = buildString {
        append(name)
        append(": ")
        append(type.classifierAsKClass().simpleName)
        append(if (type.isMarkedNullable) "?" else "")
    }
}

/**
 * Inherited instances must be [AbstractCommandValueParameter]
 */
@ExperimentalCommandDescriptors
public interface CommandValueParameter<T : Any?> : CommandParameter<T> {

    public val isVararg: Boolean

    public fun accepts(argument: CommandValueArgument, commandArgumentContext: CommandArgumentContext?): Boolean =
        accepting(argument, commandArgumentContext).isAcceptable

    public fun accepting(argument: CommandValueArgument, commandArgumentContext: CommandArgumentContext?): ArgumentAcceptance
}

@ExperimentalCommandDescriptors
public sealed class ArgumentAcceptance(
    /**
     * Higher means more acceptable
     */
    @ConsoleExperimentalApi
    public val acceptanceLevel: Int,
) {
    public object Direct : ArgumentAcceptance(Int.MAX_VALUE)

    public class WithTypeConversion(
        public val typeVariant: TypeVariant<*>,
    ) : ArgumentAcceptance(20)

    public class WithContextualConversion(
        public val parser: CommandValueArgumentParser<*>,
    ) : ArgumentAcceptance(10)

    public class ResolutionAmbiguity(
        public val candidates: List<TypeVariant<*>>,
    ) : ArgumentAcceptance(0)

    public object Impossible : ArgumentAcceptance(-1)

    public companion object {
        @JvmStatic
        public val ArgumentAcceptance.isAcceptable: Boolean
            get() = acceptanceLevel > 0

        @JvmStatic
        public val ArgumentAcceptance.isNotAcceptable: Boolean
            get() = acceptanceLevel <= 0
    }
}

@ExperimentalCommandDescriptors
public class CommandReceiverParameter<T : CommandSender>(
    override val isOptional: Boolean,
    override val type: KType,
) : CommandParameter<T>, AbstractCommandParameter<T>() {
    override val name: String get() = PARAMETER_NAME

    init {
        val classifier = type.classifier
        require(classifier is KClass<*>) {
            "CommandReceiverParameter.type.classifier must be KClass."
        }
        require(classifier.isSubclassOf(CommandSender::class)) {
            "CommandReceiverParameter.type.classifier must be subclass of CommandSender."
        }
    }

    public companion object {
        public const val PARAMETER_NAME: String = "<receiver>"
    }
}


internal val ANY_TYPE = typeOf0<Any>()
internal val ARRAY_OUT_ANY_TYPE = typeOf0<Array<out Any?>>()

@ExperimentalCommandDescriptors
public sealed class AbstractCommandValueParameter<T> : CommandValueParameter<T>, AbstractCommandParameter<T>() {
    override fun toString(): String = buildString {
        if (isVararg) append("vararg ")
        append(super.toString())
        if (isOptional) {
            append(" = ...")
        }
    }

    public override fun accepting(argument: CommandValueArgument, commandArgumentContext: CommandArgumentContext?): ArgumentAcceptance {
        if (isVararg) {
            val arrayElementType = this.type.arguments.single() // Array<T>
            return acceptingImpl(arrayElementType.type ?: ANY_TYPE, argument, commandArgumentContext)
        }

        return acceptingImpl(this.type, argument, commandArgumentContext)
    }

    private fun acceptingImpl(
        expectingType: KType,
        argument: CommandValueArgument,
        commandArgumentContext: CommandArgumentContext?,
    ): ArgumentAcceptance {
        if (argument.type.isSubtypeOf(expectingType)) return ArgumentAcceptance.Direct

        argument.typeVariants.associateWith { typeVariant ->
            if (typeVariant.outType.isSubtypeOf(expectingType)) {
                // TODO: 2020/10/11 resolution ambiguity
                return ArgumentAcceptance.WithTypeConversion(typeVariant)
            }
        }
        expectingType.classifierAsKClassOrNull()?.let { commandArgumentContext?.get(it) }?.let { parser ->
            return ArgumentAcceptance.WithContextualConversion(parser)
        }
        return ArgumentAcceptance.Impossible
    }

    @ConsoleExperimentalApi
    public class StringConstant(
        @ConsoleExperimentalApi
        public override val name: String?,
        public val expectingValue: String,
    ) : AbstractCommandValueParameter<String>() {
        public override val type: KType get() = STRING_TYPE
        public override val isOptional: Boolean get() = false
        public override val isVararg: Boolean get() = false

        init {
            require(expectingValue.isNotBlank()) {
                "expectingValue must not be blank"
            }
            require(expectingValue.none(Char::isWhitespace)) {
                "expectingValue must not contain whitespace"
            }
        }

        override fun toString(): String = "<$expectingValue>"

        private companion object {
            @OptIn(ExperimentalStdlibApi::class)
            val STRING_TYPE = typeOf<String>()
        }
    }

    /**
     * @see createOptional
     * @see createRequired
     */
    public class UserDefinedType<T>(
        public override val name: String?,
        public override val isOptional: Boolean,
        public override val isVararg: Boolean,
        public override val type: KType,
    ) : AbstractCommandValueParameter<T>() {
        init {
            requireNotNull(type.classifierAsKClassOrNull()) {
                "type.classifier must be KClass."
            }
            if (isVararg)
                check(type.isSubtypeOf(ARRAY_OUT_ANY_TYPE)) {
                    "type must be subtype of Array if vararg. Given $type."
                }
        }

        public companion object {
            @JvmStatic
            public inline fun <reified T : Any> createOptional(name: String, isVararg: Boolean): UserDefinedType<T> {
                @OptIn(ExperimentalStdlibApi::class)
                return UserDefinedType(name, true, isVararg, typeOf<T>())
            }

            @JvmStatic
            public inline fun <reified T : Any> createRequired(name: String, isVararg: Boolean): UserDefinedType<T> {
                @OptIn(ExperimentalStdlibApi::class)
                return UserDefinedType(name, false, isVararg, typeOf<T>())
            }
        }
    }

    /**
     * Extended by [CommandValueArgumentParser]
     */
    @ConsoleExperimentalApi
    public abstract class Extended<T> : AbstractCommandValueParameter<T>() {
        abstract override fun toString(): String
    }
}