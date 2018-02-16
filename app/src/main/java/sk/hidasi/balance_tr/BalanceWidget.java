package sk.hidasi.balance_tr;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;

/**
 * Implementation of App Widget functionality.
 * App Widget Configuration implemented in {@link BalanceWidgetConfigureActivity BalanceWidgetConfigureActivity}
 */
public class BalanceWidget extends AppWidgetProvider {

	private static final String TAG = BalanceWidget.class.getSimpleName();
	private static final String ACTION_WIDGET_REFRESH = "sk.hidasi.action_widget_refresh";
	private static final String ACTION_WIDGET_SETTINGS = "sk.hidasi.action_widget_settings";
	private static final String WIDGET_ID = "widget_id";

	private static class GetUrlContentTask extends AsyncTask<String, Integer, String> {

		private final Context mContext;
		private final int mAppWidgetId;

		private GetUrlContentTask(final Context context, final int appWidgetId) {
			mContext = context;
			mAppWidgetId = appWidgetId;
		}

		protected String doInBackground(String... urls) {
			Log.d(TAG, "Sending request...");
			String content = "";
			try {
				final URL url = new URL(urls[0]);
				final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				connection.setRequestMethod("GET");
				connection.setDoOutput(true);
				connection.setConnectTimeout(5000);
				connection.setReadTimeout(5000);
				connection.connect();
				final BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				String line;
				while ((line = rd.readLine()) != null) {
					content += line + "\n";
				}
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (ProtocolException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			Log.d(TAG, "GET request = " + content);
			return content;
		}

		protected void onProgressUpdate(Integer... progress) {
		}

		protected void onPostExecute(String result) {
			if (!result.isEmpty()) {
				String text = mContext.getString(R.string.widget_text_loading);
				try {
					final JSONObject json = new JSONObject(result);
					final boolean resultOk = json.getBoolean("result");
					if (resultOk) {
						text = json.getString("balance") + "€";
					} else {
						Toast.makeText(mContext, R.string.wrong_result, Toast.LENGTH_SHORT).show();
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}
				BalanceWidgetConfigureActivity.saveWidgetText(mContext, mAppWidgetId, text);
				AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(mContext);
				BalanceWidget.updateAppWidget(mContext, appWidgetManager, mAppWidgetId, false);
			}
		}
	}

	static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
								int appWidgetId, boolean sendRequest) {

		final CharSequence widgetText = BalanceWidgetConfigureActivity.loadWidgetText(context, appWidgetId);
		// Construct the RemoteViews object
		final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.balance_widget);
		views.setTextViewText(R.id.widget_text, widgetText);

		{
			Intent intent = new Intent(context, BalanceWidget.class);
			intent.setAction(ACTION_WIDGET_REFRESH);
			intent.putExtra(WIDGET_ID, appWidgetId);
			final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			views.setOnClickPendingIntent(R.id.widget_layout, pendingIntent);
		}

		{
			Intent intent = new Intent(context, BalanceWidget.class);
			intent.setAction(ACTION_WIDGET_SETTINGS);
			intent.putExtra(WIDGET_ID, appWidgetId);
			final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			views.setOnClickPendingIntent(R.id.widget_settings, pendingIntent);
		}

		if (sendRequest) {
			final String serial = BalanceWidgetConfigureActivity.loadWidgetSerial(context, appWidgetId);
			final String fourDigits = BalanceWidgetConfigureActivity.loadWidgetFourDigits(context, appWidgetId);
			if (serial != null && serial.length() == 10 && fourDigits != null && fourDigits.length() == 4) {
				final String url = "http://www.trkarta.sk/balance?card_serial=" + serial + "&pan_4_digits=" + fourDigits;
				new GetUrlContentTask(context, appWidgetId).execute(url);
			}
		}

		// Instruct the widget manager to update the widget
		appWidgetManager.updateAppWidget(appWidgetId, views);
	}

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		// There may be multiple widgets active, so update all of them
		for (int appWidgetId : appWidgetIds) {
			updateAppWidget(context, appWidgetManager, appWidgetId, true);
		}
	}

	@Override
	public void onDeleted(Context context, int[] appWidgetIds) {
		// When the user deletes the widget, delete the preference associated with it.
		for (int appWidgetId : appWidgetIds) {
			BalanceWidgetConfigureActivity.deleteWidgetPrefs(context, appWidgetId);
		}
	}

	@Override
	public void onEnabled(Context context) {
		// Enter relevant functionality for when the first widget is created
	}

	@Override
	public void onDisabled(Context context) {
		// Enter relevant functionality for when the last widget is disabled
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		super.onReceive(context, intent);

		if (ACTION_WIDGET_REFRESH.equals(intent.getAction())) {
			final int widgetId = intent.getIntExtra(WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
			if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
				AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
				updateAppWidget(context, appWidgetManager, widgetId, true);
			}
		} else if (ACTION_WIDGET_SETTINGS.equals(intent.getAction())) {
			final int widgetId = intent.getIntExtra(WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
			if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
				Intent configIntent = new Intent(context, BalanceWidgetConfigureActivity.class);
				configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
				context.startActivity(configIntent);
			}
		}
	}
}

