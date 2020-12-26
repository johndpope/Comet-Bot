package io.github.starwishsama.comet.utils

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.github.starwishsama.comet.BotVariables
import io.github.starwishsama.comet.BotVariables.cfg
import io.github.starwishsama.comet.BotVariables.coolDown
import io.github.starwishsama.comet.objects.BotUser
import io.github.starwishsama.comet.objects.BotUser.Companion.getUser
import io.github.starwishsama.comet.objects.BotUser.Companion.isBotOwner
import io.github.starwishsama.comet.utils.RuntimeUtil.getJVMVersion
import io.github.starwishsama.comet.utils.RuntimeUtil.getMaxMemory
import io.github.starwishsama.comet.utils.RuntimeUtil.getOsInfo
import io.github.starwishsama.comet.utils.RuntimeUtil.getUsedMemory
import io.github.starwishsama.comet.utils.StringUtil.convertToChain
import io.github.starwishsama.comet.utils.StringUtil.toFriendly
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageChain
import org.apache.commons.lang3.StringUtils
import org.hydev.logger.HyLogger
import java.time.Duration
import java.time.LocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinDuration

/**
 * 用于辅助机器人运行的各种工具方法
 *
 * @author Nameless
 */

@Suppress("unused", "NOTHING_TO_INLINE")
inline fun HyLogger.warningS(message: String?) {
    if (cfg.debugMode) {
        warning(message ?: return)
    }
}

@Suppress("unused", "NOTHING_TO_INLINE")
inline fun HyLogger.warningS(message: String?, throwable: Throwable?) {
    if (cfg.debugMode) {
        warning((message ?: return) + "\n" + throwable?.stackTraceToString())
    }
}

@Suppress("unused", "NOTHING_TO_INLINE")
inline fun HyLogger.warningS(throwable: Throwable?) {
    if (cfg.debugMode) {
        warning(throwable?.stackTraceToString() ?: return)
    }
}

@Suppress("unused", "NOTHING_TO_INLINE")
inline fun HyLogger.debugS(message: String?) {
    if (cfg.debugMode) {
        debug(message ?: "null")
    }
}

@Suppress("unused", "NOTHING_TO_INLINE")
inline fun HyLogger.debugS(throwable: Throwable?) {
    if (cfg.debugMode) {
        debug(throwable?.stackTraceToString() ?: return)
    }
}

@Suppress("unused", "NOTHING_TO_INLINE")
inline fun HyLogger.debugS(message: String?, throwable: Throwable?) {
    if (cfg.debugMode) {
        debug(message ?: "null" + "\n" + throwable?.stackTraceToString())
    }
}

@Suppress("unused", "NOTHING_TO_INLINE")
inline fun HyLogger.verboseS(message: String?) {
    if (cfg.debugMode) {
        log(message ?: "null")
    }
}

@Suppress("unused", "NOTHING_TO_INLINE")
inline fun HyLogger.verboseS(throwable: Throwable?) {
    if (cfg.debugMode) {
        log(throwable?.stackTraceToString() ?: return)
    }
}

@Suppress("unused", "NOTHING_TO_INLINE")
inline fun HyLogger.verboseS(message: String?, throwable: Throwable?) {
    if (cfg.debugMode) {
        log((message ?: "null") + throwable?.stackTraceToString())
    }
}

object BotUtil {
    /**
     * 判断指定QQ号是否仍在命令冷却中
     * (可以自定义命令冷却时间)
     *
     * @author Nameless
     * @param qq 要检测的QQ号
     * @param seconds 自定义冷却时间
     * @return 目标QQ号是否处于冷却状态
     */
    fun hasNoCoolDown(qq: Long, seconds: Int = cfg.coolDownTime): Boolean {
        if (seconds < 1) return true

        val currentTime = System.currentTimeMillis()
        if (qq == 80000000L) {
            return false
        }

        if (coolDown.containsKey(qq) && !isBotOwner(qq)) {
            if (currentTime - (coolDown[qq] ?: return false) < seconds * 1000) {
                return false
            } else {
                coolDown.remove(qq)
            }
        } else {
            coolDown[qq] = currentTime
        }
        return true
    }

    /**
     * 获取本地化文本
     *
     * @author NamelessSAMA
     * @param node 本地化文本节点
     * @return 本地化文本
     */
    fun getLocalMessage(node: String): String {
        for ((n, t) in BotVariables.localMessage) {
            if (n.contentEquals(node)) {
                return t
            }
        }
        return "PlaceHolder"
    }

    fun sendMessageAsString(otherText: String?, addPrefix: Boolean = true): String {
        if (otherText.isNullOrEmpty()) return ""
        return buildString {
            if (addPrefix) {
                append(getLocalMessage("msg.bot-prefix")).append(" ")
            }
            append(otherText)
        }.trim()
    }

    fun sendMessage(otherText: String?, addPrefix: Boolean = true): MessageChain = sendMessageAsString(otherText, addPrefix).convertToChain()

    @JvmName("stringAsChain")
    fun String?.sendMessage(addPrefix: Boolean = true): MessageChain = sendMessage(this, addPrefix)

    fun List<String>.getRestString(startAt: Int): String {
        if (isEmpty()) {
            return "空"
        }

        return buildString {
            for (i in startAt until size) {
                append(this@getRestString[i]).append(" ")
            }
            trim()
        }
    }

    @ExperimentalTime
    fun getRunningTime(): String {
        val remain = Duration.between(BotVariables.startTime, LocalDateTime.now())
        return remain.toKotlinDuration().toFriendly()
    }

    fun parseAtToId(event: MessageEvent, possibleID: String): Long {
        event.message.forEach {
            if (it is At) {
                return it.target
            }
        }

        return if (StringUtils.isNumeric(possibleID)) {
           possibleID.toLong()
        } else {
            -1
        }
    }

    fun parseAtAsBotUser(event: MessageEvent, id: String): BotUser? = getUser(parseAtToId(event, id))

    @ExperimentalTime
    fun getMemoryUsage(): String =
        "OS 信息: ${getOsInfo()}\n" +
                "JVM 版本: ${getJVMVersion()}\n" +
                "内存占用: ${getUsedMemory()}MB/${getMaxMemory()}MB\n" +
                "运行时长: ${getRunningTime()}"

    fun isValidJson(json: String): Boolean {
        val jsonElement: JsonElement = try {
            JsonParser.parseString(json)
        } catch (e: Exception) {
            return false
        }
        return jsonElement.isJsonObject
    }

    fun isValidJson(element: JsonElement): Boolean {
        return element.isJsonObject || element.isJsonArray
    }
}
