package ru.playsoftware.j2meloader.nokia;

import androidx.annotation.DrawableRes;

import ru.playsoftware.j2meloader.R;

/**
 * 诺基亚 S60 风格图标映射。
 * 将 Android 包名映射到对应的 S60 图标资源 ID。
 * 两层策略：先精确匹配，再关键词模糊匹配。
 */
public class NokiaS60IconMap {

	/** 精确包名 → 图标资源 ID 映射表：{ R.drawable.xxx, pkg1, pkg2, ... } */
	private static final Object[][] EXACT_MAP = {
			// ── 日历 ──
			{R.drawable.s60_calendar,
					"com.android.calendar", "com.google.android.calendar",
					"com.miui.calendar", "com.samsung.android.calendar",
					"com.huawei.calendar", "com.oppo.calendar",
					"com.oneplus.calendar", "com.bbk.calendar"},

			// ── 联系人 ──
			{R.drawable.s60_contacts,
					"com.android.contacts", "com.google.android.contacts",
					"com.samsung.android.app.contacts", "com.samsung.android.contacts",
					"com.huawei.contacts", "com.android.incallui"},

			// ── 电话 / 通话记录 ──
			{R.drawable.s60_call_log,
					"com.android.dialer", "com.google.android.dialer",
					"com.samsung.android.dialer", "com.huawei.contacts.dialer",
					"com.android.server.telecom"},

			// ── 浏览器 ──
			{R.drawable.s60_browser,
					"com.android.browser", "com.android.chrome",
					"com.mi.globalbrowser", "com.huawei.browser",
					"com.UCMobile", "com.uc.browser", "com.tencent.mtt",
					"org.mozilla.firefox", "com.opera.browser",
					"com.microsoft.emmx", "com.android.edge"},

			// ── 短信 ──
			{R.drawable.s60_mms,
					"com.android.mms", "com.google.android.apps.messaging",
					"com.android.messaging", "com.samsung.android.messaging",
					"com.huawei.message", "com.bbk.mms"},

			// ── 图库 / 相册 ──
			{R.drawable.s60_gallery,
					"com.android.gallery3d", "com.google.android.apps.photos",
					"com.miui.gallery", "com.huawei.photos",
					"com.samsung.android.gallery", "com.oppo.gallery3d",
					"com.oneplus.gallery", "com.sec.android.gallery3d"},

			// ── 文件管理器 ──
			{R.drawable.s60_files,
					"com.android.fileexplorer", "com.mi.android.globalFileexplorer",
					"com.android.documentsui", "com.google.android.documentsui",
					"com.huawei.hidisk", "com.estrongs.android.pop",
					"com.oppo.filemanager", "com.oneplus.filemanager"},

			// ── 应用商店 ──
			{R.drawable.s60_app,
					"com.android.vending", "com.xiaomi.market",
					"com.huawei.appmarket", "com.heytap.market",
					"com.oppo.market", "com.bbk.appstore",
					"com.tencent.android.qqdownloader"},

			// ── 相机 ──
			{R.drawable.s60_camera,
					"com.android.camera", "com.android.camera2",
					"com.google.android.GoogleCamera", "com.huawei.camera",
					"com.samsung.android.camera", "com.oppo.camera",
					"com.sec.android.app.camera"},

			// ── 设置 ──
			{R.drawable.s60_settings,
					"com.android.settings", "com.huawei.systemmanager",
					"com.miui.securitycenter"},

			// ── 计算器 ──
			{R.drawable.s60_calculator,
					"com.android.calculator2", "com.google.android.calculator",
					"com.miui.calculator", "com.huawei.calculator",
					"com.oneplus.calculator", "com.sec.android.app.popupcalculator"},

			// ── 时钟 ──
			{R.drawable.s60_clock,
					"com.android.deskclock", "com.google.android.deskclock",
					"com.huawei.deskclock", "com.samsung.android.app.clock",
					"com.oneplus.deskclock"},

			// ── 邮件 ──
			{R.drawable.s60_email,
					"com.android.email", "com.google.android.gm",
					"com.microsoft.office.outlook", "com.huawei.email"},

			// ── Gmail（专用图标）──
			{R.drawable.s60_gmail, "com.google.android.gm"},

			// ── 音乐 ──
			{R.drawable.s60_music,
					"com.android.music", "com.google.android.music",
					"com.miui.player", "com.huawei.music",
					"com.samsung.android.app.music", "com.oppo.music",
					"com.tencent.qqmusic", "com.netease.cloudmusic",
					"com.kugou.android", "com.spotify.music"},

			// ── 视频播放器 ──
			{R.drawable.s60_video_player,
					"com.android.gallery3d", "com.mxtech.videoplayer.ad",
					"com.mxtech.videoplayer.pro", "com.google.android.videos",
					"com.huawei.himovie"},

			// ── 天气 ──
			{R.drawable.s60_weather,
					"com.android.weather", "com.miui.weather2",
					"com.huawei.weather", "com.sec.android.widgetapp.ap.hero.accuweather",
					"com.bbk.weather", "com.coloros.weather2"},

			// ── 地图 / 导航 ──
			{R.drawable.s60_navigator,
					"com.google.android.apps.maps", "com.baidu.BaiduMap",
					"com.autonavi.minimap", "com.tencent.map"},

			// ── 备忘录 / 笔记 ──
			{R.drawable.s60_notepad,
					"com.android.note", "com.miui.notes",
					"com.huawei.notepad", "com.samsung.android.app.notes",
					"com.google.android.apps.docs",
					"com.microsoft.office.onenote", "com.evernote"},

			// ── 收音机 ──
			{R.drawable.s60_fm_radio,
					"com.android.fmradio", "com.miui.fmradio",
					"com.huawei.android.FMRadio", "com.samsung.android.fmradio"},

			// ── 录音机 ──
			{R.drawable.s60_sound_recorder,
					"com.android.soundrecorder", "com.miui.soundrecorder",
					"com.huawei.soundrecorder", "com.samsung.android.app.voicerecorder"},

			// ── 下载管理 ──
			{R.drawable.s60_downloads,
					"com.android.providers.downloads.ui",
					"com.android.documentsui.downloads"},

			// ── WhatsApp ──
			{R.drawable.s60_whatsapp, "com.whatsapp", "com.whatsapp.w4b"},

			// ── YouTube ──
			{R.drawable.s60_youtube,
					"com.google.android.youtube", "com.google.android.apps.youtube.music"},

			// ── Skype ──
			{R.drawable.s60_skype, "com.skype.raider", "com.skype.android"},

			// ── 搜索 ──
			{R.drawable.s60_search,
					"com.google.android.googlequicksearchbox"},

			// ── 电子书 ──
			{R.drawable.s60_books,
					"com.google.android.apps.books", "com.amazon.kindle"},
	};

