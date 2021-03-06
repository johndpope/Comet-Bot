package io.github.starwishsama.comet.managers

import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import io.github.starwishsama.comet.BotVariables
import io.github.starwishsama.comet.BotVariables.daemonLogger
import io.github.starwishsama.comet.objects.gacha.custom.CustomPool
import io.github.starwishsama.comet.objects.gacha.pool.ArkNightPool
import io.github.starwishsama.comet.objects.gacha.pool.GachaPool
import io.github.starwishsama.comet.objects.gacha.pool.PCRPool
import io.github.starwishsama.comet.utils.FileUtil
import io.github.starwishsama.comet.utils.getContext
import java.io.File
import kotlin.streams.toList

/**
 * [GachaManager]
 *
 * 管理所有运行时载入的卡池.
 *
 * 另见 [io.github.starwishsama.comet.objects.gacha.custom.CustomPool]
 */
class GachaManager {
    val gachaPools = mutableSetOf<GachaPool>()
    val poolPath = FileUtil.getChildFolder("gacha")

    fun loadAllPools() {
        if (!poolPath.exists()) {
            poolPath.mkdirs()
            return
        }

        if (poolPath.listFiles()?.isEmpty() == true) {
            return
        }

        poolPath.listFiles()?.forEach {
            addPoolFromFile(it)
        }
    }

    fun addPool(gachaPool: CustomPool): Boolean {
        val exists = gachaPools.parallelStream().filter { it.name == gachaPool.poolName }.findAny().isPresent

        if (exists) {
            daemonLogger.warning("已有相同名称的卡池存在! 请检查是否忘记删除了旧文件: ${gachaPool.poolName}")
            return false
        }

        return when (gachaPool.gameType) {
            CustomPool.GameType.ARKNIGHT -> {
                parseArkNightPool(gachaPool)?.let { gachaPools.add(it) } == true
            }
            // 暂不支持 PCR 卡池自定义
            CustomPool.GameType.PCR -> {
                false
            }
        }
    }

    @Suppress("unchecked_cast")
    inline fun <reified T> getPoolsByType(): List<T> {
        return gachaPools.stream().filter { it is T }.toList() as List<T>
    }

    @Throws(JsonParseException::class)
    fun addPoolFromFile(poolFile: File) {
        require(poolFile.exists()) { "${poolFile.name} isn't exists" }
        val context = JsonParser.parseString(poolFile.getContext())
        require(context.isJsonObject) { "${poolFile.name} isn't a valid json file!" }

        try {
            val pool = BotVariables.gson.fromJson(context, CustomPool::class.java)
            addPool(pool)
        } catch (e: Exception) {
            FileUtil.createErrorReportFile("解析卡池信息失败", "gacha", e, context.asString, e.message ?: "")
        }
    }

    private fun parseArkNightPool(customPool: CustomPool): ArkNightPool? {
        val pool = ArkNightPool(
            customPool.poolName,
            customPool.poolDescription
        ) {
            customPool.condition.contains(obtain)
        }

        customPool.modifiedGachaItems.forEach { item ->
            val result = pool.poolItems.stream().filter { it.name == item.name }.findAny()

            result.ifPresent {
                if (item.isHidden) {
                    pool.poolItems.remove(it)
                    return@ifPresent
                }

                if (item.probability > 0) {
                    pool.highProbabilityItems[it] = item.probability
                }
            }.also {
                if (!result.isPresent) {
                    daemonLogger.warning("名为 ${item.name} 的抽卡物品不存在于游戏数据中")
                }
            }
        }
        return null
    }

    private fun poolTypeSelector(typeName: String): Class<out GachaPool> {
        return when (typeName.toLowerCase()) {
            "arknight" -> ArkNightPool::class.java
            "pcr" -> PCRPool::class.java
            else -> throw UnsupportedOperationException("Unsupported gacha pool type: $typeName")
        }
    }
}