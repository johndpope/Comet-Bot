package io.github.starwishsama.comet.utils

import cn.hutool.core.net.URLDecoder
import com.google.gson.JsonParser
import io.github.starwishsama.comet.BotVariables.arkNight
import io.github.starwishsama.comet.BotVariables.cfg
import io.github.starwishsama.comet.BotVariables.daemonLogger
import io.github.starwishsama.comet.BotVariables.yyMMddPattern
import io.github.starwishsama.comet.enums.UserLevel
import io.github.starwishsama.comet.exceptions.ApiException
import io.github.starwishsama.comet.objects.BotUser
import io.github.starwishsama.comet.objects.gacha.items.ArkNightOperator
import io.github.starwishsama.comet.objects.gacha.items.GachaItem
import io.github.starwishsama.comet.objects.gacha.items.PCRCharacter
import io.github.starwishsama.comet.objects.gacha.pool.ArkNightPool
import io.github.starwishsama.comet.objects.gacha.pool.GachaPool
import io.github.starwishsama.comet.objects.gacha.pool.PCRPool
import io.github.starwishsama.comet.utils.NumberUtil.toLocalDateTime
import io.github.starwishsama.comet.utils.StringUtil.getLastingTimeAsString
import io.github.starwishsama.comet.utils.network.NetUtil
import org.jsoup.Jsoup
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO

object GachaUtil {
    /**
     * 明日方舟
     */

    const val overTimeMessage = "抽卡次数到上限了, 可以少抽一点或者等待条数自动恢复哦~\n" +
            "命令条数现在每小时会恢复100次, 封顶1000次"
    var pictureReady = false
    const val arkNightPictureCount = 181

    private const val arkNightDataApi = "https://api.github.com/repos/Kengxxiao/ArknightsGameData"
    const val arkNightData = "https://raw.fastgit.org/Kengxxiao/ArknightsGameData/master/zh_CN/gamedata/excel/character_table.json"

    fun combineGachaImage(gachaResult: List<GachaItem>, poolType: GachaPool): CombinedResult {
        require(gachaResult.isNotEmpty()) { "传入的抽卡结果列表不能为空!" }

        return when (poolType) {
            is ArkNightPool -> combineArkOpImage(gachaResult as List<ArkNightOperator>)
            is PCRPool -> combinePCRImage(gachaResult as List<PCRCharacter>)
            else -> throw UnsupportedOperationException("暂不支持合成该卡池图片")
        }
    }

    fun combinePCRImage(chars: List<PCRCharacter>): CombinedResult {
        return CombinedResult(BufferedImage(1, 1, 1), listOf())
    }

    /**
     * 根据抽卡结果合成图片
     */
    fun combineArkOpImage(ops: List<ArkNightOperator>): CombinedResult {
        require(ops.isNotEmpty()) { "传入的干员列表不能为空!" }

        val lostOperators = mutableListOf<ArkNightOperator>()

        val picSize = 180

        val picHeight = 380

        val newBufferedImage = BufferedImage(picSize * ops.size, picHeight, BufferedImage.TYPE_INT_RGB)

        val createGraphics = newBufferedImage.createGraphics()

        var newBufferedImageWidth = 0

        for (i in ops) {
            val file = File(FileUtil.getResourceFolder().getChildFolder("ark"), i.name + ".png")

            if (!file.exists()) {
                lostOperators.plusAssign(i)
                daemonLogger.warning("明日方舟: 干员 ${i.name} 的图片不存在")
            } else {
                val inStream: InputStream = file.inputStream()

                val bufferedImage: BufferedImage = ImageIO.read(inStream)

                val imageWidth = bufferedImage.width
                val imageHeight = bufferedImage.height

                createGraphics.drawImage(
                        bufferedImage.getScaledInstance(
                                imageWidth,
                                imageHeight,
                                Image.SCALE_SMOOTH
                        ), newBufferedImageWidth, 0, imageWidth, imageHeight, null
                )

                newBufferedImageWidth += imageWidth

            }
        }

        createGraphics.dispose()

        return CombinedResult(newBufferedImage, lostOperators)

    }

    data class CombinedResult(
        val image: BufferedImage,
        val lostItem: List<out GachaItem>
    )

