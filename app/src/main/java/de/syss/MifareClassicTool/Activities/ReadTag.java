package de.syss.MifareClassicTool.Activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.SparseArray;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;

import de.syss.MifareClassicTool.Common;
import de.syss.MifareClassicTool.MCReader;
import de.syss.MifareClassicTool.R;

import static de.syss.MifareClassicTool.Activities.Preferences.Preference.UseInternalStorage;

/**
 * Create a key map with {@link KeyMapCreator} and then read the tag
 * @author Jinhao Jiang
 */
public class ReadTag extends Activity {

    private final static int KEY_MAP_CREATOR = 1;

    private final Handler mHandler = new Handler();
    private SparseArray<String[]> mRawDump;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_read_tag);

        if (!Common.getPreferences().getBoolean(UseInternalStorage.toString(), false)
                && !Common.isExternalStorageWritableErrorToast(this)) {
            finish();
            return;
        }

        Intent intent = new Intent(this, KeyMapCreator.class);
        intent.putExtra(KeyMapCreator.EXTRA_KEYS_DIR, Common.getFileFromStorage(
                Common.HOME_DIR + "/" + Common.KEYS_DIR).getAbsolutePath());
        intent.putExtra(KeyMapCreator.EXTRA_BUTTON_TEXT,
                getString(R.string.action_create_key_map_and_read));
        startActivityForResult(intent, KEY_MAP_CREATOR);
    }

    /**
     * Check the result code of the key mapping process
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch(requestCode) {
        case KEY_MAP_CREATOR:
            if (resultCode != Activity.RESULT_OK) {
                if (resultCode == 4) {
                    Toast.makeText(this, R.string.info_strange_error,
                            Toast.LENGTH_LONG).show();
                }
                finish();
                return;
            } else {
                // Read tag
                readTag();
            }
            break;
        }
    }

    /**
     * Reads the tag and then call {@link #createTagDump(SparseArray)} with a worker thread
     */
    private void readTag() {
        final MCReader reader = Common.checkForTagAndCreateReader(this);

        if (reader == null) {
            return;
        }

        new Thread(() -> {
            // Get key map from global variables
            mRawDump = reader.readAsMuchAsPossible(Common.getKeyMap());
            reader.close();
            mHandler.post(() -> createTagDump(mRawDump));
        }).start();
    }

    /**
     * Create a tag dump in a format that can be read by {@link DumpEditor}
     * and then start the dump editor
     */
    private void createTagDump(SparseArray<String[]> rawDump) {
        ArrayList<String> tmpDump = new ArrayList<>();

        if (rawDump != null) {
            if (rawDump.size() != 0) {
                for (int i = Common.getKeyMapRangeFrom(); i <= Common.getKeyMapRangeTo(); i++) {
                    String[] val = rawDump.get(i);
                    // Mark header sectors with "+"
                    tmpDump.add("+Sector: " + i);

                    if (val != null ) {
                        Collections.addAll(tmpDump, val);
                    } else {
                        // Mark non-readable sector as "*"
                        tmpDump.add("*No keys found or dead sector");
                    }
                }

                String[] dump = tmpDump.toArray(new String[tmpDump.size()]);

                // Show Dump Editor
                Intent intent = new Intent(this, DumpEditor.class);
                intent.putExtra(DumpEditor.EXTRA_DUMP, dump);
                startActivity(intent);
            } else {
                // Non-readable keys
                Toast.makeText(this, R.string.info_none_key_valid_for_reading,
                        Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, R.string.info_tag_removed_while_reading,
                    Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
