package sk.hidasi.balance_tr;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * The configuration screen for the {@link BalanceWidget BalanceWidget} AppWidget.
 */
public class BalanceWidgetConfigureActivity extends Activity implements TextWatcher {

	private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

	@BindView(R.id.appwidget_card_serial)
	EditText mSerialNumber;
	@BindView(R.id.appwidget_pan_4_digits)
	EditText mFourDigits;
	@BindView(R.id.please_enter_10_digits)
	TextView mEnter10Digits;
	@BindView(R.id.please_enter_4_digits)
	TextView mEnter4Digits;
	@BindView(R.id.save_button)
	Button mAddButton;

	@OnClick(R.id.save_button)
	public void saveButton() {
		// When the button is clicked, store the string locally
		String serial = mSerialNumber.getText().toString();
		String fourDigits = mFourDigits.getText().toString();
		BalanceWidgetHelper.saveWidgetPrefs(this, mAppWidgetId, serial, fourDigits);

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

		mSerialNumber.addTextChangedListener(this);
		mFourDigits.addTextChangedListener(this);

		// Find the widget id from the intent.
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
		}

		// If this activity was started with an intent without an app widget ID, finish with an error.
		if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
			finish();
		}

		mSerialNumber.setText(BalanceWidgetHelper.loadWidgetSerial(this, mAppWidgetId));
		mFourDigits.setText(BalanceWidgetHelper.loadWidgetFourDigits(this, mAppWidgetId));
	}

	@Override
	public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
	}

	@Override
	public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
	}

	@Override
	public void afterTextChanged(Editable editable) {
		final boolean serialOk = mSerialNumber.length() == 10;
		final boolean fourOk = mFourDigits.length() == 4;
		mEnter10Digits.setVisibility(serialOk ? View.GONE : View.VISIBLE);
		mEnter4Digits.setVisibility(fourOk ? View.GONE : View.VISIBLE);
		mAddButton.setEnabled(serialOk && fourOk);
		if (serialOk && !fourOk) {
			mFourDigits.requestFocus();
		}
		if (fourOk && !serialOk) {
			mSerialNumber.requestFocus();
		}
	}
}