    fun getStar(rare: Int, isArkNight: Boolean = false): String = buildString {
        val trueRare = if (isArkNight) rare + 1 else rare
        for (i in 0 until trueRare) {
            append("★")
        }
    }

    fun checkHasGachaTime(user: BotUser, time: Int): Boolean =
            (user.commandTime >= time || user.compareLevel(UserLevel.ADMIN)) && time <= 10000

    fun downloadArkNightsFile() {
        val arkLoc = FileUtil.getResourceFolder().getChildFolder("ark")

        if (arkNightPictureCount > arkLoc.filesCount()) {
            val startTime = LocalDateTime.now()
            daemonLogger.info("正在下载 明日方舟图片资源文件")

            var successCount = 0

            val downloadList = mutableSetOf<String>()

            val ele = Jsoup.connect(
                    "http://prts.wiki/w/PRTS:%E6%96%87%E4%BB%B6%E4%B8%80%E8%A7%88/%E5%B9%B2%E5%91%98%E7%B2%BE%E8%8B%B10%E5%8D%8A%E8%BA%AB%E5%83%8F"
            ).get().getElementsByClass("mw-parser-output")[0].select("a")


            ele.forEach {
                val doc2 = Jsoup.connect("http://prts.wiki/" + it.attr("href")).get()
                downloadList.plusAssign(doc2.getElementsByClass("fullImageLink")[0].select("a").attr("href"))
            }

            // [http://prts.wiki]/images/f/ff/半身像_诗怀雅_1.png

            downloadList.parallelStream().forEach { url ->
                val opName = URLDecoder.decode(url.split("/")[4].split("_")[1], Charsets.UTF_8)

                if (arkNight.stream().filter { it.name == opName }.findFirst().isPresent) {
                    try {
                        val file = File(arkLoc, url)
                        if (!file.exists()) {
                            val result = TaskUtil.executeRetry(3) {
                                NetUtil.downloadFile(arkLoc, "http://prts.wiki$url", "$opName.png")
                            }
                            if (result != null) throw result
                            successCount++
                        }
                    } catch (e: Exception) {
                        if (e !is ApiException)
                            daemonLogger.warning("下载 $url 时出现了意外", e)
                        else
                            daemonLogger.warning("下载异常: ${e.message ?: "无信息"}")
                        return@forEach
                    } finally {
                        if (successCount > 0 && successCount % 10 == 0) {
                            daemonLogger.info("明日方舟 > 已下载 $successCount/${arkNight.size}")
                        }
                    }
                }
            }

            daemonLogger.info("明日方舟 > 缺失资源文件下载完成 [$successCount/${arkNight.size}], 耗时 ${startTime.getLastingTimeAsString()}")
        }

        pictureReady = true
    }

    fun arkNightDataCheck(location: File) {
        val isOld: Boolean

        daemonLogger.info("明日方舟 > 检查是否为旧版本数据...")
        if (!location.exists() || !JsonParser.parseString(location.getContext()).isJsonObject) {
            daemonLogger.info("明日方舟 > 你正在使用旧版本的数据, 正在自动下载新数据")
            isOld = true
        } else {
            return
        }

        if (!location.exists()) {
            daemonLogger.info("正在下载 明日方舟干员数据")
            NetUtil.downloadFile(location, arkNightData)
        }

        val result = JsonParser.parseString(NetUtil.executeHttpRequest(arkNightDataApi).body?.string())
        val updateTime = LocalDateTime.parse(result.asJsonObject["updated_at"].asString, DateTimeFormatter.ISO_DATE_TIME)

        if (isOld || location.lastModified().toLocalDateTime() < updateTime) {
            daemonLogger.info("明日方舟干员数据有更新 (${yyMMddPattern.format(updateTime)}), 正在下载")
            val data = NetUtil.downloadFile(FileUtil.getCacheFolder(), arkNightData, location.name)
            Files.copy(data.toPath(), location.toPath(), StandardCopyOption.REPLACE_EXISTING)
            data.delete()
        } else {
            daemonLogger.info("明日方舟干员数据为最新版本: ${yyMMddPattern.format(updateTime)}")
        }
    }

    fun arkPictureIsUsable(): Boolean = cfg.arkDrawUseImage && pictureReady
}