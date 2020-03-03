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

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

/**
 * Helper methods for the {@link BalanceWidget BalanceWidget} AppWidget and the {@link ConfigureActivity} Activity.
 */
class BalanceWidgetHelper {

	private static final String TAG = BalanceWidgetHelper.class.getSimpleName();

	private static final String PREFS_NAME = "sk.hidasi.balance_tr.BalanceWidget";
	private static final String PREF_PREFIX_TEXT = "appwidget_text_";
	private static final String PREF_PREFIX_SERIAL = "appwidget_serial_";
	private static final String PREF_PREFIX_FOUR_DIGITS = "appwidget_four_digits_";
	private static final String PREF_PREFIX_UPDATE_MINUTES = "appwidget_update_minutes_";
	private static final String PREF_PREFIX_DARK_THEME = "appwidget_dark_theme_";
	private static final String PREF_PREFIX_UPDATE_FAILED = "appwidget_update_failed_";
	private static final String PREF_PREFIX_MILLIS = "appwidget_millis_";
	private static final String PREF_PREFIX_LAST_UPDATE_SUCCESS = "appwidget_last_update_success_";

	private static final String WIDGET_ID = "widget_id";
	private static final String CHANNEL_ID = "widget_channel";

	private static final String CANCEL_TAG = "CancelTag";

	private static NotificationChannel mNotificationChannel;
	private static RequestQueue mRequestQueue;
	private static long mFailureRetrySeconds;

	static void createHttpRequest(final Context context, final AppWidgetManager appWidgetManager, final int appWidgetId, final boolean fromUser) {

		final String serial = loadWidgetSerial(context, appWidgetId);
		final String fourDigits = loadWidgetFourDigits(context, appWidgetId);
		final long updateInSeconds = 60 * loadWidgetUpdateMinutes(context, appWidgetId);

		if (serial == null || serial.length() != 10 || fourDigits == null || fourDigits.length() != 4) {
			Log.e(TAG, "Invalid serial number or four digits");
			return;
		}

		if (BuildConfig.DEBUG) {
			// special case for making screenshots
			if (serial.equals("1234567890") && fourDigits.equals("1234")) {
				saveWidgetText(context, appWidgetId, "12,30€");
				BalanceWidget.updateAppWidget(context, appWidgetManager, appWidgetId, 1000);
				return;
			}
		}

		if (!testNetwork(context)) {
			// no network connection, just schedule next update
			BalanceWidget.updateAppWidget(context, appWidgetManager, appWidgetId, updateInSeconds);
			if (fromUser) {
				// notify user about the reason we will not update the widget
				if (!isIgnoringBatteryOptimizations(context)) {
					Toast.makeText(context, R.string.turn_off_battery_optimization, Toast.LENGTH_LONG).show();
				} else {
					Toast.makeText(context, R.string.no_connection, Toast.LENGTH_SHORT).show();
				}
			} else if (loadWidgetLastUpdateSuccess(context, appWidgetId) == 0) {
				Toast.makeText(context, R.string.no_connection, Toast.LENGTH_SHORT).show();
			}
			return;
		}

		if (mRequestQueue != null) {
			mRequestQueue.cancelAll(CANCEL_TAG);
		}

		mRequestQueue = Volley.newRequestQueue(context);
		final String url = String.format("https://www.trkarta.sk/balance?card_serial=%1$s&pan_4_digits=%2$s", serial, fourDigits);
		final String oldText = loadWidgetText(context, appWidgetId);

		final JsonObjectRequest jsonRequest = new JsonObjectRequest(Request.Method.GET, url, null,
				response -> {
					if (response == null) {
						Log.d(TAG, "Request response = null");
						saveWidgetText(context, appWidgetId, oldText);
						saveWidgetUpdateFailed(context, appWidgetId, true);
						BalanceWidget.updateAppWidget(context, appWidgetManager, appWidgetId, getNextRetrySeconds(updateInSeconds));
					} else {
						Log.d(TAG, "Request response = " + response);
						try {
							final boolean resultOk = response.getBoolean("result");
							String text;
							if (resultOk) {
								text = response.getString("balance") + "€";
							} else {
								text = context.getString(R.string.widget_text_error);
								showErrorNotification(context, appWidgetId);
							}
							saveWidgetText(context, appWidgetId, text);
							saveLastUpdateSuccess(context, appWidgetId, System.currentTimeMillis());
							mFailureRetrySeconds = 0;
							BalanceWidget.updateAppWidget(context, appWidgetManager, appWidgetId, updateInSeconds);
						} catch (JSONException e) {
							Log.e(TAG, "JSONException: " + e.getMessage());
						}
					}
				},
				error -> {
					Log.d(TAG, "Request failed");
					saveWidgetText(context, appWidgetId, oldText);
					saveWidgetUpdateFailed(context, appWidgetId, true);
					BalanceWidget.updateAppWidget(context, appWidgetManager, appWidgetId, getNextRetrySeconds(updateInSeconds));
				});

		final String text = context.getString(R.string.widget_text_loading);
		saveWidgetText(context, appWidgetId, text);
		saveWidgetUpdateFailed(context, appWidgetId, false);
		BalanceWidget.updateAppWidget(context, appWidgetManager, appWidgetId, 0);

		jsonRequest.setTag(CANCEL_TAG);
		Log.d(TAG, "Sending request... " + url);
		mRequestQueue.add(jsonRequest);
	}

