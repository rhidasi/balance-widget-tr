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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.SystemClock;
import androidx.annotation.NonNull;

import android.util.Log;
import android.widget.RemoteViews;

/**
 * Implementation of App Widget functionality.
 * App Widget Configuration implemented in {@link ConfigureActivity ConfigureActivity}
 */
public class BalanceWidget extends AppWidgetProvider {

	private static final String TAG = BalanceWidget.class.getSimpleName();

	public static final String ACTION_WIDGET_REFRESH = "sk.hidasi.action_widget_refresh";
	public static final String ACTION_WIDGET_CLICK = "sk.hidasi.action_widget_click";
	public static final String ACTION_WIDGET_CONFIG = "sk.hidasi.action_widget_config";

	private static final String WIDGET_ID = "widget_id";
	private static final long DOUBLE_CLICK_DELAY = 250;

	private final Handler mHandler = new Handler();

	public static void updateAppWidget(final Context context, final AppWidgetManager appWidgetManager, int appWidgetId, long nextUpdateInSeconds) {

		final String widgetText = BalanceWidgetHelper.loadWidgetText(context, appWidgetId);
		final boolean darkTheme = BalanceWidgetHelper.loadWidgetDarkTheme(context, appWidgetId);
		final boolean updateFailed = BalanceWidgetHelper.loadWidgetUpdateFailed(context, appWidgetId);

		final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.balance_widget);
		final Resources resources = context.getResources();

		final BitmapFactory.Options opt = new BitmapFactory.Options();
		opt.inMutable = true;
		opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
		final Bitmap bmp = BitmapFactory.decodeResource(resources, darkTheme ? R.drawable.ic_widget_dark : R.drawable.ic_widget, opt);
		final Canvas canvas = new Canvas(bmp);
		final int width = canvas.getWidth();
		final int height = canvas.getHeight();
		canvas.drawColor(0xE6FFFFFF, PorterDuff.Mode.DST_IN);

		if (widgetText != null) {
			final Paint textPaint = new Paint();
			textPaint.setStyle(Paint.Style.FILL);
			textPaint.setColor(darkTheme ? Color.WHITE : Color.BLACK);
			textPaint.setAlpha(updateFailed ? 192 : 255);
			textPaint.setTextSize(height * 0.23f);
			textPaint.setTextAlign(Paint.Align.CENTER);

			textPaint.setAntiAlias(true);
			textPaint.setTypeface(Typeface.DEFAULT_BOLD);

			int xPos = (int) (0.54 * width);
			int yPos = (int) (0.56 * height - (textPaint.descent() + textPaint.ascent()) / 2.);
			canvas.drawText(widgetText, xPos, yPos, textPaint);
		}

		views.setImageViewBitmap(R.id.imageView, bmp);

		if (nextUpdateInSeconds > 0) {
			final AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
			if (alarm != null) {
				final long triggerTime = SystemClock.elapsedRealtime() + nextUpdateInSeconds * 60 * 1000;
				final PendingIntent refreshIntent = createPendingIntent(context, appWidgetId, ACTION_WIDGET_REFRESH);
				alarm.cancel(refreshIntent);
				alarm.set(AlarmManager.ELAPSED_REALTIME, triggerTime, refreshIntent);
			} else {
				Log.e(TAG, "Could not get AlarmManager system service.");
			}
		}

		final PendingIntent settingsIntent = createPendingIntent(context, appWidgetId, ACTION_WIDGET_CLICK);
		views.setOnClickPendingIntent(R.id.imageView, settingsIntent);

		// Instruct the widget manager to update the widget
		appWidgetManager.updateAppWidget(appWidgetId, views);
	}

	@NonNull
	private static PendingIntent createPendingIntent(final Context context, int appWidgetId, final String action) {
		Intent intent = new Intent(context, BalanceWidget.class);
		intent.setAction(action);
		intent.putExtra(WIDGET_ID, appWidgetId);
		return PendingIntent.getBroadcast(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	}

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		// There may be multiple widgets active, so update all of them
		for (int appWidgetId : appWidgetIds) {
			updateAppWidget(context, appWidgetManager, appWidgetId, 0);
			BalanceWidgetHelper.createHttpRequest(context, appWidgetManager, appWidgetId, false);
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

		final int widgetId = intent.getIntExtra(WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
		if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID)
			return;

		String action = intent.getAction();
		if (ACTION_WIDGET_REFRESH.equals(action)) {
			AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
			BalanceWidgetHelper.createHttpRequest(context, appWidgetManager, widgetId, false);
		}
		if (ACTION_WIDGET_CLICK.equals(action)) {
			final long currentClickMillis = System.currentTimeMillis();
			final long lastClickMillis = BalanceWidgetHelper.loadWidgetClickMillis(context, widgetId);
			mHandler.removeCallbacksAndMessages(null);
			if (currentClickMillis - lastClickMillis > DOUBLE_CLICK_DELAY) {
				mHandler.postDelayed(() -> {
					// first click refresh
					AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
					BalanceWidgetHelper.createHttpRequest(context, appWidgetManager, widgetId, true);
				}, DOUBLE_CLICK_DELAY);
			} else {
				// double click, open settings
				action = ACTION_WIDGET_CONFIG;
			}
			BalanceWidgetHelper.saveWidgetClickMillis(context, widgetId, currentClickMillis);
		}
		if (ACTION_WIDGET_CONFIG.equals(action)) {
			final Intent configIntent = new Intent(context, ConfigureActivity.class);
			configIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
			context.startActivity(configIntent);
		}
	}
}

