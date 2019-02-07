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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Helper methods for the {@link BalanceWidget BalanceWidget} AppWidget and the {@link ConfigureActivity} Activity.
 */
class BalanceWidgetHelper {

	private static final String TAG = BalanceWidgetHelper.class.getSimpleName();
	private static final int ON_FAILURE_RETRY_MINUTES = 5;

	private static final String PREFS_NAME = "sk.hidasi.balance_tr.BalanceWidget";
	private static final String PREF_PREFIX_TEXT = "appwidget_text_";
	private static final String PREF_PREFIX_SERIAL = "appwidget_serial_";
	private static final String PREF_PREFIX_FOUR_DIGITS = "appwidget_four_digits_";
	private static final String PREF_PREFIX_UPDATE_MINUTES = "appwidget_update_minutes_";
	private static final String PREF_PREFIX_DARK_THEME = "appwidget_dark_theme_";
	private static final String PREF_PREFIX_UPDATE_FAILED = "appwidget_update_failed_";
	private static final String PREF_PREFIX_MILLIS = "appwidget_millis_";

	private static final String WIDGET_ID = "widget_id";
	private static final String CHANNEL_ID = "widget_channel";
	private static NotificationChannel mNotificationChannel;

	public static void createHttpRequest(final Context context, final AppWidgetManager appWidgetManager, final int appWidgetId, final boolean fromUser) {

		final String serial = loadWidgetSerial(context, appWidgetId);
		final String fourDigits = loadWidgetFourDigits(context, appWidgetId);
		final long updateInMinutes = loadWidgetUpdateMinutes(context, appWidgetId);

		if (serial == null || serial.length() != 10 || fourDigits == null || fourDigits.length() != 4) {
			Log.e(TAG, "Invalid serial number or four digits");
			return;
		}

		if (BuildConfig.DEBUG) {
			// detect "screenshot mode"
			if (serial.equals("1234567890") && fourDigits.equals("1234")) {
				saveWidgetText(context, appWidgetId, "12,30€");
				BalanceWidget.updateAppWidget(context, appWidgetManager, appWidgetId, 60);
				return;
			}
		}

		if (!testNetwork(context)) {
			// no network connection, just schedule next update
			BalanceWidget.updateAppWidget(context, appWidgetManager, appWidgetId, updateInMinutes);
			if (fromUser) {
				// notify user about the reason we will not update the widget
				if (!isIgnoringBatteryOptimizations(context)) {
					Toast.makeText(context, R.string.turn_off_battery_optimization, Toast.LENGTH_LONG).show();
				} else {
					Toast.makeText(context, R.string.no_connection, Toast.LENGTH_SHORT).show();
				}
			}
			return;
		}

		final HttpUrl url = new HttpUrl.Builder()
				.scheme("https")
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
				.retryOnConnectionFailure(false)
				.build();

		final String oldText = loadWidgetText(context, appWidgetId);
		final String text = context.getString(R.string.widget_text_loading);
		saveWidgetText(context, appWidgetId, text);
		saveWidgetUpdateFailed(context, appWidgetId, false);
		BalanceWidget.updateAppWidget(context, appWidgetManager, appWidgetId, 0);

		Log.d(TAG, "Sending request..." + request.toString());
		client.newCall(request).enqueue(new Callback() {

			@Override
			public void onFailure(final Call call, IOException e) {
				Log.d(TAG, "Request failed");
				saveWidgetText(context, appWidgetId, oldText);
				saveWidgetUpdateFailed(context, appWidgetId, true);
				BalanceWidget.updateAppWidget(context, appWidgetManager, appWidgetId, ON_FAILURE_RETRY_MINUTES);
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
						showErrorNotification(context, appWidgetId);
					}
					saveWidgetText(context, appWidgetId, text);
					BalanceWidget.updateAppWidget(context, appWidgetManager, appWidgetId, updateInMinutes);
				} catch (JSONException e) {
					Log.d(TAG, "Parsing result failed");
					saveWidgetText(context, appWidgetId, oldText);
					saveWidgetUpdateFailed(context, appWidgetId, true);
					BalanceWidget.updateAppWidget(context, appWidgetManager, appWidgetId, ON_FAILURE_RETRY_MINUTES);
				}
			}
		});
	}

	private static boolean isIgnoringBatteryOptimizations(final Context context)
	{
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
			return pm == null || !pm.isPowerSaveMode() || pm.isIgnoringBatteryOptimizations(context.getPackageName());
		}
		return true;
	}

	private static void createNotificationChannel(final Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mNotificationChannel == null) {
			final CharSequence name = context.getString(R.string.notification_channel_name);
			final String description = context.getString(R.string.notification_channel_description);
			mNotificationChannel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT);
			mNotificationChannel.setDescription(description);
			final NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
			notificationManager.createNotificationChannel(mNotificationChannel);
		}
	}

	private static void showErrorNotification(final Context context, int widgetId) {
		createNotificationChannel(context);

		Intent intent = new Intent(context, BalanceWidget.class);
		intent.setAction(BalanceWidget.ACTION_WIDGET_CONFIG);
		intent.putExtra(WIDGET_ID, widgetId);
		PendingIntent configIntent = PendingIntent.getBroadcast(context, widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		NotificationCompat.Style bigStyle = new NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notification_text));

		Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
				.setSmallIcon(R.drawable.ic_error_outline_black_24dp)
				.setContentTitle(context.getString(R.string.notification_title))
				.setContentText(context.getString(R.string.notification_text))
				.setStyle(bigStyle)
				.setPriority(NotificationCompat.PRIORITY_DEFAULT)
				.setContentIntent(configIntent)
				.setAutoCancel(true)
				.build();

		NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
		notificationManager.notify(widgetId, notification);
	}

	static private boolean testNetwork(final Context context) {
		final ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
		final NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
		return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
	}

	static boolean onOptionsItemSelected(final Activity activity, final MenuItem item) {
		// Handle menu item selection
		switch (item.getItemId()) {
			case R.id.about:
				Intent intent = new Intent(activity, AboutActivity.class);
				activity.startActivity(intent);
				return true;
			default:
				return activity.onOptionsItemSelected(item);
		}
	}

	static String loadWidgetText(final Context context, int appWidgetId) {
		final SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
		return  prefs.getString(PREF_PREFIX_TEXT + appWidgetId, null);
	}

	static String loadWidgetSerial(final Context context, int appWidgetId) {
		final SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
		return  prefs.getString(PREF_PREFIX_SERIAL + appWidgetId, null);
	}

	static String loadWidgetFourDigits(final Context context, int appWidgetId) {
		final SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
		return  prefs.getString(PREF_PREFIX_FOUR_DIGITS + appWidgetId, null);
	}

	static int loadWidgetUpdateMinutes(final Context context, int appWidgetId) {
		final SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
		return  prefs.getInt(PREF_PREFIX_UPDATE_MINUTES + appWidgetId, 30);
	}

	static boolean loadWidgetDarkTheme(final Context context, int appWidgetId) {
		final SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
		return  prefs.getBoolean(PREF_PREFIX_DARK_THEME + appWidgetId, false);
	}

	static void saveWidgetText(final Context context, int appWidgetId, final String text) {
		Log.d(TAG, "saveWidgetText(), appWidgetId=" + appWidgetId);
		final SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
		prefs.putString(PREF_PREFIX_TEXT + appWidgetId, text);
		prefs.apply();
	}

	static void saveWidgetPrefs(final Context context, int appWidgetId, final String serial, final String fourDigits, int updateMinutes, boolean darkTheme) {
		Log.d(TAG, "saveWidgetPrefs(), appWidgetId=" + appWidgetId);
		final SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
		prefs.putString(PREF_PREFIX_SERIAL + appWidgetId, serial);
		prefs.putString(PREF_PREFIX_FOUR_DIGITS + appWidgetId, fourDigits);
		prefs.putInt(PREF_PREFIX_UPDATE_MINUTES + appWidgetId, updateMinutes);
		prefs.putBoolean(PREF_PREFIX_DARK_THEME + appWidgetId, darkTheme);
		prefs.apply();
	}

	static void deleteWidgetPrefs(final Context context, int appWidgetId) {
		final SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
		prefs.remove(PREF_PREFIX_TEXT + appWidgetId);
		prefs.remove(PREF_PREFIX_SERIAL + appWidgetId);
		prefs.remove(PREF_PREFIX_FOUR_DIGITS + appWidgetId);
		prefs.remove(PREF_PREFIX_UPDATE_MINUTES + appWidgetId);
		prefs.remove(PREF_PREFIX_DARK_THEME + appWidgetId);
		prefs.remove(PREF_PREFIX_UPDATE_FAILED + appWidgetId);
		prefs.remove(PREF_PREFIX_MILLIS + appWidgetId);
		prefs.apply();
	}

	static boolean loadWidgetUpdateFailed(final Context context, int appWidgetId) {
		final SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
		return  prefs.getBoolean(PREF_PREFIX_UPDATE_FAILED + appWidgetId, false);
	}

	static void saveWidgetUpdateFailed(final Context context, int appWidgetId, final boolean failed) {
		Log.d(TAG, "saveWidgetUpdateFailed(), appWidgetId=" + appWidgetId);
		final SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
		prefs.putBoolean(PREF_PREFIX_UPDATE_FAILED + appWidgetId, failed);
		prefs.apply();
	}

	static long loadWidgetClickMillis(final Context context, int appWidgetId) {
		final SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
		return  prefs.getLong(PREF_PREFIX_MILLIS + appWidgetId, 0);
	}

	static void saveWidgetClickMillis(final Context context, int appWidgetId, final long millis) {
		Log.d(TAG, "saveWidgetClickMillis(), appWidgetId=" + appWidgetId);
		final SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
		prefs.putLong(PREF_PREFIX_MILLIS + appWidgetId, millis);
		prefs.apply();
	}
}
