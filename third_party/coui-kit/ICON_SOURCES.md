# 系统功能图标来源与调用依据

图标取自 ColorOS 17 PMX110 17.0.0.115。下表列出原始资源、调用链和校验值。

| CouiIcons | 来源包 / drawable | 实际调用链 | 原始 XML SHA-256 |
| --- | --- | --- | --- |
| Components | com.oplus.tips / ic_all_category_unselected | Tips menu_main_tab.xml → nav_ic_all_category selector | d90a4e1069c850935b80ab0585ee03b8f9c5adac3f7dc4170dbc0b3420f990e5 |
| ComponentsSelected | com.oplus.tips / ic_all_category_selected | Tips menu_main_tab.xml → nav_ic_all_category selector | d7ddc190370839cb719be7679b947c8817ba3f954b81ee6e8caac3929b337ecf |
| Featured | com.oplus.tips / ic_featured_unselected | Tips menu_main_tab.xml → nav_ic_featured selector | f143443c3c31ba9d62e1b85e92b145d63a17905f8b3ea630469f3a0ceba23051 |
| FeaturedSelected | com.oplus.tips / ic_featured_selected | Tips menu_main_tab.xml → nav_ic_featured selector | 8fad30894e1538412410025f0c2ee00753a16b8cf19cf32fa03654d277c0ee52 |
| Theme | com.android.settings / dark_theme | Settings DarkThemeSlice.getSlice → IconCompat.createWithResource | c184ff63fcd218d7e4ee8c5cd66df8acaf676b731c99b6ebe49c223d6de94238 |
| Add | com.coloros.alarmclock / color_floating_button_icon_add | Clock OplusTimerFragment → COUIFloatingButton.setMainFabDrawable | abfd514625d69101f66032a542e255ad5f7257674790648d2cf7904508958e3e |
| Search | com.android.settings / ic_homepage_search | Settings search_bar.xml / search_bar_unified_version.xml ImageView | 5bdd6bb736f7b27db5bc12b58fc230f12897ade073592e5f47929dcf3d230005 |
| SearchField | com.android.settings / coui_search_view_icon_normal | Settings HomepageSearchFeature → coui_search_view_icon selector / COUISearchView | 764254abc126bfd054360128c4b70c81c18638fba39078842bc268c5186e7cef |
| More | com.android.settings / coui_toolbar_menu_icon_more_normal | Settings Widget.COUI.Toolbar.MenuView → coui_toolbar_menu_icon_more selector | 139a02285cbb3f10ce937164ed125a75018bae5748a87f08a6b8ba3bbd752165 |
| Settings | com.android.settings / ic_settings_24dp | Settings SmartTouchUtils.buildNotification → smallIcon / action | f566d961f177b2af683ebe78d3b097ccdc352683d77cf5ed1190c15563dc2273 |
| Brightness | com.android.settings / settings_brightness_ic | Settings top_level_settings_oplus.xml → HomepagePreference icon | 3de7e2d4ccadbdea685204f03412d10da30b5630bd41fcb96f398275e0cd6161 |
| Notifications | com.android.settings / settings_notify_center_ic | Settings top_level_settings_oplus.xml → HomepagePreference icon | 08998674008af9e005ec9b45cbc188bf11d6fd4ed4d4aceee73c856e8cbb24ee |
| Display | com.android.settings / settings_large_screen_ic | Settings top_level_settings_oplus.xml → HomepagePreference icon | b82258ad8a0dabb448297b90ffaa8e5174d23d26fc25d38f7eeb2facd84014e6 |
| Personalise | com.android.settings / settings_personalise_ic | Settings top_level_settings_oplus.xml → HomepagePreference icon | c9ba0d6f36c1299f187e44720ac58488332856d16dc4babd2e3b447913b679d5 |
| Network | com.android.settings / settings_mobile_network_ic | Settings top_level_settings_oplus.xml → HomepagePreference icon | faa79add622fb861f1945b87ecf479e2fcb14fce78909dd8fa79d6650f300d81 |
| Wallpaper | com.android.settings / settings_wallpaper_ic | Settings top_level_settings_oplus.xml → HomepagePreference icon | c9ba0d6f36c1299f187e44720ac58488332856d16dc4babd2e3b447913b679d5 |
| Time | com.coloros.alarmclock / alarm_clock_icon_small | Clock alarmclock.fluid.a → drawable/alarm_clock_icon_small | e2bc49d69829d66be7c8423783b56f8481b98c3a8f9882a4fb4edb241fb3672b |
| Countdown | com.coloros.alarmclock / icon_timer_small | Clock timer_alert_fullscreen_view.xml / timer_device_case_view.xml | a4ed71d8f550b39cbeddb6b318bbed18a974a0566413791ac6485509218932df |
| Calendar | com.android.settings / material_ic_calendar_black_24dp | Settings MaterialDatePicker.createHeaderToggleDrawable → stateListDrawable | 1ae3408e3c2df83732e59913f5ff206b910344050c6801c1ab27919faf72e774 |
| Info | com.android.settings / settings_about_device_ic | Settings top_level_settings_oplus.xml → about_device icon | 824159fceb2b5df4121a98346bb48738f201d69d220e5939a31d1dc902cef906 |
| Sound | com.android.settings / settings_sound_settings_ic | Settings top_level_settings_oplus.xml → HomepagePreference icon | f3b6597e87e4fd34e33ee31c0a4e8200da8a29bb91a1af9a610b05a6c9e38da1 |
| Delete | com.android.settings / ic_delete | Settings preferred_locale_menu.xml / preferred_locale_menu_down.xml | 43cbd104ba875a9735f44765b420d9559562c55689e2535809ac0035a5a3eb3b |
| Check | com.android.settings / menu_ic_confirm_normal | Settings user_create_settings_menu.xml → menu_ic_confirm_selector | 1ea5b0354f29aa26fc13b6da1a397ce416d728839dfe004043201685f5d574bf |
| Back | com.android.settings / coui_back_arrow_normal | Settings COUIToolbar → coui_back_arrow selector | 6d6f5ff551a7e809a560823dc5be56d4291e841427dc94c472c5dfe7940b615d |
| Next | com.android.settings / coui_btn_next_normal | Settings Preference style coui_jump_mark → coui_btn_next selector | 5e65a11bb63a15e9e35c34009b56b2529404692bddcb0d15cae00d672e586b41 |
| Popup | com.android.settings / coui_pop_up_next_normal | Settings Preference style coui_jump_mark → coui_pop_up_next selector | 4577c1f6d328c3dda72aba65d85075410633f914179b4bd377c7a6295bd4bbd3 |
| Close | com.android.settings / ic_close | Settings identity_check_promo_card.xml / safety_center_qs.xml | bcfe067d988f857ef7524b7a8547d4ad91c387db5ae145eb18aa1771c38c9a5b |

SearchField 与 Search 来自不同资源。Back/Next 继承原选择器的 RTL 镜像。矢量保留路径、分组变换、描边和 alpha，功能色由调用方 tint；Tips 导航态 alpha 由底栏控制。

APK SHA-256：

- com.android.settings：d97df8a9227520a6f673e5d6f5efbc901b5d9d4c501d12e5d8bf717daae079d5
- com.coloros.alarmclock：231b22b01cee3783259bdf896399308c0b9cd7e279b0ba226d6735f0075a778d
- com.oplus.tips：556917e2079696cd13fd82c3636949a4a2f5d78e473c3e79c829f9724aea83f9

原厂及 Android 上游权利保留。未检出这些提取资源附带的独立许可；本项目不将其改标为 MPL-2.0，也不授予额外第三方权利。
