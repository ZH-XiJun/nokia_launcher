package ru.playsoftware.j2meloader.nokia;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import androidx.annotation.DrawableRes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ru.playsoftware.j2meloader.R;

/**
 * 诺基亚 S60 风格图标映射。
 * 三层互补匹配策略（任一命中即换图标）：
 *   1. Intent Filter 扫描 — 按功能类别分配图标，覆盖任何 ROM 的同类应用
 *   2. 精确包名匹配   — 知名应用直接命中，init 时构建进缓存
 *   3. 关键词模糊匹配 — 运行时兜底，覆盖未命中缓存的包名
 * 第 1/2 层结果缓存至内存，仅在应用安装/卸载变化时重新扫描。
 */
public class NokiaS60IconMap {

	/** IntentFilter 探测项：图标资源 + 探测 Intent */
	private static class Probe {
		final int iconResId;
		final Intent intent;
		Probe(int iconResId, Intent intent) { this.iconResId = iconResId; this.intent = intent; }
	}

	/** 按优先级排列的探测列表：越靠前优先级越高（先命中先得） */
	private static final Probe[] PROBES = {
			// ── 高优先级：功能明确的定向意图 ──
			new Probe(R.drawable.s60_camera,       intent(MediaStore.ACTION_IMAGE_CAPTURE)),
			new Probe(R.drawable.s60_calendar,     appCategory(Intent.CATEGORY_APP_CALENDAR)),
			new Probe(R.drawable.s60_call_log,     intent(Intent.ACTION_DIAL)),
			new Probe(R.drawable.s60_contacts,     appCategory(Intent.CATEGORY_APP_CONTACTS)),
			new Probe(R.drawable.s60_mms,          appCategory(Intent.CATEGORY_APP_MESSAGING)),
			new Probe(R.drawable.s60_calculator,   appCategory(Intent.CATEGORY_APP_CALCULATOR)),
			new Probe(R.drawable.s60_clock,        intent(AlarmClock.ACTION_SET_ALARM)),
			new Probe(R.drawable.s60_email,        appCategory(Intent.CATEGORY_APP_EMAIL)),
			new Probe(R.drawable.s60_weather,      appCategory(Intent.CATEGORY_APP_WEATHER)),
			new Probe(R.drawable.s60_music,        appCategory(Intent.CATEGORY_APP_MUSIC)),
			new Probe(R.drawable.s60_navigator,    appCategory(Intent.CATEGORY_APP_MAPS)),
			new Probe(R.drawable.s60_gallery,      appCategory(Intent.CATEGORY_APP_GALLERY)),
			new Probe(R.drawable.s60_notepad,      intent("android.intent.action.CREATE_NOTE")),
			new Probe(R.drawable.s60_app,          appCategory(Intent.CATEGORY_APP_MARKET)),
			new Probe(R.drawable.s60_settings,     intent(Settings.ACTION_SETTINGS)),
			new Probe(R.drawable.s60_fm_radio,     intent("android.intent.action.FM_RADIO")),

			// ── 中优先级：较通用但仍具辨别力 ──
			new Probe(R.drawable.s60_files,        appCategory(Intent.CATEGORY_APP_FILES)),
			new Probe(R.drawable.s60_video_player, new Intent(Intent.ACTION_VIEW).setType("video/*").addCategory(Intent.CATEGORY_DEFAULT)),

			// ── 低优先级：泛用意图，靠后避免误匹配 ──
			new Probe(R.drawable.s60_browser,      appCategory(Intent.CATEGORY_APP_BROWSER)),
			new Probe(R.drawable.s60_sound_recorder, intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)),
			new Probe(R.drawable.s60_search,       intent(Intent.ACTION_SEARCH)),
	};

	// ── Fallback：精确包名匹配（意图匹配不到的知名应用）──
	private static final Object[][] EXACT_FALLBACK = {
			{R.drawable.s60_calendar,  "com.android.calendar", "com.google.android.calendar", "com.miui.calendar"},
			{R.drawable.s60_contacts,  "com.android.contacts", "com.google.android.contacts", "com.samsung.android.app.contacts"},
			{R.drawable.s60_call_log,  "com.android.dialer", "com.google.android.dialer"},
			{R.drawable.s60_browser,   "com.android.chrome", "com.mi.globalbrowser", "com.UCMobile", "com.tencent.mtt", "org.mozilla.firefox"},
			{R.drawable.s60_mms,       "com.android.mms", "com.google.android.apps.messaging", "com.samsung.android.messaging"},
			{R.drawable.s60_gallery,   "com.android.gallery3d", "com.miui.gallery", "com.google.android.apps.photos"},
			{R.drawable.s60_files,     "com.android.fileexplorer", "com.mi.android.globalFileexplorer", "com.android.documentsui", "com.huawei.hidisk", "com.estrongs.android.pop"},
			{R.drawable.s60_app,       "com.android.vending", "com.xiaomi.market", "com.huawei.appmarket", "com.tencent.android.qqdownloader"},
			{R.drawable.s60_camera,    "com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera", "com.huawei.camera"},
			{R.drawable.s60_settings,  "com.android.settings"},
			{R.drawable.s60_calculator,"com.android.calculator2", "com.miui.calculator", "com.huawei.calculator"},
			{R.drawable.s60_clock,     "com.android.deskclock", "com.google.android.deskclock", "com.huawei.deskclock"},
			{R.drawable.s60_email,     "com.android.email", "com.google.android.gm", "com.microsoft.office.outlook"},
			{R.drawable.s60_music,     "com.android.music", "com.miui.player", "com.tencent.qqmusic", "com.netease.cloudmusic", "com.kugou.android", "com.spotify.music"},
			{R.drawable.s60_weather,   "com.miui.weather2", "com.huawei.weather"},
			{R.drawable.s60_navigator, "com.google.android.apps.maps", "com.baidu.BaiduMap", "com.autonavi.minimap", "com.tencent.map"},
			{R.drawable.s60_notepad,   "com.miui.notes", "com.huawei.notepad", "com.google.android.apps.docs", "com.evernote"},
			{R.drawable.s60_video_player, "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro"},
			{R.drawable.s60_sound_recorder, "com.android.soundrecorder", "com.miui.soundrecorder"},
			{R.drawable.s60_fm_radio,  "com.android.fmradio", "com.miui.fmradio"},
			{R.drawable.s60_whatsapp,  "com.whatsapp", "com.whatsapp.w4b"},
			{R.drawable.s60_youtube,   "com.google.android.youtube"},
			{R.drawable.s60_skype,     "com.skype.raider", "com.skype.android"},
			{R.drawable.s60_downloads, "com.android.providers.downloads.ui"},
			{R.drawable.s60_books,     "com.google.android.apps.books", "com.amazon.kindle"},
	};

	// ── 缓存 ──
	private static final Map<String, Integer> cache = new HashMap<>();
	/** 上次扫描时的启动器包名集合，用于检测应用安装/卸载变化 */
	private static Set<String> lastKnownPackages = null;

	// ── 运行时兜底：关键词模糊匹配（包名包含任一关键词则命中）──
	private static final Object[][] FUZZY_FALLBACK = {
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
			{R.drawable.s60_video_player, "video", "movie", "player", "media", "mxplayer", "bilibili", "douyin", "tiktok"},
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
			{R.drawable.s60_sdcard, "sdcard"},
	};

	// ── 工厂方法 ──
	private static Intent intent(String action) {
		Intent i = new Intent(action);
		// 部分 ROM 的意图匹配需要添加 CATEGORY_DEFAULT
		i.addCategory(Intent.CATEGORY_DEFAULT);
		return i;
	}
	private static Intent appCategory(String category) {
		Intent i = new Intent(Intent.ACTION_MAIN);
		i.addCategory(category);
		return i;
	}

	/**
	 * 初始化 / 刷新图标缓存。仅在应用列表发生变化时才重新扫描意图。
	 * 应在 {@code loadApps()} 的前期调用一次。
	 */
	public static void init(PackageManager pm) {
		// 获取当前所有启动器应用的包名集合
		Intent launcher = new Intent(Intent.ACTION_MAIN);
		launcher.addCategory(Intent.CATEGORY_LAUNCHER);
		List<ResolveInfo> allApps = pm.queryIntentActivities(launcher, 0);
		Set<String> currentPkgs = new HashSet<>();
		for (ResolveInfo ri : allApps) {
			if (ri.activityInfo != null) currentPkgs.add(ri.activityInfo.packageName);
		}

		// 包名集合未变则直接复用缓存
		if (lastKnownPackages != null && lastKnownPackages.equals(currentPkgs)) {
			NokiaLog.d("S60IconMap", "init: 应用列表未变化，复用缓存 (" + cache.size() + " 项)");
			return;
		}

		long start = System.currentTimeMillis();
		cache.clear();

		// 第 1 层：意图探测（优先级从高到低，先命中先得）
		for (Probe probe : PROBES) {
			List<ResolveInfo> hits = pm.queryIntentActivities(probe.intent, 0);
			for (ResolveInfo ri : hits) {
				if (ri.activityInfo == null) continue;
				String pkg = ri.activityInfo.packageName;
				if (!cache.containsKey(pkg)) {
					cache.put(pkg, probe.iconResId);
				}
			}
		}

		// 第 2 层：精确包名 Fallback（意图匹配不到的应用补上）
		for (Object[] row : EXACT_FALLBACK) {
			int resId = (int) row[0];
			for (int i = 1; i < row.length; i++) {
				String pkg = (String) row[i];
				if (!cache.containsKey(pkg)) {
					cache.put(pkg, resId);
				}
			}
		}

		lastKnownPackages = currentPkgs;
		long elapsed = System.currentTimeMillis() - start;
		NokiaLog.i("S60IconMap", "init: 扫描完成，缓存 " + cache.size() + " 项，耗时 " + elapsed + "ms");
	}

	/**
	 * 根据包名查找对应的 S60 图标资源 ID。
	 * 三层互补策略（任一命中即返回）：
	 *   1. 缓存（意图匹配 + 精确包名，init 时构建）
	 *   2. 关键词模糊匹配（运行时兜底）
	 */
	@DrawableRes
	public static int getIcon(String packageName) {
		if (packageName == null) return 0;

		// 第 1 层：缓存（意图匹配 + 精确包名）
		Integer resId = cache.get(packageName);
		if (resId != null) return resId;

		// 第 2 层：关键词模糊匹配（运行时，覆盖未命中缓存的包名）
		String lower = packageName.toLowerCase();
		for (Object[] entry : FUZZY_FALLBACK) {
			int iconRes = (int) entry[0];
			for (int i = 1; i < entry.length; i++) {
				if (lower.contains((String) entry[i])) {
					return iconRes;
				}
			}
		}

		return 0;
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
