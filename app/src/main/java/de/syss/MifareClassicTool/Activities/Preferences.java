package de.syss.MifareClassicTool.Activities;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import de.syss.MifareClassicTool.Common;
import de.syss.MifareClassicTool.R;

/**
 * Enable the customization of global preferences
 * @author Jinhao Jiang
 */
public class Preferences extends BasicActivity {

    public enum Preference {
        AutoCopyUID("auto_copy_uid"),
        UIDFormat("uid_format"),
        SaveLastUsedKeyFiles("save_last_used_key_files"),
        UseCustomSectorCount("use_custom_sector_count"),
        CustomSectorCount("custom_sector_count"),
        UseInternalStorage("use_internal_storage"),
        UseRetryAuthentication("use_retry_authentication"),
        RetryAuthenticationCount("retry_authentication_count");

        private final String text;

        Preference(final String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private CheckBox mPrefAutoCopyUID;
    private CheckBox mPrefSaveLastUsedKeyFiles;
    private CheckBox mUseCustomSectorCount;
    private CheckBox mUseRetryAuthentication;
    private CheckBox mUseInternalStorage;
    private CheckBox mPrefAutostartIfCardDetected;
    private EditText mCustomSectorCount;
    private EditText mRetryAuthenticationCount;
    private RadioGroup mUIDFormatRadioGroup;

    private PackageManager mPackageManager;
    private ComponentName mComponentName;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences);

        mPackageManager = getApplicationContext().getPackageManager();
        mComponentName = new ComponentName(
                getPackageName(), getPackageName() + ".MainMenuAlias");

        // Get preferences
        mPrefAutoCopyUID = findViewById(
                R.id.checkBoxPreferencesCopyUID);
        mPrefSaveLastUsedKeyFiles = findViewById(
                R.id.checkBoxPreferencesSaveLastUsedKeyFiles);
        mUseCustomSectorCount = findViewById(
                R.id.checkBoxPreferencesUseCustomSectorCount);
        mCustomSectorCount = findViewById(
                R.id.editTextPreferencesCustomSectorCount);
        mUseInternalStorage = findViewById(
                R.id.checkBoxPreferencesUseInternalStorage);
        mPrefAutostartIfCardDetected = findViewById(
                R.id.checkBoxPreferencesAutostartIfCardDetected);
        mUseRetryAuthentication = findViewById(
                R.id.checkBoxPreferencesUseRetryAuthentication);
        mRetryAuthenticationCount = findViewById(
                R.id.editTextPreferencesRetryAuthenticationCount);

        // Assign the last stored values
        SharedPreferences pref = Common.getPreferences();
        mPrefAutoCopyUID.setChecked(pref.getBoolean(
                Preference.AutoCopyUID.toString(), false));
        setUIDFormatBySequence(pref.getInt(Preference.UIDFormat.toString(),0));
        mPrefSaveLastUsedKeyFiles.setChecked(pref.getBoolean(
                Preference.SaveLastUsedKeyFiles.toString(), true));
        mUseCustomSectorCount.setChecked(pref.getBoolean(
                Preference.UseCustomSectorCount.toString(), false));
        mCustomSectorCount.setEnabled(mUseCustomSectorCount.isChecked());
        mCustomSectorCount.setText("" + pref.getInt(
                Preference.CustomSectorCount.toString(), 16));
        mUseInternalStorage.setChecked(pref.getBoolean(
                Preference.UseInternalStorage.toString(), false));
        mUseRetryAuthentication.setChecked(pref.getBoolean(
                Preference.UseRetryAuthentication.toString(), false));
        mRetryAuthenticationCount.setEnabled(
                mUseRetryAuthentication.isChecked());
        mRetryAuthenticationCount.setText("" + pref.getInt(
                Preference.RetryAuthenticationCount.toString(), 1));
        detectAutostartIfCardDetectedState();

        // UID Format Options
        mUIDFormatRadioGroup = findViewById(R.id.radioGroupUIDFormat);
        toggleUIDFormat(null);
    }

