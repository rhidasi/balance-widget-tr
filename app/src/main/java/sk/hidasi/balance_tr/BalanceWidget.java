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

	public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {

		final CharSequence widgetText = BalanceWidgetHelper.loadWidgetText(context, appWidgetId);
		// Construct the RemoteViews object
		final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.balance_widget);
		views.setTextViewText(R.id.widget_text, widgetText);

		final AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

		{
			Intent intent = new Intent(context, BalanceWidget.class);
			intent.setAction(ACTION_WIDGET_REFRESH);
			intent.putExtra(WIDGET_ID, appWidgetId);
			final PendingIntent pending = PendingIntent.getBroadcast(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			views.setOnClickPendingIntent(R.id.widget_layout, pending);
			final long interval = 1000 * 60 * BalanceWidgetHelper.loadWidgetUpdateMinutes(context, appWidgetId);
			alarm.cancel(pending);
			alarm.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + interval, pending);
		}

		{
			Intent intent = new Intent(context, BalanceWidget.class);
			intent.setAction(ACTION_WIDGET_SETTINGS);
			intent.putExtra(WIDGET_ID, appWidgetId);
			final PendingIntent pending = PendingIntent.getBroadcast(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			views.setOnClickPendingIntent(R.id.widget_settings, pending);
		}

		// Instruct the widget manager to update the widget
		appWidgetManager.updateAppWidget(appWidgetId, views);
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

		if (ACTION_WIDGET_REFRESH.equals(intent.getAction())) {
			final int appWidgetId = intent.getIntExtra(WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
			if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
				AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
				BalanceWidgetHelper.createHttpRequest(context, appWidgetManager, appWidgetId);
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