	/** 关键词模糊匹配：包名包含任一关键词则命中，按顺序匹配（先命中的优先） */
	private static final Object[][] FUZZY_MAP = {
			{R.drawable.s60_calculator, "calculator", "calc"},
			{R.drawable.s60_clock, "clock", "deskclock", "alarm", "timer"},
			{R.drawable.s60_calendar, "calendar", "schedule"},
			{R.drawable.s60_contacts, "contact", "contacts"},
			{R.drawable.s60_call_log, "dialer", "phone", "call", "telecom"},
			{R.drawable.s60_browser, "browser", "chrome", "webview"},
			{R.drawable.s60_mms, "mms", "message", "messaging", "sms", "chat"},
			{R.drawable.s60_gallery, "gallery", "photo", "album", "picture"},
			{R.drawable.s60_files, "file", "explorer", "document", "storage", "disk"},
			{R.drawable.s60_camera, "camera", "cam"},
			{R.drawable.s60_settings, "setting", "system", "config"},
			{R.drawable.s60_music, "music", "player", "song", "audio", "mp3", "spotify", "qqmusic", "kugou", "netease"},
			{R.drawable.s60_video_player, "video", "movie", "player", "media", "mxplayer", "bilibili", "douyin", "tiktok", "快手"},
			{R.drawable.s60_email, "mail", "email", "outlook"},
			{R.drawable.s60_weather, "weather", "forecast"},
			{R.drawable.s60_navigator, "map", "navig", "gps", "location"},
			{R.drawable.s60_notepad, "note", "memo", "pad", "notepad", "evernote"},
			{R.drawable.s60_fm_radio, "radio", "fm"},
			{R.drawable.s60_sound_recorder, "recorder", "record", "voice"},
			{R.drawable.s60_downloads, "download"},
			{R.drawable.s60_whatsapp, "whatsapp"},
			{R.drawable.s60_youtube, "youtube"},
			{R.drawable.s60_skype, "skype"},
			{R.drawable.s60_app, "market", "store", "appstore", "vending"},
			{R.drawable.s60_search, "search", "assistant"},
			{R.drawable.s60_books, "book", "reader", "kindle", "read"},
			{R.drawable.s60_dictionary, "dictionary", "dict", "translate", "translator"},
			{R.drawable.s60_themes, "theme", "wallpaper", "launcher"},
			{R.drawable.s60_sync, "sync", "backup"},
			{R.drawable.s60_sdcard, "sdcard", "sdcard"},
	};

	/**
	 * 根据包名查找对应的 S60 图标资源 ID。
	 * 先精确匹配，再关键词模糊匹配，均未命中返回 0（保持原图标）。
	 */
	@DrawableRes
	public static int getIcon(String packageName) {
		if (packageName == null) return 0;

		// 第一层：精确包名匹配
		for (Object[] entry : EXACT_MAP) {
			int resId = (int) entry[0];
			for (int i = 1; i < entry.length; i++) {
				if (packageName.equals(entry[i])) {
					return resId;
				}
			}
		}

		// 第二层：关键词模糊匹配
		String lower = packageName.toLowerCase();
		for (Object[] entry : FUZZY_MAP) {
			int resId = (int) entry[0];
			for (int i = 1; i < entry.length; i++) {
				if (lower.contains((String) entry[i])) {
					return resId;
				}
			}
		}

		return 0; // 无匹配，保留原始图标
	}

	/**
	 * 根据 NokiaAppItem 中的包名反查是否匹配 S60 图标。
	 * 用于排序：能匹配到图标的排在前面。
	 */
	@DrawableRes
	public static int getIconForItem(NokiaAppItem item) {
		if (item == null || item.launchIntent == null
				|| item.launchIntent.getComponent() == null) return 0;
		return getIcon(item.launchIntent.getComponent().getPackageName());
	}
}
