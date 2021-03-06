package io.github.starwishsama.comet.commands.console

import io.github.starwishsama.comet.BotVariables.bot
import io.github.starwishsama.comet.BotVariables.daemonLogger
import io.github.starwishsama.comet.api.command.CommandProps
import io.github.starwishsama.comet.api.command.interfaces.ConsoleCommand
import io.github.starwishsama.comet.enums.UserLevel
import io.github.starwishsama.comet.utils.CometUtil.getRestString
import io.github.starwishsama.comet.utils.StringUtil.isNumeric
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class BroadcastCommand: ConsoleCommand {
    override suspend fun execute(args: List<String>): String {
        return if (args.size > 1 && args[0].isNumeric()) {
            sendMessage(args[0].toLong(), args.getRestString(1))
        } else if (args[0] == "all") {
            sendMessage(-1, args.getRestString(1))
        } else {
            getHelp()
        }
    }

    private fun sendMessage(groupId: Long, message: String): String {
        if (groupId == -1L) {
            bot.groups.forEach {
                runBlocking {
                    try {
                        it.sendMessage(message)
                        delay(1_500)
                    } catch (e: RuntimeException) {
                        daemonLogger.warning("发送失败", e)
                    }
                }
            }

            return ""
        }

        val g = bot.getGroup(groupId) ?: return "找不到群号对应的群"

        return runBlocking {
            try {
                g.sendMessage(message)
                return@runBlocking "发送成功!"
            } catch (e: RuntimeException) {
                daemonLogger.warning(e.stackTraceToString())
                return@runBlocking "发送失败, 错误信息: ${e.message}"
            }
        }
    }

    override fun getProps(): CommandProps = CommandProps("broadcast", mutableListOf("bc"), "", "", UserLevel.CONSOLE)

    override fun getHelp(): String {
        return "/bc [群号] [发送内容]"
    }
}