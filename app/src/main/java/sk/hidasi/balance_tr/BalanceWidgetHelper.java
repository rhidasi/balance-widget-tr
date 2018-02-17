package sk.hidasi.balance_tr;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

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

	public static void createHttpRequest(final Context context, final AppWidgetManager appWidgetManager, final int appWidgetId) {

		final String serial = loadWidgetSerial(context, appWidgetId);
		final String fourDigits = loadWidgetFourDigits(context, appWidgetId);

		if (serial == null || serial.length() != 10 || fourDigits == null || fourDigits.length() != 4)
			return;

		Log.d(TAG, "Sending request...");
		HttpUrl url = new HttpUrl.Builder()
				.scheme("http")
				.host("www.trkarta.sk")
				.addPathSegment("balance")
				.addQueryParameter("card_serial", serial)
				.addQueryParameter("pan_4_digits", fourDigits)
				.build();
		Request request = new Request.Builder()
				.url(url)
				.build();
		OkHttpClient client = new OkHttpClient();
		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(final Call call, IOException e) {
				Log.d(TAG, "Request failed");
			}

			@Override
			public void onResponse(Call call, final Response response) throws IOException {
				final String content = response.body().string();
				Log.d(TAG, "Request response = " + content);
				String text;
				try {
					final JSONObject json = new JSONObject(content);
					final boolean resultOk = json.getBoolean("result");
					if (resultOk) {
						text = json.getString("balance") + "€";
					} else {
						text = context.getString(R.string.widget_text_error);
					}
				} catch (JSONException e) {
					text = context.getString(R.string.widget_text_loading);
				}
				saveWidgetText(context, appWidgetId, text);
				BalanceWidget.updateAppWidget(context, appWidgetManager, appWidgetId);
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

	static void saveWidgetText(Context context, int appWidgetId, String result) {
		Log.d(TAG, "saveWidgetText(), appWidgetId=" + appWidgetId);
		SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
		prefs.putString(PREF_PREFIX_TEXT + appWidgetId, result);
		prefs.apply();
	}

	static void saveWidgetPrefs(Context context, int appWidgetId, String serial, String fourDigits, int updateMinutes) {
		Log.d(TAG, "saveWidgetPrefs(), appWidgetId=" + appWidgetId);
		SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
		prefs.putString(PREF_PREFIX_SERIAL + appWidgetId, serial);
		prefs.putString(PREF_PREFIX_FOUR_DIGITS + appWidgetId, fourDigits);
		prefs.putInt(PREF_PREFIX_UPDATE_MINUTES + appWidgetId, updateMinutes);
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
