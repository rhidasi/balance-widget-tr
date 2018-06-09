/*
 * Copyright (C) 2018 Robert Hidasi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sk.hidasi.balance_tr;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Helper methods for the {@link BalanceWidget BalanceWidget} AppWidget and the {@link BalanceWidgetConfigureActivity} Activity.
 */
public class BalanceWidgetHelper {

	private static final String TAG = BalanceWidgetHelper.class.getSimpleName();
	private static final String PREFS_NAME = "sk.hidasi.balance_tr.BalanceWidget";
	private static final String PREF_PREFIX_TEXT = "appwidget_text_";
	private static final String PREF_PREFIX_SERIAL = "appwidget_serial_";
	private static final String PREF_PREFIX_FOUR_DIGITS = "appwidget_four_digits_";
	private static final String PREF_PREFIX_UPDATE_MINUTES = "appwidget_update_minutes_";
	private static final String PREF_PREFIX_DARK_THEME = "appwidget_dark_theme_";

	public static void createHttpRequest(final Context context, final AppWidgetManager appWidgetManager, final int appWidgetId) {

		final String serial = loadWidgetSerial(context, appWidgetId);
		final String fourDigits = loadWidgetFourDigits(context, appWidgetId);

		if (serial == null || serial.length() != 10 || fourDigits == null || fourDigits.length() != 4)
			return;

		final HttpUrl url = new HttpUrl.Builder()
				.scheme("http")
				.host("www.trkarta.sk")
				.addPathSegment("balance")
				.addQueryParameter("card_serial", serial)
				.addQueryParameter("pan_4_digits", fourDigits)
				.build();
		final Request request = new Request.Builder()
				.url(url)
				.build();

		final OkHttpClient client = new OkHttpClient.Builder()
				.connectTimeout(10, TimeUnit.SECONDS)
				.readTimeout(10, TimeUnit.SECONDS)
				.build();

		Log.d(TAG, "Sending request...");
		client.newCall(request).enqueue(new Callback() {

			@Override
			public void onFailure(final Call call, IOException e) {
				Log.d(TAG, "Request failed");
				// shedule next update
				BalanceWidget.updateAppWidget(context, appWidgetManager, appWidgetId, false);
			}

			@Override
			public void onResponse(Call call, final Response response) throws IOException {
					final String content = response.body().string();
					Log.d(TAG, "Request response = " + content);
				try {
					final JSONObject json = new JSONObject(content);
					final boolean resultOk = json.getBoolean("result");
					String text;
					if (resultOk) {
						text = json.getString("balance") + "€";
					} else {
						text = context.getString(R.string.widget_text_error);
					}
					saveWidgetText(context, appWidgetId, text);
					BalanceWidget.updateAppWidget(context, appWidgetManager, appWidgetId, true);
				} catch (JSONException e) {
					final String text = context.getString(R.string.widget_text_loading);
					saveWidgetText(context, appWidgetId, text);
					BalanceWidget.updateAppWidget(context, appWidgetManager, appWidgetId, false);
				}
			}
		});
	}

	static String loadWidgetText(Context context, int appWidgetId) {
		SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
		return  prefs.getString(PREF_PREFIX_TEXT + appWidgetId, null);
	}

	static String loadWidgetSerial(Context context, int appWidgetId) {
		SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
		return  prefs.getString(PREF_PREFIX_SERIAL + appWidgetId, null);
	}

	static String loadWidgetFourDigits(Context context, int appWidgetId) {
		SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
		return  prefs.getString(PREF_PREFIX_FOUR_DIGITS + appWidgetId, null);
	}

	static int loadWidgetUpdateMinutes(Context context, int appWidgetId) {
		SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
		return  prefs.getInt(PREF_PREFIX_UPDATE_MINUTES + appWidgetId, 30);
	}

	static boolean loadWidgetDarkTheme(Context context, int appWidgetId) {
		SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
		return  prefs.getBoolean(PREF_PREFIX_DARK_THEME + appWidgetId, false);
	}

	static void saveWidgetText(Context context, int appWidgetId, String result) {
		Log.d(TAG, "saveWidgetText(), appWidgetId=" + appWidgetId);
		SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
		prefs.putString(PREF_PREFIX_TEXT + appWidgetId, result);
		prefs.apply();
	}

	static void saveWidgetPrefs(Context context, int appWidgetId, String serial, String fourDigits, int updateMinutes, boolean darkTheme) {
		Log.d(TAG, "saveWidgetPrefs(), appWidgetId=" + appWidgetId);
		SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
		prefs.putString(PREF_PREFIX_SERIAL + appWidgetId, serial);
		prefs.putString(PREF_PREFIX_FOUR_DIGITS + appWidgetId, fourDigits);
		prefs.putInt(PREF_PREFIX_UPDATE_MINUTES + appWidgetId, updateMinutes);
		prefs.putBoolean(PREF_PREFIX_DARK_THEME + appWidgetId, darkTheme);
		prefs.apply();
	}

	static void deleteWidgetPrefs(Context context, int appWidgetId) {
		SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
		prefs.remove(PREF_PREFIX_TEXT + appWidgetId);
		prefs.remove(PREF_PREFIX_SERIAL + appWidgetId);
		prefs.remove(PREF_PREFIX_FOUR_DIGITS + appWidgetId);
		prefs.apply();
	}
}
