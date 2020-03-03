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

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.view.View;
import android.widget.Toast;

import sk.hidasi.balance_tr.databinding.ActivityAboutBinding;

public class AboutActivity extends AppCompatActivity {

    private ActivityAboutBinding mBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        setExtraInformation();

        final PackageManager pm = getPackageManager();
        final ComponentName componentName = new ComponentName(this, MainActivity.class);
        final boolean iconDisabled = pm.getComponentEnabledSetting(componentName) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (iconDisabled) {
            mBinding.restoreLauncherIcon.setVisibility(View.VISIBLE);
        }
    }

    private void setExtraInformation() {
        mBinding.versionInfo.setText(getString(R.string.app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, Build.VERSION.RELEASE, Build.VERSION.SDK_INT));
        mBinding.packageName.setText(BuildConfig.APPLICATION_ID);
    }

    public void onRate(@NonNull View view) {
        String url = "market://details?id=" + BuildConfig.APPLICATION_ID;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(view.getContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public void onRestoreLauncherIcon(@NonNull View view) {
        AlertDialog.Builder dlgAlert  = new AlertDialog.Builder(this);
        dlgAlert.setTitle(R.string.restore_launcher_icon);
        dlgAlert.setMessage(R.string.restore_launcher_icon_warning);
        dlgAlert.setPositiveButton(android.R.string.yes, (dialogInterface, i) -> {
            final PackageManager pm = getPackageManager();
            final ComponentName componentName = new ComponentName(this, MainActivity.class);
            pm.setComponentEnabledSetting(componentName,PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0);
        });
        dlgAlert.setNegativeButton(android.R.string.no, null);
        dlgAlert.create().show();
    }

    @Override
    public boolean onSupportNavigateUp(){
        finish();
        return true;
    }
}