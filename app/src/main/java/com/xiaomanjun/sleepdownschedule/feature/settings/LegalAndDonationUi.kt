package com.xiaomanjun.sleepdownschedule.feature.settings

import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.*

import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import java.math.BigDecimal

private data class PrivacyPolicySection(val title: String, val body: String)

private val SleepDownPrivacyPolicySections = listOf(
    PrivacyPolicySection(
        "一、适用范围与处理原则",
        "本政策适用于 SleepDown 课程表 Android 应用及由我们运营的 sleepdownschedule.cn、api.sleepdownschedule.cn 服务。我们遵循合法、正当、必要、诚信和公开透明原则，仅处理实现相应功能所需的信息。除非功能另有明确说明，拒绝非必要权限不会影响课表的基本查看与编辑。"
    ),
    PrivacyPolicySection(
        "二、本机保存的课表数据",
        "课程名称、教师、地点、周次、节次、学期、作息方案、应用设置、壁纸取景、组件外观、助手记忆和导入历史默认保存在你的设备应用空间内。我们不会通过 SleepDown 后端主动上传这些内容。备份文件仅在你主动导出或选择恢复时由你指定位置；卸载应用或清除应用数据可能删除未另行备份的本机数据。"
    ),
    PrivacyPolicySection(
        "三、联网服务与匿名统计",
        "应用会为当前安装随机生成一个安装标识，并向 SleepDown 后端发送该标识、应用版本号与名称、Android API 版本以及首次/最近活跃时间，用于统计安装量、日活跃、周活跃、月活跃和版本兼容情况。该标识不是 IMEI、Android ID、OAID、手机号、MAC 地址或广告标识。应用还会请求公告、协议、捐赠致谢和加密的 AI 配置。网络连接过程中服务器和网络设施可能临时接触 IP 地址，但应用业务数据库不以 IP 地址建立用户画像。"
    ),
    PrivacyPolicySection(
        "四、AI 与第三方服务",
        "当你主动使用 AI 对话、今日助手或 AI 教务导入时，你提交的文字、课程上下文以及主动选择的图片或文件会直接发送给你选定的模型服务商。SleepDown 后端只下发加密配置，不代理或保存这些 AI 请求正文。若使用“每日免费 AI”，应用会在本机校验并解密后直接访问后台指定的模型接口；若使用自己的 API Key，Key 会加密保存在本机，也不会写入普通课表备份。第三方模型服务商会依据其自身条款和隐私政策处理数据，请勿提交无权提供或不希望交由第三方处理的信息。"
    ),
    PrivacyPolicySection(
        "五、教务、天气与外部页面",
        "教务导入可能在应用内打开学校教务网页，登录凭据和页面数据由相应学校系统处理。天气功能仅在你开启功能并授予大致位置权限后读取设备提供的最近位置，用于查询附近天气并在本机短期缓存结果。GitHub、官网、支付工具及其他外部页面由相应服务提供者独立处理数据。"
    ),
    PrivacyPolicySection(
        "六、权限说明",
        "通知、精确闹钟、开机启动、前台服务、忽略电池优化、勿扰访问和唤醒锁用于课程提醒、实时活动、下载或恢复计划任务；大致位置用于可选天气；系统文件或照片选择器用于你主动选择的导入文件、备份和壁纸。应用不会因获得选择器结果而扫描设备上的其他文件。你可随时在 Android 系统设置或应用设置中关闭相应权限或功能。"
    ),
    PrivacyPolicySection(
        "七、捐赠与公开致谢",
        "捐赠完全自愿，支付由外部支付工具处理，SleepDown 不在应用内收集银行卡号或支付账户密码。经捐赠者同意后，我们可在后台发布其自选 ID 和捐赠金额，形成任何人均可通过应用取得的公开致谢名单。请优先使用昵称或无法直接识别真实身份的 ID；你可通过本政策所列渠道申请更正或撤下。"
    ),
    PrivacyPolicySection(
        "八、保存期限与安全",
        "本机信息由你控制，直至你删除、清除应用数据或卸载。匿名安装与活跃记录会在提供统计和维护服务所需期间保存；公开致谢保留至你请求撤下或我们停止该栏目。我们使用 HTTPS、访问控制、服务端静态加密和客户端加密传输配置等措施降低未授权访问风险，但互联网传输无法保证绝对安全。"
    ),
    PrivacyPolicySection(
        "九、你的权利与未成年人保护",
        "你可以在应用内查看、修改、导入、导出或删除本机课表与设置，并可撤回可选权限。对于后端保存的安装统计或公开致谢，可通过反馈渠道申请查询、更正或删除；为避免误删，我们可能要求提供与请求相关的必要验证信息。未满十四周岁的用户应在监护人指导下使用联网和捐赠功能，不应向 AI 或公开致谢提交真实姓名、学校班级、联系方式等敏感信息。"
    ),
    PrivacyPolicySection(
        "十、政策更新与联系我们",
        "功能、数据处理方式或法律要求发生重要变化时，我们会更新本政策，并可通过应用内协议、公告或官网提示。若更新涉及需要重新取得同意的事项，我们会依法另行征得同意。运营者为 SleepDown 课程表开发者。隐私问题、权利请求与投诉可通过官网 https://sleepdownschedule.cn 或 GitHub Issues（xiaomanjun233/CourseSchedule）联系我们。"
    )
)

@Composable
fun PrivacyPolicySettingsScreen(state: AppState, backdrop: Backdrop?) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = detailContentTopPadding(), bottom = DockScrollPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "privacy-meta") {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                SettingsInfoRow(
                    "SleepDown 课程表隐私政策",
                    "版本 1.0\n更新日期：2026 年 8 月 16 日\n生效日期：2026 年 8 月 16 日\n\n请在使用前完整阅读。各项联网能力均不影响本机课表的基本查看与编辑。"
                )
            }
        }
        SleepDownPrivacyPolicySections.forEachIndexed { index, section ->
            item(key = "privacy-section-$index") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsInfoRow(section.title, section.body)
                }
            }
        }
    }
}

private fun RemoteDonationEntry.formattedAmount(): String {
    val number = BigDecimal.valueOf(amountCents, 2).toPlainString()
    return when (currency) {
        "CNY" -> "¥$number"
		"USD" -> "\$$number"
        "EUR" -> "€$number"
        else -> "$currency $number"
    }
}

@Composable
fun DonationThanksPanel(
	state: AppState,
	backdrop: Backdrop?,
	section: RemoteDonationSection
) {
	SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
		SettingsInfoRow(
			section.title.ifBlank { "捐赠致谢" },
			section.message.ifBlank { "感谢每一份支持。" }
		)
		SettingsDivider()
		Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
			Text("ID", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			Text("捐赠金额", modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		section.entries.filter(RemoteDonationEntry::enabled).forEach { item ->
			SettingsDivider()
			Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp)) {
				Text(item.supporterId, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
				Text(item.formattedAmount(), modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
			}
		}
		if (section.entries.none(RemoteDonationEntry::enabled)) {
			SettingsDivider()
			Text("名单已发布，暂时还没有公开条目。", modifier = Modifier.fillMaxWidth().padding(20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
    }
}
