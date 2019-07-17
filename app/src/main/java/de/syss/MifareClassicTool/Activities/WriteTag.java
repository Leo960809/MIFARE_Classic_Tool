package de.syss.MifareClassicTool.Activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.nfc.tech.MifareClassic;
import android.os.Bundle;
import android.os.Handler;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.syss.MifareClassicTool.Common;
import de.syss.MifareClassicTool.MCReader;
import de.syss.MifareClassicTool.R;

/**
 * Write data to tag
 * @author Jinhao Jiang
 */
public class WriteTag extends BasicActivity {

    /**
     * The corresponding Intent will contain a dump
     * Headers are marked with "+" (e.g. "+Sector: 1")
     */
    public final static String EXTRA_DUMP = "de.syss.MifareClassicTool.Activity.DUMP";

    private static final int FC_WRITE_DUMP = 1;
    private static final int CKM_WRITE_DUMP = 2;
    private static final int CKM_WRITE_BLOCK = 3;
    private static final int CKM_FACTORY_FORMAT = 4;
    private static final int CKM_WRITE_NEW_VALUE = 5;

    private EditText mSectorTextBlock;
    private EditText mBlockTextBlock;
    private EditText mDataText;
    private EditText mSectorTextVB;
    private EditText mBlockTextVB;
    private EditText mNewValueTextVB;
    private RadioButton mIncreaseVB;
    private EditText mStaticAC;
    private ArrayList<View> mWriteModeLayouts;
    private CheckBox mWriteManufBlock;
    private CheckBox mEnableStaticAC;
    private HashMap<Integer, HashMap<Integer, byte[]>> mDumpWithPos;
    private boolean mWriteDumpFromEditor = false;
    private String[] mDumpFromEditor;

