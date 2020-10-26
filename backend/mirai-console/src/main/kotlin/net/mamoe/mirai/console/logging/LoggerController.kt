/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 *
 */

package net.mamoe.mirai.console.logging

import net.mamoe.mirai.console.ConsoleFrontEndImplementation
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.internal.logging.LoggerControllerImpl
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.utils.MiraiLogger
import net.mamoe.mirai.utils.SimpleLogger

/**
 * 日志控制系统
 *
 * @see [LoggerControllerImpl]
 */
@ConsoleExperimentalApi
@ConsoleFrontEndImplementation
public interface LoggerController {
    /** 是否应该记录该等级的日志 */
    public fun shouldLog(identity: String?, priority: SimpleLogger.LogPriority): Boolean

    /**
     * 创建一个新的 [MiraiLogger]
     *
     * 实现细节:
     * - 应当直接创建一个新的 [MiraiLogger], 且不进行任何持久性操作,
     *   例如 放置到字段, 放入任意集合 等
     * - 即不需要在此方法中把 [MiraiLogger] 放入任意缓存
     *
     * * **注意**: [MiraiConsole] 会将 [net.mamoe.mirai.utils.DefaultLogger] 设置为 `MiraiConsole::createLogger`.
     * `MiraiConsole::createLogger` 会使用 [LoggerController.newLogger]
     * 因此不要在 [newLogger] 中调用 [net.mamoe.mirai.utils.DefaultLogger]
     */
    public fun newLogger(identity: String?): MiraiLogger
}