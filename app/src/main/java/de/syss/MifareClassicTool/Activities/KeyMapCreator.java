package de.syss.MifareClassicTool.Activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils.TruncateAt;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import de.syss.MifareClassicTool.Activities.Preferences.Preference;
import de.syss.MifareClassicTool.Common;
import de.syss.MifareClassicTool.MCReader;
import de.syss.MifareClassicTool.R;

import static de.syss.MifareClassicTool.Activities.Preferences.Preference.UseInternalStorage;

/**
 * Create a key map
 * @author Jinhao Jiang
 */
public class KeyMapCreator extends BasicActivity {

    public final static String EXTRA_KEYS_DIR =
            "de.syss.MifareClassicTool.Activity.KEYS_DIR";
    public final static String EXTRA_SECTOR_CHOOSER =
            "de.syss.MifareClassicTool.Activity.SECTOR_CHOOSER";
    public final static String EXTRA_SECTOR_CHOOSER_FROM =
            "de.syss.MifareClassicTool.Activity.SECTOR_CHOOSER_FROM";
    public final static String EXTRA_SECTOR_CHOOSER_TO =
            "de.syss.MifareClassicTool.Activity.SECTOR_CHOOSER_TO";
    public final static String EXTRA_TITLE =
            "de.syss.MifareClassicTool.Activity.TITLE";
    public final static String EXTRA_BUTTON_TEXT =
            "de.syss.MifareClassicTool.Activity.BUTTON_TEXT";

    public static final int MAX_SECTOR_COUNT = 40;
    public static final int MAX_BLOCK_COUNT_PER_SECTOR = 16;

    private static final String LOG_TAG = KeyMapCreator.class.getSimpleName();

    private static final int DEFAULT_SECTOR_RANGE_FROM = 0;
    private static final int DEFAULT_SECTOR_RANGE_TO = 15;

