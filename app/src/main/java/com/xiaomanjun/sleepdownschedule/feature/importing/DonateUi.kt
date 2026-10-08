package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.feature.importing.history.*
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.*
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.feature.home.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*
import com.xiaomanjun.sleepdownschedule.feature.settings.*
import com.xiaomanjun.sleepdownschedule.app.config.SleepDownRemoteConfig
import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.agent.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


@Composable
fun DonateSettingsScreen(
	state: AppState,
	backdrop: Backdrop?
) {
	val scope = rememberCoroutineScope()
	val remoteConfigState by SleepDownRemoteConfig.state.collectAsStateWithLifecycle()
	val donationSection = remoteConfigState.bootstrap?.donations
	LaunchedEffect(Unit) {
		SleepDownRemoteConfig.refresh(scope, force = true)
	}
    val topPadding = detailContentTopPadding()
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = DockScrollPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                SettingsInfoRow("感谢支持", "SleepDown 课程表会继续保持简洁、免费和尽量少打扰。你的捐赠会用于测试设备、应用维护和后续功能适配。捐赠完全自愿，不会影响任何功能使用。")
            }
        }
        item(key = "donation-reward-code") {
            Image(
                painter = painterResource(R.drawable.donate_reward),
                contentDescription = "微信赞赏码",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedRectangle(28.dp)),
                contentScale = ContentScale.FillWidth
            )
        }
        item {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                SettingsInfoRow("使用方式", "打开微信扫一扫，识别上方赞赏码即可。谢谢你愿意支持这个小小的课程表继续变好。")
            }
        }
        if (donationSection?.published == true) {
            item(key = "donation-thanks-inline") {
                DonationThanksPanel(
                    state = state,
                    backdrop = backdrop,
                    section = donationSection
                )
            }
        }
    }
}

