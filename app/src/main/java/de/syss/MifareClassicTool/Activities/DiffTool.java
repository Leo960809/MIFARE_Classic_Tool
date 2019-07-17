package de.syss.MifareClassicTool.Activities;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;

import de.syss.MifareClassicTool.Common;
import de.syss.MifareClassicTool.MCDiffUtils;
import de.syss.MifareClassicTool.R;

/**
 * A tool to display the difference between two dumps
 * @author Jinhao Jiang
 */
public class DiffTool extends BasicActivity {

    public final static String EXTRA_DUMP = "de.syss.MifareClassicTool.Activity.DUMP";

    private final static int FILE_CHOOSER_DUMP_FILE_1 = 1;
    private final static int FILE_CHOOSER_DUMP_FILE_2 = 2;

    private LinearLayout mDiffContent;
    private Button mDumpFileButton1;
    private Button mDumpFileButton2;
    private SparseArray<String[]> mDump1;
    private SparseArray<String[]> mDump2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diff_tool);

        mDiffContent = findViewById(R.id.linearLayoutDiffTool);
        mDumpFileButton1 = findViewById(R.id.buttonDiffToolDump1);
        mDumpFileButton2 = findViewById(R.id.buttonDiffToolDump2);

        // Check if one or both dumps are already chosen
        if (getIntent().hasExtra(EXTRA_DUMP)) {
            mDump1 = convertDumpFormat(getIntent().getStringArrayExtra(EXTRA_DUMP));
            mDumpFileButton1.setText(R.string.text_dump_from_editor);
            mDumpFileButton1.setEnabled(false);
            onChooseDump2(null);
        }

        runDiff();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch(requestCode) {
            case FILE_CHOOSER_DUMP_FILE_1:
                if (resultCode == Activity.RESULT_OK) {
                    // Dump 1 chosen
                    String fileName = data.getStringExtra(FileChooser.EXTRA_CHOSEN_FILENAME);
                    mDumpFileButton1.setText(fileName);
                    mDump1 = processChosenDump(data);
                    runDiff();
                }
                break;
            case FILE_CHOOSER_DUMP_FILE_2:
                if (resultCode == Activity.RESULT_OK) {
                    // Dump 2 chosen
                    String fileName = data.getStringExtra(FileChooser.EXTRA_CHOSEN_FILENAME);
                    mDumpFileButton2.setText(fileName);
                    mDump2 = processChosenDump(data);
                    runDiff();
                }
                break;
        }
    }

    private void runDiff() {
        // Check if both dumps exist
        if (mDump1 != null && mDump2 != null) {
            mDiffContent.removeAllViews();
            SparseArray<Integer[][]> diff = MCDiffUtils.diffIndices(mDump1, mDump2);

            // Go through all possible sectors
            for (int sector = 0; sector < 40; sector++) {
                Integer[][] blocks = diff.get(sector);
                if (blocks == null) {
                    continue;
                }

                // Add sector header
                TextView header = new TextView(this);
                header.setTextAppearance(this, android.R.style.TextAppearance_Medium);
                header.setPadding(0, Common.dpToPx(20), 0, 0);
                header.setTextColor(Color.WHITE);
                header.setText(getString(R.string.text_sector) + ": " + sector);
                mDiffContent.addView(header);

                if (blocks.length == 0 || blocks.length == 1) {
                    TextView tv = new TextView(this);
                    if (blocks.length == 0) {
                        // Sector only exist in dump 1
                        tv.setText(getString(R.string.text_only_in_dump1));
                    } else {
                        // Sector only exist in dump 2
                        tv.setText(getString(R.string.text_only_in_dump2));
                    }
                    mDiffContent.addView(tv);
                    continue;
                }

                // Go through all blocks
                for (int block = 0; block < blocks.length; block++) {
                    // Initialize diff entry
                    RelativeLayout rl = (RelativeLayout) getLayoutInflater().inflate(
                            R.layout.list_item_diff_block,
                            findViewById(android.R.id.content), false);
                    TextView dump1 = rl.findViewById(R.id.textViewDiffBlockDump1);
                    TextView dump2 = rl.findViewById(R.id.textViewDiffBlockDump2);
                    TextView diffIndex = rl.findViewById(R.id.textViewDiffBlockDiff);

                    dump1.setTypeface(Typeface.MONOSPACE);
                    dump2.setTypeface(Typeface.MONOSPACE);
                    diffIndex.setTypeface(Typeface.MONOSPACE);

                    StringBuilder diffString;
                    diffIndex.setTextColor(Color.RED);

                    // Populate the blocks of the diff entry
                    dump1.setText(mDump1.get(sector)[block]);
                    dump2.setText(mDump2.get(sector)[block]);

                    if (blocks[block].length == 0) {
                        // Set diff line for identical blocks
                        diffIndex.setTextColor(Color.GREEN);
                        diffString = new StringBuilder(
                                getString(R.string.text_identical_data));
                    } else {
                        diffString = new StringBuilder(
                                "                                ");

                        // Go through all symbols to populate the diff line
                        for (int i : blocks[block]) {
                            diffString.setCharAt(i, 'x');
                        }
                    }

                    // Add diff entry
                    diffIndex.setText(diffString);
                    mDiffContent.addView(rl);
                }
            }
        }
    }

    /**
     * Open {@link FileChooser} to select the first dump
     */
    public void onChooseDump1(View view) {
        Intent intent = prepareFileChooserForDump();
        startActivityForResult(intent, FILE_CHOOSER_DUMP_FILE_1);
    }

    /**
     * Open {@link FileChooser} to select the second dump
     */
    public void onChooseDump2(View view) {
        Intent intent = prepareFileChooserForDump();
        startActivityForResult(intent, FILE_CHOOSER_DUMP_FILE_2);
    }

    /**
     * Read the {@link FileChooser#EXTRA_CHOSEN_FILE}
     * and convert its format using {@link #convertDumpFormat(String[])}
     */
    private SparseArray<String[]> processChosenDump(Intent data) {
        String path = data.getStringExtra(FileChooser.EXTRA_CHOSEN_FILE);
        File file = new File(path);
        String[] dump = Common.readFileLineByLine(file, false, this);
        int err = Common.isValidDump(dump, false);

        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            return null;
        } else {
            return convertDumpFormat(dump);
        }
    }

    private Intent prepareFileChooserForDump() {
        Intent intent = new Intent(this, FileChooser.class);
        intent.putExtra(FileChooser.EXTRA_DIR, Common.getFileFromStorage(
                Common.HOME_DIR + "/" + Common.DUMPS_DIR).getAbsolutePath());
        intent.putExtra(FileChooser.EXTRA_TITLE, getString(R.string.text_open_dump_title));
        intent.putExtra(FileChooser.EXTRA_BUTTON_TEXT, getString(R.string.action_open_dump_file));
        intent.putExtra(FileChooser.EXTRA_ENABLE_DELETE_FILE, true);
        return intent;
    }

    /**
     * Convert the format of a dump
     */
    private static SparseArray<String[]> convertDumpFormat(String[] dump) {
        SparseArray<String[]> ret = new SparseArray<>();
        int i = 0;
        int sector = 0;

        for (String line : dump) {
            if (line.startsWith("+")) {
                String[] tmp = line.split(": ");
                sector = Integer.parseInt(tmp[tmp.length-1]);
                i = 0;
                if (sector < 32) {
                    ret.put(sector, new String[4]);
                } else {
                    ret.put(sector, new String[16]);
                }
            } else {
                ret.get(sector)[i++] = line;
            }
        }
        return ret;
    }
}
