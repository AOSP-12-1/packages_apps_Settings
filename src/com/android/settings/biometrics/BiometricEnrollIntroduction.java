/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.settings.biometrics;

import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.View;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settings.password.ChooseLockGeneric;
import com.android.settings.password.ChooseLockSettingsHelper;

import com.google.android.setupcompat.item.FooterButton;
import com.google.android.setupdesign.span.LinkSpan;

/**
 * Abstract base class for the intro onboarding activity for biometric enrollment.
 */
public abstract class BiometricEnrollIntroduction extends BiometricEnrollBase
        implements LinkSpan.OnClickListener {

    private UserManager mUserManager;
    private boolean mHasPassword;
    private boolean mBiometricUnlockDisabledByAdmin;
    private TextView mErrorText;

    /**
     * @return true if the biometric is disabled by a device administrator
     */
    protected abstract boolean isDisabledByAdmin();

    /**
     * @return the layout resource
     */
    protected abstract int getLayoutResource();

    /**
     * @return the header resource for if the biometric has been disabled by a device administrator
     */
    protected abstract int getHeaderResDisabledByAdmin();

    /**
     * @return the default header resource
     */
    protected abstract int getHeaderResDefault();

    /**
     * @return the description resource for if the biometric has been disabled by a device admin
     */
    protected abstract int getDescriptionResDisabledByAdmin();

    /**
     * @return the cancel button
     */
    protected abstract FooterButton getCancelButton();

    /**
     * @return the next button
     */
    protected abstract FooterButton getNextButton();

    /**
     * @return the error TextView
     */
    protected abstract TextView getErrorTextView();

    /**
     * @return 0 if there are no errors, otherwise returns the resource ID for the error string
     * to be displayed.
     */
    protected abstract int checkMaxEnrolled();

    /**
     * @return the challenge generated by the biometric hardware
     */
    protected abstract long getChallenge();

    /**
     * @return one of the ChooseLockSettingsHelper#EXTRA_KEY_FOR_* constants
     */
    protected abstract String getExtraKeyForBiometric();

    /**
     * @return the intent for proceeding to the next step of enrollment. For Fingerprint, this
     * should lead to the "Find Sensor" activity. For Face, this should lead to the "Enrolling"
     * activity.
     */
    protected abstract Intent getEnrollingIntent();

    /**
     * @return the title to be shown on the ConfirmLock screen.
     */
    protected abstract int getConfirmLockTitleResId();

    /**
     * @param span
     */
    public abstract void onClick(LinkSpan span);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBiometricUnlockDisabledByAdmin = isDisabledByAdmin();

        setContentView(getLayoutResource());
        if (mBiometricUnlockDisabledByAdmin) {
            setHeaderText(getHeaderResDisabledByAdmin());
        } else {
            setHeaderText(getHeaderResDefault());
        }

        mErrorText = getErrorTextView();

        mUserManager = UserManager.get(this);
        updatePasswordQuality();

        if (!mHasPassword) {
            // No password registered, launch into enrollment wizard.
            launchChooseLock();
        } else {
            launchConfirmLock(getConfirmLockTitleResId(), getChallenge());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        final int errorMsg = checkMaxEnrolled();
        if (errorMsg == 0) {
            mErrorText.setText(null);
            getNextButton().setVisibility(View.VISIBLE);
        } else {
            mErrorText.setText(errorMsg);
            getNextButton().setText(getResources().getString(R.string.done));
            getNextButton().setVisibility(View.VISIBLE);
        }
    }

    private void updatePasswordQuality() {
        final int passwordQuality = new ChooseLockSettingsHelper(this).utils()
                .getActivePasswordQuality(mUserManager.getCredentialOwnerProfile(mUserId));
        mHasPassword = passwordQuality != DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
    }

    @Override
    protected void onNextButtonClick(View view) {
        if (checkMaxEnrolled() == 0) {
            // Lock thingy is already set up, launch directly to the next page
            launchNextEnrollingActivity(mToken);
        } else {
            setResult(RESULT_FINISHED);
            finish();
        }
    }

    private void launchChooseLock() {
        Intent intent = getChooseLockIntent();
        long challenge = getChallenge();
        intent.putExtra(ChooseLockGeneric.ChooseLockGenericFragment.MINIMUM_QUALITY_KEY,
                DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
        intent.putExtra(ChooseLockGeneric.ChooseLockGenericFragment.HIDE_DISABLED_PREFS, true);
        intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_HAS_CHALLENGE, true);
        intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE, challenge);
        intent.putExtra(getExtraKeyForBiometric(), true);
        if (mUserId != UserHandle.USER_NULL) {
            intent.putExtra(Intent.EXTRA_USER_ID, mUserId);
        }
        startActivityForResult(intent, CHOOSE_LOCK_GENERIC_REQUEST);
    }

    private void launchNextEnrollingActivity(byte[] token) {
        Intent intent = getEnrollingIntent();
        if (token != null) {
            intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN, token);
        }
        if (mUserId != UserHandle.USER_NULL) {
            intent.putExtra(Intent.EXTRA_USER_ID, mUserId);
        }
        startActivityForResult(intent, BIOMETRIC_FIND_SENSOR_REQUEST);
    }

    protected Intent getChooseLockIntent() {
        return new Intent(this, ChooseLockGeneric.class);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        final boolean isResultFinished = resultCode == RESULT_FINISHED;
        final int result = isResultFinished ? RESULT_OK : RESULT_SKIP;
        if (requestCode == BIOMETRIC_FIND_SENSOR_REQUEST) {
            if (isResultFinished || resultCode == RESULT_SKIP) {
                setResult(result, data);
                finish();
                return;
            }
        } else if (requestCode == CHOOSE_LOCK_GENERIC_REQUEST) {
            if (isResultFinished) {
                updatePasswordQuality();
                mToken = data.getByteArrayExtra(
                        ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN);
                overridePendingTransition(R.anim.suw_slide_next_in, R.anim.suw_slide_next_out);
                return;
            } else {
                setResult(result, data);
                finish();
            }
        } else if (requestCode == CONFIRM_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                mToken = data.getByteArrayExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN);
                overridePendingTransition(R.anim.suw_slide_next_in, R.anim.suw_slide_next_out);
            } else {
                setResult(result, data);
                finish();
            }
        } else if (requestCode == LEARN_MORE_REQUEST) {
            overridePendingTransition(R.anim.suw_slide_back_in, R.anim.suw_slide_back_out);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    protected void onCancelButtonClick(View view) {
        finish();
    }

    @Override
    protected void initViews() {
        super.initViews();

        TextView description = (TextView) findViewById(R.id.description_text);
        if (mBiometricUnlockDisabledByAdmin) {
            description.setText(getDescriptionResDisabledByAdmin());
        }
    }
}