    private Button mCreateKeyMap;
    private LinearLayout mKeyFilesGroup;
    private TextView mSectorRange;
    private final Handler mHandler = new Handler();
    private int mProgressStatus;
    private ProgressBar mProgressBar;
    private boolean mIsCreatingKeyMap;
    private File mKeyDirPath;
    private int mFirstSector;
    private int mLastSector;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_key_map);
        mCreateKeyMap = findViewById(R.id.buttonCreateKeyMap);
        mSectorRange = findViewById(R.id.textViewCreateKeyMapFromTo);
        mKeyFilesGroup = findViewById(R.id.linearLayoutCreateKeyMapKeyFiles);
        mProgressBar = findViewById(R.id.progressBarCreateKeyMap);

        // Initialize sector range
        Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_SECTOR_CHOOSER)) {
            Button changeSectorRange = findViewById(R.id.buttonCreateKeyMapChangeRange);
            boolean value = intent.getBooleanExtra(EXTRA_SECTOR_CHOOSER, true);
            changeSectorRange.setEnabled(value);
        }

        boolean custom = false;
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        String from = sharedPref.getString("default_mapping_range_from", "");
        String to = sharedPref.getString("default_mapping_range_to", "");

        // Check default values
        if (!from.equals("")) {
            custom = true;
        }

        if (!to.equals("")) {
            custom = true;
        }

        // Check customized values
        if (intent.hasExtra(EXTRA_SECTOR_CHOOSER_FROM)) {
            from = "" + intent.getIntExtra(EXTRA_SECTOR_CHOOSER_FROM, 0);
            custom = true;
        }

        if (intent.hasExtra(EXTRA_SECTOR_CHOOSER_TO)) {
            to = "" + intent.getIntExtra(EXTRA_SECTOR_CHOOSER_TO, 15);
            custom = true;
        }

        if (custom) {
            mSectorRange.setText(from + " - " + to);
        }

        // Initialize title and button text
        if (intent.hasExtra(EXTRA_TITLE)) {
            setTitle(intent.getStringExtra(EXTRA_TITLE));
        }

        if (intent.hasExtra(EXTRA_BUTTON_TEXT)) {
            ((Button) findViewById(R.id.buttonCreateKeyMap)).setText(
                    intent.getStringExtra(EXTRA_BUTTON_TEXT));
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsCreatingKeyMap = false;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (mKeyDirPath == null) {
            if (!getIntent().hasExtra(EXTRA_KEYS_DIR)) {
                setResult(2);
                finish();
                return;
            }
            String path = getIntent().getStringExtra(EXTRA_KEYS_DIR);

            if (path == null) {
                setResult(4);
                finish();
                return;
            }
            mKeyDirPath = new File(path);
        }

        // External storage writable?
        if (!Common.getPreferences().getBoolean(UseInternalStorage.toString(), false)
                && !Common.isExternalStorageWritableErrorToast(this)) {
            setResult(3);
            finish();
            return;
        }

        if (!mKeyDirPath.exists()) {
            setResult(1);
            finish();
            return;
        }

        // List key files and select last used key
        boolean selectLastUsedKeyFiles = Common.getPreferences().getBoolean(
                Preference.SaveLastUsedKeyFiles.toString(), true);
        ArrayList<String> selectedFiles = null;

        if (selectLastUsedKeyFiles) {
            SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
            String selectedFilesChain = sharedPref.getString(
                    "last_used_key_files", null);
            if (selectedFilesChain != null) {
                selectedFiles = new ArrayList<>(Arrays.asList(selectedFilesChain.split("/")));
            }
        }

        mKeyFilesGroup.removeAllViews();
        File[] keyFiles = mKeyDirPath.listFiles();

        if (keyFiles != null) {
            Arrays.sort(keyFiles);

            for (File f : keyFiles) {
                CheckBox c = new CheckBox(this);
                c.setText(f.getName());

                if (selectLastUsedKeyFiles
                        && selectedFiles != null
                        && selectedFiles.contains(f.getName())) {
                    c.setChecked(true);
                }
                mKeyFilesGroup.addView(c);
            }
        }
    }

    /**
     * Select all of the key files
     */
    public void onSelectAll(View view) {
        selectKeyFiles(true);
    }

    /**
     * Select none of the key files
     */
    public void onSelectNone(View view) {
        selectKeyFiles(false);
    }

    /**
     * Select all or none of the key files?
     */
    private void selectKeyFiles(boolean allOrNone) {
        for (int i = 0; i < mKeyFilesGroup.getChildCount(); i++) {
            CheckBox c = (CheckBox) mKeyFilesGroup.getChildAt(i);
            c.setChecked(allOrNone);
        }
    }

    /**
     * Stop creating the key map
     */
    public void onCancelCreateKeyMap(View view) {
        if (mIsCreatingKeyMap) {
            mIsCreatingKeyMap = false;
        } else {
            finish();
        }
    }

    /**
     * Create a key map and save it to {@link Common#setKeyMap(android.util.SparseArray)}
     */
    public void onCreateKeyMap(View view) {
        boolean saveLastUsedKeyFiles = Common.getPreferences().getBoolean(
                Preference.SaveLastUsedKeyFiles.toString(), true);
        StringBuilder lastSelectedKeyFiles = new StringBuilder();

        ArrayList<String> fileNames = new ArrayList<>();
        for (int i = 0; i < mKeyFilesGroup.getChildCount(); i++) {
            CheckBox c = (CheckBox) mKeyFilesGroup.getChildAt(i);
            if (c.isChecked()) {
                fileNames.add(c.getText().toString());
            }
        }

        if (fileNames.size() > 0) {
            // Check if key files exist
            ArrayList<File> keyFiles = new ArrayList<>();
            for (String fileName : fileNames) {
                File keyFile = new File(mKeyDirPath, fileName);
                if (keyFile.exists()) {
                    // Add key file
                    keyFiles.add(keyFile);
                    if (saveLastUsedKeyFiles) {
                        lastSelectedKeyFiles.append(fileName);
                        lastSelectedKeyFiles.append("/");
                    }
                } else {
                    Log.d(LOG_TAG, "Key file "
                            + keyFile.getAbsolutePath()
                            + "doesn't exists anymore.");
                }
            }

            if (keyFiles.size() > 0) {
                // Save last selected key files
                if (saveLastUsedKeyFiles) {
                    SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
                    Editor e = sharedPref.edit();
                    e.putString("last_used_key_files",
                            lastSelectedKeyFiles.substring(0, lastSelectedKeyFiles.length() - 1));
                    e.apply();
                }

                // Create the reader
                MCReader reader = Common.checkForTagAndCreateReader(this);
                if (reader == null) {
                    return;
                }

                // Set key files
                File[] keys = keyFiles.toArray(new File[keyFiles.size()]);
                if (!reader.setKeyFile(keys, this)) {
                    reader.close();
                    return;
                }

                // Keep screen on while mapping
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                // Set key map range
                if (mSectorRange.getText().toString().equals(
                        getString(R.string.text_sector_range_all))) {
                    // Read all
                    mFirstSector = 0;
                    mLastSector = reader.getSectorCount() - 1;
                } else {
                    String[] fromAndTo = mSectorRange.getText().toString().split(" ");
                    mFirstSector = Integer.parseInt(fromAndTo[0]);
                    mLastSector = Integer.parseInt(fromAndTo[2]);
                }

                if (!reader.setMappingRange(mFirstSector, mLastSector)) {
                    Toast.makeText(this, R.string.info_mapping_sector_out_of_range,
                            Toast.LENGTH_LONG).show();
                    reader.close();
                    return;
                }
                Common.setKeyMapRange(mFirstSector, mLastSector);

                // Initialize GUI elements
                mProgressStatus = -1;
                mProgressBar.setMax((mLastSector - mFirstSector) + 1);
                mCreateKeyMap.setEnabled(false);
                mIsCreatingKeyMap = true;
                Toast.makeText(this, R.string.info_wait_key_map,
                        Toast.LENGTH_SHORT).show();
                createKeyMap(reader, this);
            } else {
                // Key file not found
                Toast.makeText(this, R.string.info_mapping_no_keyfile_found,
                        Toast.LENGTH_LONG).show();
            }
        } else {
            // Key file not selected
            Toast.makeText(this, R.string.info_mapping_no_keyfile_selected,
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Triggered by {@link #onCreateKeyMap(View)},
     * create a key map and then call {@link #keyMapCreated(MCReader)} (through a worker thread)
     */
    private void createKeyMap(final MCReader reader, final Context context) {
        new Thread(() -> {
            // Build key map parts and update the progress bar.
            while (mProgressStatus < mLastSector) {
                mProgressStatus = reader.buildNextKeyMapPart();

                if (mProgressStatus == -1 || !mIsCreatingKeyMap) {
                    break;
                }

                mHandler.post(() -> mProgressBar.setProgress(
                        (mProgressStatus - mFirstSector) + 1));
            }

            mHandler.post(() -> {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                mProgressBar.setProgress(0);
                mCreateKeyMap.setEnabled(true);
                reader.close();

                if (mIsCreatingKeyMap && mProgressStatus != -1) {
                    keyMapCreated(reader);
                } else {
                    Common.setKeyMap(null);
                    Common.setKeyMapRange(-1, -1);
                    Toast.makeText(context, R.string.info_key_map_error,
                            Toast.LENGTH_LONG).show();
                }
                mIsCreatingKeyMap = false;
            });
        }).start();
    }

    /**
     * Triggered by {@link #createKeyMap(MCReader, Context)},
     * set the result code to {@link Activity#RESULT_OK}
     * and save the created key map to {@link Common#setKeyMap(android.util.SparseArray)}
     */
    private void keyMapCreated(MCReader reader) {
        if (reader.getKeyMap().size() == 0) {
            // Value key not found
            Common.setKeyMap(null);
            Toast.makeText(this, R.string.info_no_key_found,
                    Toast.LENGTH_LONG).show();
        } else {
            Common.setKeyMap(reader.getKeyMap());
            setResult(Activity.RESULT_OK);
            finish();
        }
    }

    /**
     * Show a dialog which enables the customization of key mapping range
     */
    public void onChangeSectorRange(View view) {
        LinearLayout ll = new LinearLayout(this);
        LinearLayout llv = new LinearLayout(this);

        int pad = Common.dpToPx(10);
        llv.setPadding(pad, pad, pad, pad);
        llv.setOrientation(LinearLayout.VERTICAL);
        llv.setGravity(Gravity.CENTER);
        ll.setGravity(Gravity.CENTER);

        TextView tvFrom = new TextView(this);
        tvFrom.setText(getString(R.string.text_from) + ": ");
        tvFrom.setTextSize(18);
        TextView tvTo = new TextView(this);
        tvTo.setText(" " + getString(R.string.text_to) + ": ");
        tvTo.setTextSize(18);

        // Save the customized settings as default
        final CheckBox saveAsDefault = new CheckBox(this);
        saveAsDefault.setLayoutParams(new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        saveAsDefault.setText(R.string.action_save_as_default);
        saveAsDefault.setTextSize(18);
        tvFrom.setTextColor(saveAsDefault.getCurrentTextColor());
        tvTo.setTextColor(saveAsDefault.getCurrentTextColor());

        InputFilter[] f = new InputFilter[1];
        f[0] = new InputFilter.LengthFilter(2);

        final EditText from = new EditText(this);
        from.setEllipsize(TruncateAt.END);
        from.setMaxLines(1);
        from.setSingleLine();
        from.setInputType(InputType.TYPE_CLASS_NUMBER);
        from.setMinimumWidth(60);
        from.setFilters(f);
        from.setGravity(Gravity.CENTER_HORIZONTAL);

        final EditText to = new EditText(this);
        to.setEllipsize(TruncateAt.END);
        to.setMaxLines(1);
        to.setSingleLine();
        to.setInputType(InputType.TYPE_CLASS_NUMBER);
        to.setMinimumWidth(60);
        to.setFilters(f);
        to.setGravity(Gravity.CENTER_HORIZONTAL);

        ll.addView(tvFrom);
        ll.addView(from);
        ll.addView(tvTo);
        ll.addView(to);
        llv.addView(ll);
        llv.addView(saveAsDefault);
        final Toast err = Toast.makeText(this, R.string.info_invalid_range,
                Toast.LENGTH_LONG);

        // Build and show dialog
        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_mapping_range_title)
            .setMessage(R.string.dialog_mapping_range)
            .setView(llv)
            .setPositiveButton(R.string.action_ok,
                    (dialog, whichButton) -> {
                        String txtFrom = "" + DEFAULT_SECTOR_RANGE_FROM;
                        String txtTo = "" + DEFAULT_SECTOR_RANGE_TO;
                        boolean noFrom = false;

                        if (!from.getText().toString().equals("")) {
                            txtFrom = from.getText().toString();
                        } else {
                            noFrom = true;
                        }

                        if (!to.getText().toString().equals("")) {
                            txtTo = to.getText().toString();
                        } else if (noFrom) {
                            // No value provided, read all sectors
                            mSectorRange.setText(getString(R.string.text_sector_range_all));
                            if (saveAsDefault.isChecked()) {
                                saveMappingRange("", "");
                            }
                            return;
                        }

                        int intFrom = Integer.parseInt(txtFrom);
                        int intTo = Integer.parseInt(txtTo);
                        if (intFrom > intTo || intFrom < 0 || intTo > MAX_SECTOR_COUNT - 1) {
                            err.show();
                        } else {
                            mSectorRange.setText(txtFrom + " - " + txtTo);
                            if (saveAsDefault.isChecked()) {
                                // Save as default
                                saveMappingRange(txtFrom, txtTo);
                            }
                        }
                    })
            .setNeutralButton(R.string.action_read_all_sectors,
                    (dialog, whichButton) -> {
                        // Read all sectors
                        mSectorRange.setText(getString(R.string.text_sector_range_all));
                        if (saveAsDefault.isChecked()) {
                            // Save as default
                            saveMappingRange("", "");
                        }
                    })
            .setNegativeButton(R.string.action_cancel,
                    (dialog, whichButton) -> {
                        // Cancel and do nothing
                    }).show();
    }

    /**
     * Save the mapping rage as default
     */
    private void saveMappingRange(String from, String to) {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        Editor sharedEditor = sharedPref.edit();
        sharedEditor.putString("default_mapping_range_from", from);
        sharedEditor.putString("default_mapping_range_to", to);
        sharedEditor.apply();
    }
}
