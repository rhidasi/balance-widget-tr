package sk.hidasi.balance_tr;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * The configuration screen for the {@link BalanceWidget BalanceWidget} AppWidget.
 */
public class BalanceWidgetConfigureActivity extends AppCompatActivity implements TextWatcher, SeekBar.OnSeekBarChangeListener {

	private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
	private static final int mUpdateMinutes[] = {1, 5, 10, 15, 30, 45, 60, 120, 240, 480, 720, 1440};

	@BindView(R.id.appwidget_card_serial)
	TextInputLayout mSerialNumber;
	@BindView(R.id.appwidget_pan_4_digits)
	TextInputLayout mFourDigits;
	@BindView(R.id.update_duration_seekbar)
	SeekBar mDurationSeekBar;
	@BindView(R.id.update_duration_text)
	TextView mDurationText;
	@BindView(R.id.save_button)
	Button mAddButton;

	@OnClick(R.id.save_button)
	public void saveButton() {
		// When the button is clicked, store the string locally
		final String serial = mSerialNumber.getEditText().getText().toString();
		final String fourDigits = mFourDigits.getEditText().getText().toString();
		final int updateDuration = progressToMinutes(mDurationSeekBar.getProgress());
		BalanceWidgetHelper.saveWidgetPrefs(this, mAppWidgetId, serial, fourDigits, updateDuration);

		// It is the responsibility of the configuration activity to update the app widget
		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
		BalanceWidgetHelper.createHttpRequest(this, appWidgetManager, mAppWidgetId);

		// Make sure we pass back the original appWidgetId
		Intent resultValue = new Intent();
		resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
		setResult(RESULT_OK, resultValue);
		finish();
	}

	public BalanceWidgetConfigureActivity() {
		super();
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		// Set the result to CANCELED.  This will cause the widget host to cancel
		// out of the widget placement if the user presses the back button.
		setResult(RESULT_CANCELED);

		setContentView(R.layout.balance_widget_configure);
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

		mSerialNumber.getEditText().setText(BalanceWidgetHelper.loadWidgetSerial(this, mAppWidgetId));
		mFourDigits.getEditText().setText(BalanceWidgetHelper.loadWidgetFourDigits(this, mAppWidgetId));
		final int minutes = BalanceWidgetHelper.loadWidgetUpdateMinutes(this, mAppWidgetId);
		final int oldProgress = mDurationSeekBar.getProgress();
		mDurationSeekBar.setProgress(minutesToProgress(minutes));
		if (oldProgress == mDurationSeekBar.getProgress()) {
			// trigger onProgressChange explicitly
			onProgressChanged(mDurationSeekBar, oldProgress, false);
		}
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
		if (serialOk) {
			mSerialNumber.setErrorEnabled(false);
		} else {
			mSerialNumber.setError(getString(R.string.please_enter_10_digits));
		}
		if (fourOk) {
			mFourDigits.setErrorEnabled(false);
		} else {
			mFourDigits.setError(getString(R.string.please_enter_4_digits));
		}
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
