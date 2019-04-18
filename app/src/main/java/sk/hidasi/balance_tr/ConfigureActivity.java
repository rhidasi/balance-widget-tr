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
import android.content.Intent;
import android.os.Bundle;
import com.google.android.material.textfield.TextInputLayout;
import androidx.appcompat.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * The configuration screen for the {@link BalanceWidget BalanceWidget} AppWidget.
 */
public class ConfigureActivity extends AppCompatActivity implements TextWatcher, SeekBar.OnSeekBarChangeListener {

	private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
	private static final int[] mUpdateMinutes = {1, 5, 10, 15, 30, 45, 60, 120, 240, 480, 720, 1440};

	@BindView(R.id.appwidget_card_serial)
	TextInputLayout mSerialNumber;
	@BindView(R.id.appwidget_pan_4_digits)
	TextInputLayout mFourDigits;
	@BindView(R.id.update_duration_seekbar)
	SeekBar mDurationSeekBar;
	@BindView(R.id.update_duration_text)
	TextView mDurationText;
	@BindView(R.id.dark_theme)
	Switch mDarkTheme;
	@BindView(R.id.save_button)
	Button mAddButton;

	@OnClick(R.id.save_button)
	public void saveButton() {
		// When the button is clicked, store the string locally
		final String serial = mSerialNumber.getEditText().getText().toString();
		final String fourDigits = mFourDigits.getEditText().getText().toString();
		final int updateDuration = progressToMinutes(mDurationSeekBar.getProgress());
		final boolean darkTheme = mDarkTheme.isChecked();
		BalanceWidgetHelper.saveWidgetPrefs(this, mAppWidgetId, serial, fourDigits, updateDuration, darkTheme);

		// It is the responsibility of the configuration activity to update the app widget
		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
		BalanceWidgetHelper.createHttpRequest(this, appWidgetManager, mAppWidgetId, false);

		// Make sure we pass back the original appWidgetId
		Intent resultValue = new Intent();
		resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
		setResult(RESULT_OK, resultValue);
		finish();
	}

	public ConfigureActivity() {
		super();
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		// Set the result to CANCELED.  This will cause the widget host to cancel
		// out of the widget placement if the user presses the back button.
		setResult(RESULT_CANCELED);

		setContentView(R.layout.activity_configure);
		ButterKnife.bind(this);

		mSerialNumber.getEditText().addTextChangedListener(this);
		mFourDigits.getEditText().addTextChangedListener(this);
		mDurationSeekBar.setOnSeekBarChangeListener(this);

		// Find the widget id from the intent.
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
		}

		// If this activity was started with an intent without an app widget ID, finish with an error.
		if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
			finish();
		}

		loadStoredValues();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return BalanceWidgetHelper.onOptionsItemSelected(this, item);
	}

	private void loadStoredValues() {

		final String serial = BalanceWidgetHelper.loadWidgetSerial(this, mAppWidgetId);
		mSerialNumber.getEditText().setText(serial);
		final String four = BalanceWidgetHelper.loadWidgetFourDigits(this, mAppWidgetId);
		mFourDigits.getEditText().setText(four);
		final int minutes = BalanceWidgetHelper.loadWidgetUpdateMinutes(this, mAppWidgetId);
		final int oldProgress = mDurationSeekBar.getProgress();
		mDurationSeekBar.setProgress(minutesToProgress(minutes));
		if (oldProgress == mDurationSeekBar.getProgress()) {
			// trigger onProgressChange explicitly
			onProgressChanged(mDurationSeekBar, oldProgress, false);
		}
		final boolean darkTheme = BalanceWidgetHelper.loadWidgetDarkTheme(this, mAppWidgetId);
		mDarkTheme.setChecked(darkTheme);
	}

	@Override
	public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
	}

	@Override
	public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
	}

	@Override
	public void afterTextChanged(Editable editable) {
		final boolean serialOk = mSerialNumber.getEditText().length() == 10;
		final boolean fourOk = mFourDigits.getEditText().length() == 4;
		mSerialNumber.setError(serialOk || mSerialNumber.getEditText().length() == 0 ? null : getString(R.string.enter_10_digits));
		mFourDigits.setError(fourOk || mFourDigits.getEditText().length() == 0 ? null : getString(R.string.enter_4_digits));
		mAddButton.setEnabled(serialOk && fourOk);
		if (serialOk && !fourOk) {
			mFourDigits.requestFocus();
		}
		if (fourOk && !serialOk) {
			mSerialNumber.requestFocus();
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
		String text;
		final int minutes = progressToMinutes(i);
		if (minutes < 60) {
			text = getResources().getQuantityString(R.plurals.update_minutes, minutes, minutes);
		} else {
			final int hours = minutes / 60;
			text = getResources().getQuantityString(R.plurals.update_hours, hours, hours);
		}
		mDurationText.setText(text);
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
	}

	private int progressToMinutes(final int progress) {
		return mUpdateMinutes[progress];
	}

	private int minutesToProgress(final int minutes) {
		int index = 0;
		while (index < mUpdateMinutes.length && mUpdateMinutes[index] < minutes) index++;
		return index;
	}
}
