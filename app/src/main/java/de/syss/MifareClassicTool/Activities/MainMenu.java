package de.syss.MifareClassicTool.Activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import androidx.core.app.ActivityCompat;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.syss.MifareClassicTool.Common;
import de.syss.MifareClassicTool.R;

import static de.syss.MifareClassicTool.Activities.Preferences.Preference.UseInternalStorage;

/**
 * App entry point showing the main menu.
 * @author Jinhao Jiang
 */
public class MainMenu extends Activity {

    private static final String LOG_TAG = MainMenu.class.getSimpleName();

    private final static int FILE_CHOOSER_DUMP_FILE = 1;
    private final static int FILE_CHOOSER_KEY_FILE = 2;
    private static final int REQUEST_WRITE_STORAGE_CODE = 1;

    private Button mReadTag;
    private Button mWriteTag;
    private Button mDumpEditor;
    private Button mKeyEditor;
    private Intent mOldIntent = null;

    private enum StartUpNode {
        FirstUseDialog, HasNfc, HasMifareClassicSupport, HasNfcEnabled, HandleNewIntent
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        // Show App version as the footer
        TextView tv = findViewById(R.id.textViewMainFooter);
        tv.setText(getString(R.string.app_version) + ": " + Common.getVersionCode());

        // Main layout buttons
        mReadTag = findViewById(R.id.buttonMainReadTag);
        mWriteTag = findViewById(R.id.buttonMainWriteTag);
        mKeyEditor = findViewById(R.id.buttonMainEditKeyDump);
        mDumpEditor = findViewById(R.id.buttonMainEditCardDump);

        // Add the context menu to the "Tools" button
        Button tools = findViewById(R.id.buttonMainTools);
        registerForContextMenu(tools);