    /**
     * Initialize the layout and member variables
     */
    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_write_tag);

        mSectorTextBlock = findViewById(R.id.editTextWriteTagSector);
        mBlockTextBlock = findViewById(R.id.editTextWriteTagBlock);
        mDataText = findViewById(R.id.editTextWriteTagData);
        mSectorTextVB = findViewById(R.id.editTextWriteTagValueBlockSector);
        mBlockTextVB = findViewById(R.id.editTextWriteTagValueBlockBlock);
        mNewValueTextVB = findViewById(R.id.editTextWriteTagValueBlockValue);
        mIncreaseVB = findViewById(R.id.radioButtonWriteTagWriteValueBlockIncr);
        mStaticAC = findViewById(R.id.editTextWriteTagDumpStaticAC);
        mEnableStaticAC = findViewById(R.id.checkBoxWriteTagDumpStaticAC);
        mWriteManufBlock = findViewById(R.id.checkBoxWriteTagDumpWriteManuf);

        mWriteModeLayouts = new ArrayList<>();
        mWriteModeLayouts.add(findViewById(R.id.relativeLayoutWriteTagWriteBlock));
        mWriteModeLayouts.add(findViewById(R.id.linearLayoutWriteTagDump));
        mWriteModeLayouts.add(findViewById(R.id.linearLayoutWriteTagFactoryFormat));
        mWriteModeLayouts.add(findViewById(R.id.relativeLayoutWriteTagValueBlock));

        if (savedInstanceState != null) {
            mWriteManufBlock.setChecked(savedInstanceState.getBoolean(
                    "write_manuf_block", false));
            Serializable s = savedInstanceState.getSerializable("dump_with_pos");
            if (s instanceof HashMap<?, ?>) {
                mDumpWithPos = (HashMap<Integer, HashMap<Integer, byte[]>>) s;
            }
        }

        Intent i = getIntent();
        if (i.hasExtra(EXTRA_DUMP)) {
            // Write dump directly from editor
            mDumpFromEditor = i.getStringArrayExtra(EXTRA_DUMP);
            mWriteDumpFromEditor = true;

            // Show "Write Dump" option
            RadioButton writeBlock = findViewById(R.id.radioButtonWriteTagWriteBlock);
            RadioButton factoryFormat = findViewById(R.id.radioButtonWriteTagFactoryFormat);
            RadioButton writeDump = findViewById(R.id.radioButtonWriteTagWriteDump);
            writeDump.performClick();
            writeBlock.setEnabled(false);
            factoryFormat.setEnabled(false);

            // Update button text
            Button writeDumpButton = findViewById(R.id.buttonWriteTagDump);
            writeDumpButton.setText(R.string.action_write_dump);
        }
    }

    /**
     * Save {@link #mWriteManufBlock} state and {@link #mDumpWithPos}
     */
    @Override
    public void onSaveInstanceState (Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("write_manuf_block", mWriteManufBlock.isChecked());
        outState.putSerializable("dump_with_pos", mDumpWithPos);
    }

    /**
     * Update the layout to the selected write mode
     */
    public void onChangeWriteMode(View view) {
        for (View layout : mWriteModeLayouts) {
            layout.setVisibility(View.GONE);
        }
        View parent = findViewById(R.id.linearLayoutWriteTag);
        parent.findViewWithTag(view.getTag() + "_layout").setVisibility(View.VISIBLE);
    }

    /**
     * Handle incoming results from {@link KeyMapCreator} or {@link FileChooser}
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        int ckmError = -1;

        switch(requestCode) {
            case FC_WRITE_DUMP:
                if (resultCode == Activity.RESULT_OK) {
                    // Read dump and create keys
                    readDumpFromFile(data.getStringExtra(FileChooser.EXTRA_CHOSEN_FILE));
                }
                break;
            case CKM_WRITE_DUMP:
                if (resultCode != Activity.RESULT_OK) {
                    ckmError = resultCode;
                } else {
                    checkTag();
                }
                break;
            case CKM_FACTORY_FORMAT:
                if (resultCode != Activity.RESULT_OK) {
                    ckmError = resultCode;
                } else {
                    createFactoryFormattedDump();
                }
                break;
            case CKM_WRITE_BLOCK:
                if (resultCode != Activity.RESULT_OK) {
                    ckmError = resultCode;
                } else {
                    // Write block
                    writeBlock();
                }
                break;
            case CKM_WRITE_NEW_VALUE:
                if (resultCode != Activity.RESULT_OK) {
                    ckmError = resultCode;
                } else {
                    // Write block
                    writeValueBlock();
                }
                break;
        }
    }

    /**
     * Check the user input, create a key map with {@link KeyMapCreator},
     * then trigger {@link #writeBlock()}
     */
    public void onWriteBlock(View view) {
        // Check input
        if (!checkSectorAndBlock(mSectorTextBlock, mBlockTextBlock)) {
            return;
        }
        String data = mDataText.getText().toString();
        if (!Common.isHexAnd16Byte(data, this)) {
            return;
        }

        final int sector = Integer.parseInt(mSectorTextBlock.getText().toString());
        final int block = Integer.parseInt(mBlockTextBlock.getText().toString());

        if (!isSectorInRage(this, true)) {
            return;
        }

        if (block == 3 || block == 15) {
            // WARNING: This is a sector trailer
            new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_sector_trailer_warning_title)
                .setMessage(R.string.dialog_sector_trailer_warning)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.action_i_know_what_i_am_doing,
                        (dialog, which) -> {
                            // Show key map creator
                            createKeyMapForBlock(sector, false);
                        })
                 .setNegativeButton(R.string.action_cancel,
                         (dialog, id) -> {
                         }).show();
        } else if (sector == 0 && block == 0) {
            // Check the BCC
            int bccCheck = checkBCC(true);
            if (bccCheck == 0 || bccCheck > 2) {
                // WARNING: Writing to manufacturer block
                showWriteManufInfo(true);
            }
        } else {
            createKeyMapForBlock(sector, false);
        }
    }

    /**
     * Check the user input of the sector and the block field
     */
    private boolean checkSectorAndBlock(EditText sector, EditText block) {
        if (sector.getText().toString().equals("")
                || block.getText().toString().equals("")) {
            // Location not fully set
            Toast.makeText(this, R.string.info_data_location_not_set,
                    Toast.LENGTH_LONG).show();
            return false;
        }

        int sectorNr = Integer.parseInt(sector.getText().toString());
        int blockNr = Integer.parseInt(block.getText().toString());
        if (sectorNr > KeyMapCreator.MAX_SECTOR_COUNT-1 || sectorNr < 0) {
            // Sector is out of range for MIFARE tag
            Toast.makeText(this, R.string.info_sector_out_of_range,
                    Toast.LENGTH_LONG).show();
            return false;
        }
        if (blockNr > KeyMapCreator.MAX_BLOCK_COUNT_PER_SECTOR-1 || blockNr < 0) {
            // Block is out of range for MIFARE tag
            Toast.makeText(this, R.string.info_block_out_of_range,
                    Toast.LENGTH_LONG).show();
            return false;
        }

        return true;
    }


    /**
     * Show/hide the options of write dump
     */
    public void onShowOptions(View view) {
        LinearLayout ll = findViewById(R.id.linearLayoutWriteTagDumpOptions);
        CheckBox cb = findViewById(R.id.checkBoxWriteTagDumpOptions);

        if (cb.isChecked()) {
            ll.setVisibility(View.VISIBLE);
        } else {
            ll.setVisibility(View.GONE);
        }
    }

    /**
     * Display information about writing to the manufacturer block
     * (optionally, create a key map for the first sector)
     */
    private void showWriteManufInfo(final boolean createKeyMap) {
        // WARNING: Writing to the manufacturer block is abnormal
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.dialog_block0_writing_title);
        dialog.setMessage(R.string.dialog_block0_writing);
        dialog.setIcon(android.R.drawable.ic_dialog_info);

        int buttonID = R.string.action_ok;
        if (createKeyMap) {
            buttonID = R.string.action_i_know_what_i_am_doing;
            dialog.setNegativeButton(R.string.action_cancel,
                    (dialog12, which) -> {
                    });
        }
        dialog.setPositiveButton(buttonID,
                (dialog1, which) -> {
                    if (createKeyMap) {
                        createKeyMapForBlock(0, false);
                    }
                });
        dialog.show();
    }

    /**
     * Check if the BCC of {@link #mDumpWithPos} or of {@link #mDataText} is valid
     * (This check is only for 4-byte UIDs)
     */
    private int checkBCC(boolean isWriteBlock) {
        MCReader reader = Common.checkForTagAndCreateReader(this);
        if (reader == null) {
            // No tag found
            return 2;
        }
        reader.close();

        int uidLen = Common.getUID().length;
        if (uidLen != 4) {
            // Not a 4-bytes UID
            return 3;
        }

        byte bcc;
        byte[] uid;
        // The length of UID of the dump or the block 0
        // should match the UID length of the tag (4-byte)
        if (isWriteBlock) {
            bcc = Common.hexStringToByteArray(
                    mDataText.getText().toString().substring(8, 10))[0];
            uid = Common.hexStringToByteArray(
                    mDataText.getText().toString().substring(0, 8));
        } else {
            HashMap<Integer, byte[]> sector0 = mDumpWithPos.get(0);
            if (sector0 == null) {
                // No sector 0 in this dump
                return 4;
            }
            byte[] block0 = sector0.get(0);
            if (block0 == null) {
                // No block 0 in sector 0
                return 5;
            }
            bcc = block0[4];
            uid = new byte[uidLen];
            System.arraycopy(block0, 0, uid, 0, uidLen);
        }

        boolean isValidBcc;
        try {
            isValidBcc = Common.isValidBCC(uid, bcc);
        } catch (IllegalArgumentException e) {
            // Should never happen
            return 3;
        }

        if (!isValidBcc) {
            // BCC not valid
            Toast.makeText(this, R.string.info_bcc_not_valid, Toast.LENGTH_LONG).show();
            return 1;
        }

        return 0;
    }

    /**
     * Helper function for {@link #onWriteBlock(View)} and {@link #onWriteValue(android.view.View)}
     * to show the {@link KeyMapCreator}
     */
    private void createKeyMapForBlock(int sector, boolean isValueBlock) {
        Intent intent = new Intent(this, KeyMapCreator.class);
        intent.putExtra(KeyMapCreator.EXTRA_KEYS_DIR, Common.getFileFromStorage(
                Common.HOME_DIR + "/" + Common.KEYS_DIR).getAbsolutePath());
        intent.putExtra(KeyMapCreator.EXTRA_SECTOR_CHOOSER, false);
        intent.putExtra(KeyMapCreator.EXTRA_SECTOR_CHOOSER_FROM, sector);
        intent.putExtra(KeyMapCreator.EXTRA_SECTOR_CHOOSER_TO, sector);

        if (isValueBlock) {
            intent.putExtra(KeyMapCreator.EXTRA_BUTTON_TEXT, getString(
                    R.string.action_create_key_map_and_write_value_block));
            startActivityForResult(intent, CKM_WRITE_NEW_VALUE);
        } else {
            intent.putExtra(KeyMapCreator.EXTRA_BUTTON_TEXT, getString(
                    R.string.action_create_key_map_and_write_block));
            startActivityForResult(intent, CKM_WRITE_BLOCK);
        }
    }

    /**
     * Write the given data to the tag after a key map is created
     */
    private void writeBlock() {
        MCReader reader = Common.checkForTagAndCreateReader(this);
        if (reader == null) {
            return;
        }

        int sector = Integer.parseInt(mSectorTextBlock.getText().toString());
        int block = Integer.parseInt(mBlockTextBlock.getText().toString());
        byte[][] keys = Common.getKeyMap().get(sector);
        int result = -1;

        if (keys[1] != null) {
            result = reader.writeBlock(sector, block,
                    Common.hexStringToByteArray(mDataText.getText().toString()),
                    keys[1], true);
        }

        if (result == -1 && keys[0] != null) {
            result = reader.writeBlock(sector, block,
                    Common.hexStringToByteArray(mDataText.getText().toString()),
                    keys[0], false);
        }
        reader.close();

        // Error handler
        switch (result) {
            case 2:
                Toast.makeText(this, R.string.info_block_not_in_sector,
                    Toast.LENGTH_LONG).show();
                return;
            case -1:
                Toast.makeText(this, R.string.info_error_writing_block,
                    Toast.LENGTH_LONG).show();
                return;
        }

        Toast.makeText(this, R.string.info_write_successful, Toast.LENGTH_LONG).show();
        finish();
    }

    /**
     * Check input, use {@link FileChooser} to select a dump
     * and wait for its result from {@link #onActivityResult(int, int, Intent)}
     */
    public void onWriteDump(View view) {
        // Check the static access condition option
        if (mEnableStaticAC.isChecked()) {
            String ac = mStaticAC.getText().toString();
            if (!ac.matches("[0-9A-Fa-f]+")) {
                // Not hex
                Toast.makeText(this, R.string.info_ac_not_hex,
                        Toast.LENGTH_LONG).show();
                return;
            }

            if (ac.length() != 6) {
                // Not 3 byte (6 chars)
                Toast.makeText(this, R.string.info_ac_not_3_byte,
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        if (mWriteDumpFromEditor) {
            // Write dump directly from the dump editor
            checkDumpAndShowSectorChooserDialog(mDumpFromEditor);
        } else {
            // Show file chooser
            Intent intent = new Intent(this, FileChooser.class);
            intent.putExtra(FileChooser.EXTRA_DIR, Common.getFileFromStorage(
                    Common.HOME_DIR + "/" + Common.DUMPS_DIR).getAbsolutePath());
            intent.putExtra(FileChooser.EXTRA_TITLE,
                    getString(R.string.text_open_dump_title));
            intent.putExtra(FileChooser.EXTRA_CHOOSER_TEXT,
                    getString(R.string.text_choose_dump_to_write));
            intent.putExtra(FileChooser.EXTRA_BUTTON_TEXT,
                    getString(R.string.action_write_full_dump));
            startActivityForResult(intent, FC_WRITE_DUMP);
        }
    }

    /**
     * Read the dump and call {@link #checkDumpAndShowSectorChooserDialog(String[])}
     */
    private void readDumpFromFile(String pathToDump) {
        File file = new File(pathToDump);
        String[] dump = Common.readFileLineByLine(file, false, this);
        checkDumpAndShowSectorChooserDialog(dump);
    }

    /**
     * Save the data in {@link #mDumpWithPos}
     * after the dump was selected by {@link FileChooser}
     * and read by {@link #readDumpFromFile(String)}
     */
    private void checkDumpAndShowSectorChooserDialog(final String[] dump) {
        int err = Common.isValidDump(dump, false);
        if (err != 0) {
            // ERROR
            Common.isValidDumpErrorToast(err, this);
            return;
        }

        initDumpWithPosFromDump(dump);
        // Create and show sector chooser dialog
        View dialogLayout = getLayoutInflater().inflate(
                R.layout.dialog_write_sectors,
                findViewById(android.R.id.content), false);
        LinearLayout llCheckBoxes = dialogLayout.findViewById(
                R.id.linearLayoutWriteSectorsCheckBoxes);
        Button selectAll = dialogLayout.findViewById(
                R.id.buttonWriteSectorsSelectAll);
        Button selectNone = dialogLayout.findViewById(
                R.id.buttonWriteSectorsSelectNone);
        Integer[] sectors = mDumpWithPos.keySet().toArray(new Integer[mDumpWithPos.size()]);
        Arrays.sort(sectors);
        final Context context = this;
        final CheckBox[] sectorBoxes = new CheckBox[mDumpWithPos.size()];

        for (int i = 0; i< sectors.length; i++) {
            sectorBoxes[i] = new CheckBox(this);
            sectorBoxes[i].setChecked(true);
            sectorBoxes[i].setTag(sectors[i]);
            sectorBoxes[i].setText(getString(R.string.text_sector) + " " + sectors[i]);
            llCheckBoxes.addView(sectorBoxes[i]);
        }

        OnClickListener listener = v -> {
            String tag = v.getTag().toString();
            for (CheckBox box : sectorBoxes) {
                box.setChecked(tag.equals("all"));
            }
        };

        selectAll.setOnClickListener(listener);
        selectNone.setOnClickListener(listener);

        final AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_write_sectors_title)
            .setIcon(android.R.drawable.ic_menu_edit)
            .setView(dialogLayout)
            .setPositiveButton(R.string.action_ok,
                    (dialog12, which) -> {
                    })
            .setNegativeButton(R.string.action_cancel,
                    (dialog1, which) -> {
                    })
            .show();

        final Context con = this;
        // Define behavior for positive button click
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                v -> {
                    // Initialize mDumpWithPos
                    initDumpWithPosFromDump(dump);
                    boolean writeBlock0 = false;
                    for (CheckBox box : sectorBoxes) {
                        int sector = Integer.parseInt(box.getTag().toString());

                        if (!box.isChecked()) {
                            mDumpWithPos.remove(sector);
                        } else if (sector == 0 && box.isChecked()
                                && mWriteManufBlock.isChecked()) {
                            writeBlock0 = true;
                        }
                    }
                    if (mDumpWithPos.size() == 0) {
                        // Nothing to write
                        Toast.makeText(context, R.string.info_nothing_to_write,
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Check if the last sector is out of range
                    if (!isSectorInRage(con, false)) {
                        return;
                    }

                    // BCC check
                    if (writeBlock0) {
                        int bccCheck = checkBCC(false);
                        if (bccCheck == 2) {
                            // Re-check
                            return;
                        } else if (bccCheck == 1) {
                            // ERROR
                            dialog.dismiss();
                            return;
                        }
                    }

                    // Create a key map
                    createKeyMapForDump();
                    dialog.dismiss();
                });
    }

    /**
     * Check if the chosen sector of a dump is in the range of valid sectors
     */
    private boolean isSectorInRage(Context context, boolean isWriteBlock) {
        MCReader reader = Common.checkForTagAndCreateReader(this);

        if (reader == null) {
            return false;
        }

        int lastValidSector = reader.getSectorCount() - 1;
        int lastSector;
        reader.close();

        // Initialize last sector
        if (isWriteBlock) {
            lastSector = Integer.parseInt(mSectorTextBlock.getText().toString());
        } else {
            lastSector = Collections.max(mDumpWithPos.keySet());
        }

        // Is the last sector in the valid range?
        if (lastSector > lastValidSector) {
            // Tag too small for dump
            Toast.makeText(context, R.string.info_tag_too_small, Toast.LENGTH_LONG).show();
            reader.close();
            return false;
        }

        return true;
    }

    /**
     * Initialize {@link #mDumpWithPos} with the data from a dump
     */
    private void initDumpWithPosFromDump(String[] dump) {
        mDumpWithPos = new HashMap<>();
        int sector = 0;
        int block = 0;

        // Transform the simple dump array into a structure (mDumpWithPos)
        // where the sector and block information are known additionally
        for (int i = 0; i < dump.length; i++) {
            if (dump[i].startsWith("+")) {
                String[] tmp = dump[i].split(": ");
                sector = Integer.parseInt(tmp[tmp.length-1]);
                block = 0;
                mDumpWithPos.put(sector, new HashMap<>());
            } else if (!dump[i].contains("-")) {
                // Use static access conditions for all sectors?
                if (mEnableStaticAC.isChecked()
                        && (i+1 == dump.length || dump[i+1].startsWith("+"))) {
                    // This is a Sector Trailer
                    // Replace its ACs with the static ones
                    String newBlock = dump[i].substring(0, 12)
                            + mStaticAC.getText().toString()
                            + dump[i].substring(18);
                    dump[i] = newBlock;
                }
                mDumpWithPos.get(sector).put(block++, Common.hexStringToByteArray(dump[i]));
            } else {
                block++;
            }
        }
    }

    /**
     * Create a key map for {@link #mDumpWithPos}
     */
    private void createKeyMapForDump() {
        // Show the key map creator
        Intent intent = new Intent(this, KeyMapCreator.class);
        intent.putExtra(KeyMapCreator.EXTRA_KEYS_DIR, Common.getFileFromStorage(
                Common.HOME_DIR + "/" + Common.KEYS_DIR).getAbsolutePath());
        intent.putExtra(KeyMapCreator.EXTRA_SECTOR_CHOOSER, false);
        intent.putExtra(KeyMapCreator.EXTRA_SECTOR_CHOOSER_FROM,
                (int) Collections.min(mDumpWithPos.keySet()));
        intent.putExtra(KeyMapCreator.EXTRA_SECTOR_CHOOSER_TO,
                (int) Collections.max(mDumpWithPos.keySet()));
        intent.putExtra(KeyMapCreator.EXTRA_BUTTON_TEXT,
                getString(R.string.action_create_key_map_and_write_dump));
        startActivityForResult(intent, CKM_WRITE_DUMP);
    }

    /**
     * Check if the tag is suitable for {@link #mDumpWithPos}
     */
    private void checkTag() {
        // Create the reader
        MCReader reader = Common.checkForTagAndCreateReader(this);
        if (reader == null) {
            return;
        }

        // Check if the size of the tag is correct for dump
        if (reader.getSectorCount() - 1 < Collections.max(mDumpWithPos.keySet())) {
            // Tag too small for dump
            Toast.makeText(this, R.string.info_tag_too_small, Toast.LENGTH_LONG).show();
            reader.close();
            return;
        }

        // Check if the tag is writable
        final SparseArray<byte[][]> keyMap = Common.getKeyMap();
        HashMap<Integer, int[]> dataPos = new HashMap<>(mDumpWithPos.size());

        for (int sector : mDumpWithPos.keySet()) {
            int i = 0;
            int[] blocks = new int[mDumpWithPos.get(sector).size()];
            for (int block : mDumpWithPos.get(sector).keySet()) {
                blocks[i++] = block;
            }
            dataPos.put(sector, blocks);
        }

        HashMap<Integer, HashMap<Integer, Integer>> writeOnPos =
                reader.isWritableOnPositions(dataPos, keyMap);
        reader.close();

        if (writeOnPos == null) {
            // ERROR
            Toast.makeText(this, R.string.info_check_ac_error,
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Skip dialog
        List<HashMap<String, String>> list = new ArrayList<>();
        final HashMap<Integer, HashMap<Integer, Integer>> writeOnPosSafe =
                new HashMap<>(mDumpWithPos.size());

        // Keys that are missing
        HashSet<Integer> sectors = new HashSet<>();
        for (int sector : mDumpWithPos.keySet()) {
            if (keyMap.indexOfKey(sector) < 0) {
                // Keys for sector not found
                addToList(list, getString(R.string.text_sector) + ": " + sector,
                        getString(R.string.text_keys_not_known));
            } else {
                sectors.add(sector);
            }
        }

        // Keys with missing write privileges or some blocks are read-only
        for (int sector : sectors) {
            if (writeOnPos.get(sector) == null) {
                // IO Error or ACs are invalid
                addToList(list, getString(R.string.text_sector) + ": " + sector,
                        getString(R.string.text_invalid_ac_or_sector_dead));
                continue;
            }

            byte[][] keys = keyMap.get(sector);
            Set<Integer> blocks = mDumpWithPos.get(sector).keySet();

            for (int block : blocks) {
                boolean isSafeForWriting = true;
                if (!mWriteManufBlock.isChecked()
                        && sector == 0 && block == 0) {
                    // Block 0 is read-only
                    continue;
                }

                String position = getString(R.string.text_sector) + ": "
                        + sector + ", " + getString(R.string.text_block)  + ": " + block;
                int writeInfo = writeOnPos.get(sector).get(block);

                switch (writeInfo) {
                    case 0:
                        // Block is read-only
                        addToList(list, position, getString(R.string.text_block_read_only));
                        isSafeForWriting = false;
                        break;
                    case 1:
                        if (keys[0] == null) {
                            // Key with write privileges (A) not known
                            addToList(list, position, getString(
                                R.string.text_write_key_a_not_known));
                            isSafeForWriting = false;
                        }
                        break;
                    case 2:
                        if (keys[1] == null) {
                            // Key with write privileges (B) not known
                            addToList(list, position, getString(
                                R.string.text_write_key_b_not_known));
                            isSafeForWriting = false;
                        }
                        break;
                    case 3:
                        // Both keys have write privileges
                        writeInfo = (keys[0] != null) ? 1 : 2;
                        break;
                    case 4:
                        if (keys[0] == null) {
                            // Key with write privileges (A) not known
                            addToList(list, position, getString(
                                R.string.text_write_key_a_not_known));
                            isSafeForWriting = false;
                        } else {
                            // ACs are read-only
                            addToList(list, position, getString(
                                R.string.text_ac_read_only));
                        }
                        break;
                    case 5:
                        if (keys[1] == null) {
                            // Key with write privileges (B) not known
                            addToList(list, position, getString(
                                R.string.text_write_key_b_not_known));
                            isSafeForWriting = false;
                        } else {
                            // ACs are read-only
                            addToList(list, position, getString(
                                R.string.text_ac_read_only));
                        }
                        break;
                    case 6:
                        if (keys[1] == null) {
                            // Key with write privileges (B) not known
                            addToList(list, position, getString(
                                R.string.text_write_key_b_not_known));
                            isSafeForWriting = false;
                        } else {
                            // Keys are read-only
                            addToList(list, position, getString(
                                R.string.text_keys_read_only));
                        }
                        break;
                    case -1:
                        // ERROR
                        addToList(list, position, getString(R.string.text_strange_error));
                        isSafeForWriting = false;
                }

                if (isSafeForWriting) {
                    if (writeOnPosSafe.get(sector) == null) {
                        // Create sector
                        HashMap<Integer, Integer> blockInfo = new HashMap<>();
                        blockInfo.put(block, writeInfo);
                        writeOnPosSafe.put(sector, blockInfo);
                    } else {
                        // Add to sector
                        writeOnPosSafe.get(sector).put(block, writeInfo);
                    }
                }
            }
        }

        // Show skip/cancel dialog
        if (list.size() != 0) {
            LinearLayout ll = new LinearLayout(this);
            int pad = Common.dpToPx(5);
            ll.setPadding(pad, pad, pad, pad);
            ll.setOrientation(LinearLayout.VERTICAL);
            TextView textView = new TextView(this);
            textView.setText(R.string.dialog_not_writable);
            textView.setTextAppearance(this, android.R.style.TextAppearance_Medium);
            ListView listView = new ListView(this);
            ll.addView(textView);
            ll.addView(listView);
            String[] from = new String[] {"position", "reason"};
            int[] to = new int[] {android.R.id.text1, android.R.id.text2};
            ListAdapter adapter = new SimpleAdapter(this, list,
                    android.R.layout.two_line_list_item, from, to);
            listView.setAdapter(adapter);

            new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_not_writable_title)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setView(ll)
                .setPositiveButton(R.string.action_skip_blocks,
                        (dialog, which) -> {
                            // Skip the non-writable blocks
                            writeDump(writeOnPosSafe, keyMap);
                        })
                .setNegativeButton(R.string.action_cancel_all,
                        (dialog, which) -> {
                        })
                .show();
        } else {
            // Write dump
            writeDump(writeOnPosSafe, keyMap);
        }
    }

    /**
     * A helper function for {@link #checkTag()}
     */
    private void addToList(List<HashMap<String, String>> list, String position, String reason) {
        HashMap<String, String> item = new HashMap<>();
        item.put( "position", position);
        item.put( "reason", reason);
        list.add(item);
    }

    /**
     * Write a dump to a tag
     */
    private void writeDump(final HashMap<Integer, HashMap<Integer, Integer>> writeOnPos,
            final SparseArray<byte[][]> keyMap) {
        if (writeOnPos.size() == 0) {
            // Nothing to write
            Toast.makeText(this, R.string.info_nothing_to_write, Toast.LENGTH_LONG).show();
            return;
        }

        // Create the reader
        final MCReader reader = Common.checkForTagAndCreateReader(this);
        if (reader == null) {
            return;
        }

        LinearLayout ll = new LinearLayout(this);
        int pad = Common.dpToPx(10);
        ll.setPadding(pad, pad, pad, pad);
        ll.setGravity(Gravity.CENTER);
        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        pad = Common.dpToPx(5);
        progressBar.setPadding(0, 0, pad, 0);
        TextView tv = new TextView(this);
        tv.setText(getString(R.string.dialog_wait_write_tag));
        tv.setTextSize(18);
        ll.addView(progressBar);
        ll.addView(tv);
        final AlertDialog warning = new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_wait_write_tag_title)
            .setView(ll)
            .show();


        // Start writing in new thread
        final Activity a = this;
        final Handler handler = new Handler();
        new Thread(() -> {
            // Write dump to tag
            for (int sector : writeOnPos.keySet()) {
                byte[][] keys = keyMap.get(sector);
                for (int block : writeOnPos.get(sector).keySet()) {
                    // Select key with write privileges
                    byte writeKey[] = null;
                    boolean useAsKeyB = true;
                    int wi = writeOnPos.get(sector).get(block);

                    if (wi == 1 || wi == 4) {
                        // Write with key A
                        writeKey = keys[0];
                        useAsKeyB = false;
                    } else if (wi == 2 || wi == 5 || wi == 6) {
                        // Write with key B
                        writeKey = keys[1];
                    }

                    // Write block
                    int result = reader.writeBlock(sector, block,
                            mDumpWithPos.get(sector).get(block), writeKey, useAsKeyB);

                    if (result != 0) {
                        // ERROR
                        handler.post(() -> Toast.makeText(a, R.string.info_write_error,
                                Toast.LENGTH_LONG).show());
                        reader.close();
                        warning.cancel();
                        return;
                    }
                }
            }

            reader.close();
            warning.cancel();
            handler.post(() -> Toast.makeText(a, R.string.info_write_successful,
                    Toast.LENGTH_LONG).show());
            a.finish();
        }).start();
    }

    /**
     * Open key map creator
     */
    public void onFactoryFormat(View view) {
        // Show key map creator
        Intent intent = new Intent(this, KeyMapCreator.class);
        intent.putExtra(KeyMapCreator.EXTRA_KEYS_DIR, Common.getFileFromStorage(
                Common.HOME_DIR + "/" + Common.KEYS_DIR).getAbsolutePath());
        intent.putExtra(KeyMapCreator.EXTRA_SECTOR_CHOOSER, false);
        intent.putExtra(KeyMapCreator.EXTRA_BUTTON_TEXT,
                getString(R.string.action_create_key_map_and_factory_format));
        startActivityForResult(intent, CKM_FACTORY_FORMAT);
    }

    /**
     * Create an empty dump with a size matching the current tag
     * and call {@link #checkTag()} afterwards
     */
    private void createFactoryFormattedDump() {
        mDumpWithPos = new HashMap<>();
        int sectors = MifareClassic.get(Common.getTag()).getSectorCount();
        byte[] emptyBlock = new byte[]
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] normalSectorTrailer = new byte[]
                {-1, -1, -1, -1, -1, -1, -1, 7, -128, 105, -1, -1, -1, -1, -1, -1};
        byte[] lastSectorTrailer = new byte[]
                {-1, -1, -1, -1, -1, -1, -1, 7, -128, -68, -1, -1, -1, -1, -1, -1};

        // Empty 4-block sector
        HashMap<Integer, byte[]> empty4BlockSector = new HashMap<>(4);
        for (int i = 0; i < 3; i++) {
            empty4BlockSector.put(i, emptyBlock);
        }
        empty4BlockSector.put(3, normalSectorTrailer);

        // Empty 16-block sector
        HashMap<Integer, byte[]> empty16BlockSector = new HashMap<>(16);
        for (int i = 0; i < 15; i++) {
            empty16BlockSector.put(i, emptyBlock);
        }
        empty16BlockSector.put(15, normalSectorTrailer);

        HashMap<Integer, byte[]> firstSector = new HashMap<>(4);
        HashMap<Integer, byte[]> lastSector;
        firstSector.put(1, emptyBlock);
        firstSector.put(2, emptyBlock);
        firstSector.put(3, normalSectorTrailer);
        mDumpWithPos.put(0, firstSector);

        // Sector 1 - 31
        for (int i = 1; i < sectors && i < 32; i++) {
            mDumpWithPos.put(i, empty4BlockSector);
        }
        // Sector 32 - 39
        if (sectors == 40) {
            for (int i = 32; i < sectors && i < 39; i++) {
                mDumpWithPos.put(i, empty16BlockSector);
            }
            // The sector trailer is different in the last sector
            lastSector = new HashMap<>(empty16BlockSector);
            lastSector.put(15, lastSectorTrailer);
        } else {
            // The sector trailer is different in the last sector
            lastSector = new HashMap<>(empty4BlockSector);
            lastSector.put(3, lastSectorTrailer);
        }

        mDumpWithPos.put(sectors - 1, lastSector);
        checkTag();
    }

    /**
     * Check the user input, show the {@link KeyMapCreator},
     * and create a key map with {@link #writeValueBlock()}
     */
    public void onWriteValue(View view) {
        // Check input
        if (!checkSectorAndBlock(mSectorTextVB, mBlockTextVB)) {
            return;
        }

        int sector = Integer.parseInt(mSectorTextVB.getText().toString());
        int block = Integer.parseInt(mBlockTextVB.getText().toString());
        if (block == 3 || block == 15 || (sector == 0 && block == 0)) {
            // Invalid block
            Toast.makeText(this, R.string.info_not_vb, Toast.LENGTH_LONG).show();
            return;
        }

        try {
            Integer.parseInt(mNewValueTextVB.getText().toString());
        } catch (Exception e) {
            // Value too big.
            Toast.makeText(this, R.string.info_value_too_big, Toast.LENGTH_LONG).show();
            return;
        }

        createKeyMapForBlock(sector, true);
    }

    /**
     * after a key map was created, increment/decrement the value block
     */
    private void writeValueBlock() {
        // Write the new value
        MCReader reader = Common.checkForTagAndCreateReader(this);
        if (reader == null) {
            return;
        }

        int value = Integer.parseInt(mNewValueTextVB.getText().toString());
        int sector = Integer.parseInt(mSectorTextVB.getText().toString());
        int block = Integer.parseInt(mBlockTextVB.getText().toString());
        byte[][] keys = Common.getKeyMap().get(sector);
        int result = -1;

        if (keys[1] != null) {
            result = reader.writeValueBlock(sector, block, value,
                    mIncreaseVB.isChecked(), keys[1], true);
        }
        if (result == -1 && keys[0] != null) {
            result = reader.writeValueBlock(sector, block, value,
                    mIncreaseVB.isChecked(), keys[0], false);
        }

        reader.close();

        // Error handler
        switch (result) {
            case 2:
                Toast.makeText(this, R.string.info_block_not_in_sector,
                        Toast.LENGTH_LONG).show();
                return;
            case -1:
                Toast.makeText(this, R.string.info_error_writing_value_block,
                        Toast.LENGTH_LONG).show();
                return;
        }

        Toast.makeText(this, R.string.info_write_successful,
                Toast.LENGTH_LONG).show();
        finish();
    }
}
