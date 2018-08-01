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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.RemoteViews;

/**
 * Implementation of App Widget functionality.
 * App Widget Configuration implemented in {@link BalanceWidgetConfigureActivity BalanceWidgetConfigureActivity}
 */
public class BalanceWidget extends AppWidgetProvider {

	private static final String ACTION_WIDGET_REFRESH = "sk.hidasi.action_widget_refresh";
	private static final String ACTION_WIDGET_SETTINGS = "sk.hidasi.action_widget_settings";
	private static final String WIDGET_ID = "widget_id";
	private static final int ON_FAILURE_RETRY_MINUTES = 5;

	public static void updateAppWidget(final Context context, final AppWidgetManager appWidgetManager, int appWidgetId, boolean requestOk) {

		final CharSequence widgetText = BalanceWidgetHelper.loadWidgetText(context, appWidgetId);
		final boolean darkTheme = BalanceWidgetHelper.loadWidgetDarkTheme(context, appWidgetId);

		// Construct the RemoteViews object
		final RemoteViews views = new RemoteViews(context.getPackageName(), darkTheme ? R.layout.balance_widget_dark : R.layout.balance_widget);
		views.setTextViewText(R.id.widget_text, widgetText);

		final PendingIntent refreshIntent = createPendingIntent(context, appWidgetId, ACTION_WIDGET_REFRESH);
		views.setOnClickPendingIntent(R.id.widget_layout, refreshIntent);
		final AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		final long trigger = getAlarmTrigger(context, appWidgetId, requestOk);
		alarm.cancel(refreshIntent);
		alarm.set(AlarmManager.ELAPSED_REALTIME, trigger, refreshIntent);

		final PendingIntent settingsIntent = createPendingIntent(context, appWidgetId, ACTION_WIDGET_SETTINGS);
		views.setOnClickPendingIntent(R.id.widget_settings, settingsIntent);

		// Instruct the widget manager to update the widget
		appWidgetManager.updateAppWidget(appWidgetId, views);
	}

	private static PendingIntent createPendingIntent(final Context context, int appWidgetId, final String action)
	{
		Intent intent = new Intent(context, BalanceWidget.class);
		intent.setAction(action);
		intent.putExtra(WIDGET_ID, appWidgetId);
		return PendingIntent.getBroadcast(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	}

	private static long getAlarmTrigger(final Context context, int appWidgetId, boolean requestOk) {
		final long interval = 1000 * 60 * (requestOk ? BalanceWidgetHelper.loadWidgetUpdateMinutes(context, appWidgetId) : ON_FAILURE_RETRY_MINUTES);
		return SystemClock.elapsedRealtime() + interval;
	}

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		// There may be multiple widgets active, so update all of them
		for (int appWidgetId : appWidgetIds) {
			BalanceWidgetHelper.createHttpRequest(context, appWidgetManager, appWidgetId);
		}
	}

	@Override
	public void onDeleted(Context context, int[] appWidgetIds) {
		// When the user deletes the widget, delete the preference associated with it.
		for (int appWidgetId : appWidgetIds) {
			BalanceWidgetHelper.deleteWidgetPrefs(context, appWidgetId);
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

		final String action = intent.getAction();

		if (ACTION_WIDGET_REFRESH.equals(action)) {
			final int appWidgetId = intent.getIntExtra(WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
			if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
				AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
				BalanceWidgetHelper.createHttpRequest(context, appWidgetManager, appWidgetId);
			}
		} else if (ACTION_WIDGET_SETTINGS.equals(action)) {
			final int widgetId = intent.getIntExtra(WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
			if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
				Intent configIntent = new Intent(context, BalanceWidgetConfigureActivity.class);
				configIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
				context.startActivity(configIntent);
			}
		}
	}
}