        // Check writing permissions
        if (Common.hasWritePermissionToExternalStorage(this)) {
            initFolders();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE_CODE);
        }
    }

    private void runStartUpNode(StartUpNode startUpNode) {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);

        switch (startUpNode) {
            case FirstUseDialog:
                boolean isFirstRun = sharedPref.getBoolean("is_first_run", true);
                if (isFirstRun) {
                    createFirstUseDialog().show();
                } else {
                    runStartUpNode(StartUpNode.HasNfc);
                }
                break;
            case HasNfc:
                Common.setNfcAdapter(NfcAdapter.getDefaultAdapter(this));
                if (Common.getNfcAdapter() != null) {
                    runStartUpNode(StartUpNode.HasMifareClassicSupport);
                } else {
                    Toast.makeText(this, R.string.info_no_mfc_support,
                            Toast.LENGTH_LONG).show();
                }
                break;
            case HasMifareClassicSupport:
                if (!Common.hasMifareClassicSupport() && !Common.useAsEditorOnly()) {
                    AlertDialog ad = createHasNoMifareClassicSupportDialog();
                    ad.show();
                    ((TextView) ad.findViewById(android.R.id.message))
                            .setMovementMethod(LinkMovementMethod.getInstance());
                } else {
                    runStartUpNode(StartUpNode.HasNfcEnabled);
                }
                break;
            case HasNfcEnabled:
                Common.setNfcAdapter(NfcAdapter.getDefaultAdapter(this));
                if (!Common.getNfcAdapter().isEnabled()) {
                    if (!Common.useAsEditorOnly()) {
                        createNfcEnableDialog().show();
                    } else {
                    }
                } else {
                    useAsEditorOnly(false);
                    Common.enableNfcForegroundDispatch(this);
                }
                break;
            case HandleNewIntent:
                Common.setPendingComponentName(null);
                Intent intent = getIntent();
                if (intent != null) {
                    boolean isIntentWithTag = intent.getAction().equals(
                            NfcAdapter.ACTION_TECH_DISCOVERED);
                    if (isIntentWithTag && intent != mOldIntent) {
                        mOldIntent = intent;
                        onNewIntent(getIntent());
                    } else {
                        break;
                    }
                }
                break;
        }
    }

    /**
     * Use the app in editor only mode or not?
     */
    private void useAsEditorOnly(boolean useAsEditorOnly) {
        Common.setUseAsEditorOnly(useAsEditorOnly);

        mReadTag.setEnabled(!useAsEditorOnly);
        mWriteTag.setEnabled(!useAsEditorOnly);
    }

    /**
     * Create the dialog that is displayed when the app is run for the first time
     */
    private AlertDialog createFirstUseDialog() {
        return new AlertDialog.Builder(this)
                .setMessage(R.string.dialog_first_run)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> dialog.cancel())
                .setOnCancelListener(dialog -> {
                    SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
                    Editor sharedEditor = sharedPref.edit();
                    sharedEditor.putBoolean("is_first_run", false);
                    sharedEditor.apply();
                    runStartUpNode(StartUpNode.HasNfc);
                })
                .create();
    }

    /**
     * Create the dialog that is displayed if the device does not support MIFARE Classic
     */
    private AlertDialog createHasNoMifareClassicSupportDialog() {
        CharSequence styledText = Html.fromHtml(getString(R.string.dialog_no_mfc_support_device));
        return new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_no_mfc_support_device_title)
                .setMessage(styledText)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.action_exit_app, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .create();
    }

    /**
     * Create the dialog that is displayed when NFC is off
     */
    private AlertDialog createNfcEnableDialog() {
        return new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_nfc_not_enabled_title)
                .setMessage(R.string.dialog_nfc_not_enabled)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton(R.string.action_nfc, (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= 16) {
                        startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
                    } else {
                        startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                    }
                })
                .setNegativeButton(R.string.action_exit_app, (dialog, id) -> finish())
                .setOnCancelListener(dialog -> finish())
                .create();
    }

    /**
     * Create the directories needed by MCT and clean up tmp directory
     */
    @SuppressLint("ApplySharedPref")
    private void initFolders() {
        boolean isUseInternalStorage = Common.getPreferences().getBoolean(
                UseInternalStorage.toString(), false);

        // Initialize the folders
        for (int i = 0; i < 2; i++) {
            if (!isUseInternalStorage
                    && !Common.isExternalStorageWritableErrorToast(this)) {
                continue;
            }

            // Create keys directory
            File path = Common.getFileFromStorage(
                    Common.HOME_DIR + "/" + Common.KEYS_DIR);
            if (!path.exists() && !path.mkdirs()) {
                Log.e(LOG_TAG, "ERROR while creating '"
                        + Common.HOME_DIR + "/" + Common.KEYS_DIR + "' directory.");
                return;
            }

            // Create dumps directory
            path = Common.getFileFromStorage(
                    Common.HOME_DIR + "/" + Common.DUMPS_DIR);
            if (!path.exists() && !path.mkdirs()) {
                Log.e(LOG_TAG, "ERROR while creating '"
                        + Common.HOME_DIR + "/" + Common.DUMPS_DIR + "' directory.");
                return;
            }

            // Create tmp directory
            path = Common.getFileFromStorage(
                    Common.HOME_DIR + "/" + Common.TMP_DIR);
            if (!path.exists() && !path.mkdirs()) {
                Log.e(LOG_TAG, "ERROR while creating '"
                        + Common.HOME_DIR + Common.TMP_DIR + "' directory.");
                return;
            }

            // Clean up tmp directory
            File[] tmpFiles = path.listFiles();
            if (tmpFiles != null) {
                for (File file : tmpFiles) {
                    file.delete();
                }
            }

            // Create std. key file (if necessary)
            copyStdKeysFilesIfNecessary();

            // Change the storage for the second run
            Common.getPreferences().edit().putBoolean(
                    UseInternalStorage.toString(), !isUseInternalStorage).commit();
        }

        // Restore the storage preference
        Common.getPreferences().edit().putBoolean(
                UseInternalStorage.toString(), isUseInternalStorage).commit();
    }

    /**
     * Create the option menu
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.general_options, menu);
        return true;
    }

    /**
     * Create the tools menu
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();

        menu.setHeaderTitle(R.string.dialog_tools_menu_title);
        menu.setHeaderIcon(android.R.drawable.ic_menu_preferences);
        inflater.inflate(R.menu.tools, menu);

        // Enable/Disable tag info tool
        menu.findItem(R.id.menuMainTagInfo).setEnabled(!Common.useAsEditorOnly());
        // Enable/Disable diff tool
        menu.findItem(R.id.menuMainDiffTool).setEnabled(
                Common.hasWritePermissionToExternalStorage(this));
    }

    /**
     * Resume by triggering startup system
     */
    @Override
    public void onResume() {
        super.onResume();

        if (Common.hasWritePermissionToExternalStorage(this)) {
            mKeyEditor.setEnabled(true);
            mDumpEditor.setEnabled(true);
            useAsEditorOnly(Common.useAsEditorOnly());
            runStartUpNode(StartUpNode.FirstUseDialog);
        } else {
            enableMenuButtons(false);
        }
    }

    /**
     * Disable NFC foreground dispatch system
     */
    @Override
    public void onPause() {
        Common.disableNfcForegroundDispatch(this);
        super.onPause();
    }

    /**
     * Handle new intent
     */
    @Override
    public void onNewIntent(Intent intent) {
        if(Common.getPendingComponentName() != null) {
            intent.setComponent(Common.getPendingComponentName());
            startActivity(intent);
        } else {
            int typeCheck = Common.treatAsNewTag(intent, this);
            if (typeCheck == -1 || typeCheck == -2) {
                // If the device or the tag does not support MIFARE Classic
                // Run the Tag Info Tool
                Intent i = new Intent(this, TagInfoTool.class);
                startActivity(i);
            }
        }
    }

    /**
     * Handle permission requests
     */
    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_WRITE_STORAGE_CODE:
                if (grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initFolders();
                } else {
                    Toast.makeText(this, R.string.info_write_permission,
                            Toast.LENGTH_LONG).show();
                    enableMenuButtons(false);
                }
                break;
        }
    }

    /**
     * Enable menu buttons which enables the use of the external storage
     */
    private void enableMenuButtons(boolean enable) {
        mWriteTag.setEnabled(enable);
        mReadTag.setEnabled(enable);
        mDumpEditor.setEnabled(enable);
        mKeyEditor.setEnabled(enable);
    }

    /**
     * Show the {@link ReadTag}
     */
    public void onShowReadTag(View view) {
        Intent intent = new Intent(this, ReadTag.class);
        startActivity(intent);
    }

    /**
     * Show the {@link WriteTag}
     */
    public void onShowWriteTag(View view) {
        Intent intent = new Intent(this, WriteTag.class);
        startActivity(intent);
    }

    /**
     * Show the tools (context menu)
     */
    public void onShowTools(View view) {
        openContextMenu(view);
    }

    public void onOpenTagDumpEditor(View view) {
        if (!Common.getPreferences().getBoolean(UseInternalStorage.toString(), false)
                && !Common.isExternalStorageWritableErrorToast(this)) {
            return;
        }

        File file = Common.getFileFromStorage(Common.HOME_DIR + "/" + Common.DUMPS_DIR);
        if (file.isDirectory() && (file.listFiles() == null || file.listFiles().length == 0)) {
            Toast.makeText(this, R.string.info_no_dumps, Toast.LENGTH_LONG).show();
        }

        Intent intent = new Intent(this, FileChooser.class);
        intent.putExtra(FileChooser.EXTRA_DIR, file.getAbsolutePath());
        intent.putExtra(FileChooser.EXTRA_TITLE, getString(R.string.text_open_dump_title));
        intent.putExtra(FileChooser.EXTRA_BUTTON_TEXT, getString(R.string.action_open_dump_file));
        intent.putExtra(FileChooser.EXTRA_ENABLE_DELETE_FILE, true);
        startActivityForResult(intent, FILE_CHOOSER_DUMP_FILE);
    }

    public void onOpenKeyEditor(View view) {
        if (!Common.getPreferences().getBoolean(UseInternalStorage.toString(), false)
                && !Common.isExternalStorageWritableErrorToast(this)) {
            return;
        }

        Intent intent = new Intent(this, FileChooser.class);
        intent.putExtra(FileChooser.EXTRA_DIR, Common.getFileFromStorage(
                Common.HOME_DIR + "/" + Common.KEYS_DIR).getAbsolutePath());
        intent.putExtra(FileChooser.EXTRA_TITLE, getString(R.string.text_open_key_file_title));
        intent.putExtra(FileChooser.EXTRA_BUTTON_TEXT, getString(R.string.action_open_key_file));
        intent.putExtra(FileChooser.EXTRA_ENABLE_NEW_FILE, true);
        intent.putExtra(FileChooser.EXTRA_ENABLE_DELETE_FILE, true);
        startActivityForResult(intent, FILE_CHOOSER_KEY_FILE);
    }

    /**
     * Show the {@link Preferences}
     */
    private void onShowPreferences() {
        Intent intent = new Intent(this, Preferences.class);
        startActivity(intent);
    }

    /**
     * Show dialog
     */
    private void onShowAboutDialog() {
        CharSequence styledText = Html.fromHtml(
                getString(R.string.dialog_about_mct, Common.getVersionCode()));

        AlertDialog ad = new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_about_mct_title)
            .setMessage(styledText)
            .setPositiveButton(R.string.action_ok,
                    (dialog, which) -> {
                    }).create();
         ad.show();

         ((TextView)ad.findViewById(android.R.id.message)).setMovementMethod(
                 LinkMovementMethod.getInstance());
    }

    /**
     * Handle the selected action from the options menu
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menuMainPreferences:
            onShowPreferences();
            return true;
        case R.id.menuMainAbout:
            onShowAboutDialog();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Handle the selected action from the tools menu
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        Intent intent;

        switch (item.getItemId()) {
        case R.id.menuMainTagInfo:
            intent = new Intent(this, TagInfoTool.class);
            startActivity(intent);
            return true;
        case R.id.menuMainValueBlockTool:
            intent = new Intent(this, ValueBlockTool.class);
            startActivity(intent);
            return true;
        case R.id.menuMainAccessConditionTool:
            intent = new Intent(this, AccessConditionTool.class);
            startActivity(intent);
            return true;
        case R.id.menuMainDiffTool:
            intent = new Intent(this, DiffTool.class);
            startActivity(intent);
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }

    /**
     * Run {@link DumpEditor} or {@link KeyEditor}
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch(requestCode) {
        case FILE_CHOOSER_DUMP_FILE:
            if (resultCode == Activity.RESULT_OK) {
                Intent intent = new Intent(this, DumpEditor.class);
                intent.putExtra(FileChooser.EXTRA_CHOSEN_FILE,
                        data.getStringExtra(FileChooser.EXTRA_CHOSEN_FILE));
                startActivity(intent);
            }
            break;
        case FILE_CHOOSER_KEY_FILE:
            if (resultCode == Activity.RESULT_OK) {
                Intent intent = new Intent(this, KeyEditor.class);
                intent.putExtra(FileChooser.EXTRA_CHOSEN_FILE,
                        data.getStringExtra(FileChooser.EXTRA_CHOSEN_FILE));
                startActivity(intent);
            }
            break;
        }
    }

    /**
     * Copy ({@link Common#STD_KEYS} and {@link Common#STD_KEYS_EXTENDED})
     * to {@link Common#KEYS_DIR}
     */
    private void copyStdKeysFilesIfNecessary() {
        File std = Common.getFileFromStorage(Common.HOME_DIR
                + "/" + Common.KEYS_DIR + "/" + Common.STD_KEYS);
        File extended = Common.getFileFromStorage(Common.HOME_DIR
                + "/" + Common.KEYS_DIR + "/" + Common.STD_KEYS_EXTENDED);
        AssetManager assetManager = getAssets();

        if (!std.exists()) {
            // Copy std.keys
            try {
                InputStream in = assetManager.open(
                        Common.KEYS_DIR + "/" + Common.STD_KEYS);
                OutputStream out = new FileOutputStream(std);
                Common.copyFile(in, out);
                in.close();
                out.flush();
                out.close();
              } catch(IOException e) {
                  Log.e(LOG_TAG, "ERROR while copying 'std.keys'");
              }
        }
        if (!extended.exists()) {
            // Copy extended-std.keys
            try {
                InputStream in = assetManager.open(
                        Common.KEYS_DIR + "/" + Common.STD_KEYS_EXTENDED);
                OutputStream out = new FileOutputStream(extended);
                Common.copyFile(in, out);
                in.close();
                out.flush();
                out.close();
              } catch(IOException e) {
                  Log.e(LOG_TAG, "ERROR while copying 'extended-std.keys'");
              }
        }
    }
}