	private static long getNextRetrySeconds(final long maxUpdateInSeconds) {
		mFailureRetrySeconds = Math.min(maxUpdateInSeconds, (mFailureRetrySeconds + 1) * 2);
		return mFailureRetrySeconds;
	}

	private static boolean isIgnoringBatteryOptimizations(final Context context) {
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
			if (notificationManager != null) {
				notificationManager.createNotificationChannel(mNotificationChannel);
			} else {
				Log.e(TAG, "Could not get NotificationManager system service.");
			}
		}
	}

	private static void showErrorNotification(final Context context, int widgetId) {

		final long lastSuccess =  loadWidgetLastUpdateSuccess(context, widgetId);
		if (lastSuccess != 0) {
			// the server returns spurious error responses sometimes
			// suppress the error message for one hour
			// another cause of the error response is an expired card
			if (System.currentTimeMillis() - lastSuccess < 60 * 60 * 1000)
				return;
		}

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
		final ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connectivityManager != null) {
			final NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
			return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
		} else {
			Log.e(TAG, "Could not get ConnectivityManager system service.");
			return false;
		}
	}

	static boolean onOptionsItemSelected(final Activity activity, final MenuItem item) {
		// Handle menu item selection
		if (item.getItemId() == R.id.about) {
			Intent intent = new Intent(activity, AboutActivity.class);
			activity.startActivity(intent);
			return true;
		}
		return activity.onOptionsItemSelected(item);
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

	private static void saveWidgetText(final Context context, int appWidgetId, final String text) {
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
		prefs.remove(PREF_PREFIX_LAST_UPDATE_SUCCESS + appWidgetId);
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
		prefs.remove(PREF_PREFIX_LAST_UPDATE_SUCCESS + appWidgetId);
		prefs.apply();
	}

	static boolean loadWidgetUpdateFailed(final Context context, int appWidgetId) {
		final SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
		return  prefs.getBoolean(PREF_PREFIX_UPDATE_FAILED + appWidgetId, false);
	}

	private static void saveWidgetUpdateFailed(final Context context, int appWidgetId, final boolean failed) {
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

	private static long loadWidgetLastUpdateSuccess(final Context context, int appWidgetId) {
		final SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
		return  prefs.getLong(PREF_PREFIX_LAST_UPDATE_SUCCESS + appWidgetId, 0);
	}

	private static void saveLastUpdateSuccess(final Context context, int appWidgetId, final long millis) {
		Log.d(TAG, "saveLastUpdateSuccess(), appWidgetId=" + appWidgetId);
		final SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
		prefs.putLong(PREF_PREFIX_LAST_UPDATE_SUCCESS + appWidgetId, millis);
		prefs.apply();
	}
}
