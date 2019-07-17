package de.syss.MifareClassicTool.Activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Locale;

import de.syss.MifareClassicTool.Common;
import de.syss.MifareClassicTool.R;

/**
 * Show and edit key files
 * @author Jinhao Jiang
 */
public class KeyEditor extends BasicActivity implements IActivityThatReactsToSave{

    private EditText mKeys;
    private String mFileName;
    private String[] mLines;

    private boolean mKeyChanged;
    private boolean mCloseAfterSuccessfulSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_key_editor);

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(FileChooser.EXTRA_CHOSEN_FILE)) {
            mKeys = findViewById(R.id.editTextKeyEditorKeys);
            mKeys.setTypeface(Typeface.DEFAULT);


            File keyFile = new File(getIntent().getStringExtra(FileChooser.EXTRA_CHOSEN_FILE));
            mFileName = keyFile.getName();
            setTitle(getTitle() + " (" + mFileName + ")");

            if (keyFile.exists()) {
                String keyDump[] = Common.readFileLineByLine(
                        keyFile, true, this);
                if (keyDump == null) {
                    // ERROR
                    finish();
                    return;
                }
                setKeyArrayAsText(keyDump);
            }

            mKeys.addTextChangedListener(new TextWatcher(){
                @Override
                public void afterTextChanged(Editable s) {
                    // Changed text
                    mKeyChanged = true;
                }
                @Override
                public void beforeTextChanged(CharSequence s,
                        int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s,
                        int start, int before, int count) {}
            });

            setIntent(null);
        } else {
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.key_editor_functions, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Selection handler
        switch (item.getItemId()) {
            case R.id.menuKeyEditorSave:
                onSave();
                return true;
            case R.id.menuKeyEditorShare:
                shareKeyFile();
                return true;
            case R.id.menuKeyEditorRemoveDuplicates:
                removeDuplicates();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Disaplay a dialog with the options of "save", "don't save" and "cancel"
     * (if there are unsaved changes)
     */
    @Override
    public void onBackPressed() {
        if (mKeyChanged) {
            new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_save_before_quitting_title)
            .setMessage(R.string.dialog_save_before_quitting)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton(R.string.action_save,
                    (dialog, which) -> {
                        // Save
                        mCloseAfterSuccessfulSave = true;
                        onSave();
                    })
            .setNeutralButton(R.string.action_cancel,
                    (dialog, which) -> {
                        // Cancel
                    })
            .setNegativeButton(R.string.action_dont_save,
                    (dialog, id) -> {
                        // Don't save
                        finish();
                    }).show();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onSaveSuccessful() {
        if (mCloseAfterSuccessfulSave) {
            finish();
        }
        mKeyChanged = false;
    }

    @Override
    public void onSaveFailure() {
        mCloseAfterSuccessfulSave = false;
    }

    /**
     * Share a key file as "file://" stream resource
     */
    private void shareKeyFile() {
        if (!isValidKeyFileErrorToast()) {
            return;
        }

        // Save key file to to a temporary file
        String fileName;
        if (mFileName.equals("")) {
            // Use date and time as name (if not given)
            GregorianCalendar calendar = new GregorianCalendar();
            SimpleDateFormat fmt = new SimpleDateFormat(
                    "yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
            fmt.setCalendar(calendar);
            fileName = fmt.format(calendar.getTime());
        } else {
            fileName = mFileName;
        }

        // Save file to tmp directory
        File file = Common.getFileFromStorage(
                Common.HOME_DIR + "/" + Common.TMP_DIR + "/" + fileName);
        if (!Common.saveFile(file, mLines, false)) {
            Toast.makeText(this, R.string.info_save_error,
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Share file
        Common.shareTmpFile(this, file);
    }

    private void onSave() {
        if (!isValidKeyFileErrorToast()) {
            return;
        }

        final File path = Common.getFileFromStorage(
                Common.HOME_DIR + "/" + Common.KEYS_DIR);
        final Context cont = this;
        final IActivityThatReactsToSave activity = this;
        // Ask user for the filename
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setLines(1);
        input.setHorizontallyScrolling(true);
        input.setText(mFileName);
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_save_keys_title)
            .setMessage(R.string.dialog_save_keys)
            .setIcon(android.R.drawable.ic_menu_save)
            .setView(input)
            .setPositiveButton(R.string.action_ok,
                    (dialog, whichButton) -> {
                        if (input.getText() != null && !input.getText().toString().equals("")) {
                            File file = new File(path.getPath(), input.getText().toString());
                            Common.checkFileExistenceAndSave(
                                    file, mLines, false, cont, activity);
                        } else {
                            // Invalid name
                            Toast.makeText(cont, R.string.info_empty_file_name,
                                    Toast.LENGTH_LONG).show();
                        }
                    })
            .setNegativeButton(R.string.action_cancel,
                    (dialog, whichButton) -> mCloseAfterSuccessfulSave = false).show();
    }

    /**
     * Remove duplicates key(s)
     */
    private void removeDuplicates() {
        if (isValidKeyFileErrorToast()) {
            ArrayList<String> newLines = new ArrayList<>();

            for (String line : mLines) {
                if (line.equals("") || line.startsWith("#")) {
                    // Add comments
                    newLines.add(line);
                    continue;
                }
                if (!newLines.contains(line)) {
                    // Add key(s)
                    newLines.add(line);
                }
            }

            mLines = newLines.toArray(new String[newLines.size()]);
            setKeyArrayAsText(mLines);
        }
    }

    /**
     * Update the user input field for keys with the given lines
     */
    private void setKeyArrayAsText(String[] lines) {
        StringBuilder keyText = new StringBuilder();
        String s = System.getProperty("line.separator");
        
        for (int i = 0; i < lines.length - 1; i++) {
            keyText.append(lines[i]);
            keyText.append(s);
        }

        keyText.append(lines[lines.length-1]);
        mKeys.setText(keyText);
    }

    /**
     * Check if the user input is valid and update {@link #mLines}
     */
    private int isValidKeyFile() {
        String[] lines = mKeys.getText().toString().split(System.getProperty("line.separator"));
        boolean keyFound = false;

        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].startsWith("#")
                    && !lines[i].equals("")
                    && !lines[i].matches("[0-9A-Fa-f]+")) {
                // Not pure hex and not a comment
                return 2;
            }

            if (!lines[i].startsWith("#") && !lines[i].equals("")
                    && lines[i].length() != 12) {
                // Not 12 chars per line
                return 3;
            }

            if (!lines[i].startsWith("#") && !lines[i].equals("")) {
                // At least one key found
                lines[i] = lines[i].toUpperCase(Locale.getDefault());
                keyFound = true;
            }
        }

        if (!keyFound) {
            // Key(s) not found
            return 1;
        }

        mLines = lines;
        return 0;
    }

    /**
     * Check the keys with {@link #isValidKeyFile()()}
     */
    private boolean isValidKeyFileErrorToast() {
        int err = isValidKeyFile();

        if (err == 1) {
            Toast.makeText(this, R.string.info_valid_keys_no_keys,
                    Toast.LENGTH_LONG).show();
        } else if (err == 2) {
            Toast.makeText(this, R.string.info_valid_keys_not_hex,
                    Toast.LENGTH_LONG).show();
        } else if (err == 3) {
            Toast.makeText(this, R.string.info_valid_keys_not_6_byte,
                    Toast.LENGTH_LONG).show();
        }

        return err == 0;
    }
}