    /**
     * Autostart if card is detected?
     */
    private void detectAutostartIfCardDetectedState() {
        int enabledSetting = mPackageManager.getComponentEnabledSetting(mComponentName);

        switch (enabledSetting) {
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                break;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
                mPrefAutostartIfCardDetected.setChecked(false);
                break;
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
                mPrefAutostartIfCardDetected.setChecked(true);
                break;
            default:
                break;
        }
    }

    /**
     * Toggle the copied UID format
     */
    public void toggleUIDFormat(View view) {
        for (int i = 0; i < mUIDFormatRadioGroup.getChildCount(); i++) {
            mUIDFormatRadioGroup.getChildAt(i).setEnabled(mPrefAutoCopyUID.isChecked());
        }
    }

    /**
     * Get the customized format for the copied UID
     */
    private int getUIDFormatSequence() {
        switch(mUIDFormatRadioGroup.getCheckedRadioButtonId()) {
            case R.id.radioButtonHex:
                return 0;
            case R.id.radioButtonDecBE:
                return 1;
            case R.id.radioButtonDecLE:
                return 2;
        }
        return 0;
    }

    /**
     * Set the format for the copied UID
     */
    private void setUIDFormatBySequence(int seq) {
        RadioButton selectRadioButton;
        int rBID;

        switch(seq) {
            case 1:
                rBID = R.id.radioButtonDecBE;
                break;
            case 2:
                rBID = R.id.radioButtonDecLE;
                break;
            default:
                rBID = R.id.radioButtonHex;
        }

        selectRadioButton = findViewById(rBID);
        selectRadioButton.toggle();
    }

    /**
     * Enable the custom sector count text box
     */
    public void onUseCustomSectorCountChanged(View view) {
        mCustomSectorCount.setEnabled(mUseCustomSectorCount.isChecked());
    }

    /**
     * Enable the retry authentication count text box
     */
    public void onUseRetryAuthenticationChanged(View view) {
        mRetryAuthenticationCount.setEnabled(mUseRetryAuthentication.isChecked());
    }

    /**
     * Save the preferences
     */
    public void onSave(View view) {
        // Check if settings are valid
        int customSectorCount = Integer.parseInt(
                mCustomSectorCount.getText().toString());
        if (customSectorCount <= 0 || customSectorCount > 40) {
            Toast.makeText(this, R.string.info_sector_count_error,
                    Toast.LENGTH_LONG).show();
            return;
        }

        int retryAuthenticationCount = Integer.parseInt(
                mRetryAuthenticationCount.getText().toString());
        if (retryAuthenticationCount <= 0 || retryAuthenticationCount > 1000) {
            Toast.makeText(this, R.string.info_retry_authentication_count_error,
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Save preferences
        SharedPreferences.Editor edit = Common.getPreferences().edit();
        edit.putBoolean(Preference.AutoCopyUID.toString(),
                mPrefAutoCopyUID.isChecked());
        edit.putInt(Preference.UIDFormat.toString(),
                getUIDFormatSequence());
        edit.putBoolean(Preference.SaveLastUsedKeyFiles.toString(),
                mPrefSaveLastUsedKeyFiles.isChecked());
        edit.putBoolean(Preference.UseCustomSectorCount.toString(),
                mUseCustomSectorCount.isChecked());
        edit.putBoolean(Preference.UseInternalStorage.toString(),
                mUseInternalStorage.isChecked());
        edit.putBoolean(Preference.UseRetryAuthentication.toString(),
                mUseRetryAuthentication.isChecked());
        edit.putInt(Preference.CustomSectorCount.toString(),
                customSectorCount);
        edit.putInt(Preference.RetryAuthenticationCount.toString(),
                retryAuthenticationCount);
        edit.apply();

        int newState;
        if (mPrefAutostartIfCardDetected.isChecked()) {
            newState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        } else {
            newState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        }
        mPackageManager.setComponentEnabledSetting(
                mComponentName, newState, PackageManager.DONT_KILL_APP);

        finish();
    }

    /**
     * Exit the preferences without saving
     */
    public void onCancel(View view) {
        finish();
    }
}