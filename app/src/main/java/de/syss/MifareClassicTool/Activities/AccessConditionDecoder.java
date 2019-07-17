package de.syss.MifareClassicTool.Activities;

import android.os.Bundle;
import android.text.SpannableString;
import android.util.Log;
import android.widget.TableLayout;
import android.widget.TableLayout.LayoutParams;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import de.syss.MifareClassicTool.Common;
import de.syss.MifareClassicTool.Common.Operations;
import de.syss.MifareClassicTool.R;

/**
 * Display the MIFARE Access Conditions
 * @author Jinhao Jiang
 */
public class AccessConditionDecoder extends BasicActivity {

    public final static String EXTRA_AC = "de.syss.MifareClassicTool.Activity.AC";

    private static final String LOG_TAG = AccessConditionDecoder.class.getSimpleName();

    private TableLayout mLayout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_access_condition_decoder);

        if (getIntent().hasExtra(EXTRA_AC)) {
            mLayout = findViewById(R.id.tableLayoutAccessConditionDecoder);
            String[] accessConditions = getIntent().getStringArrayExtra(EXTRA_AC);

            for (int j = 0; j < accessConditions.length; j = j + 2) {
                boolean hasMoreThan4Blocks = false;

                if (accessConditions[j + 1].startsWith("*")) {
                    hasMoreThan4Blocks = true;
                    accessConditions[j + 1] = accessConditions[j + 1].substring(1);
                }

                // b6 = bAC[0], b7 = bAC[1], ...
                byte[] bAC = Common.hexStringToByteArray(accessConditions[j + 1]);

                // acMatrix[C1 - C3][Block1 - Block3 + Sector Trailer]
                byte[][] acMatrix = Common.acBytesToACMatrix(bAC);
                if (acMatrix != null) {
                    String sectorNumber = accessConditions[j].split(": ")[1];
                    addSectorAC(acMatrix, getString(R.string.text_sector)
                            + ": " + sectorNumber, hasMoreThan4Blocks);
                }
            }
        } else {
            Log.d(LOG_TAG, "No Access Conditions");
            finish();
        }
    }

    /**
     * Add the access condition information of the sector to the layout table
     */
    private void addSectorAC(byte[][] acMatrix, String sectorHeader, boolean hasMoreThan4Blocks) {
        // Add sector header
        TextView header = new TextView(this);
        header.setText(Common.colorString(sectorHeader,
                getResources().getColor(R.color.blue)),
                BufferType.SPANNABLE);
        TableRow tr = new TableRow(this);
        tr.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        tr.addView(header);
        mLayout.addView(tr, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        // Add Block 0-2
        addBlockAC(acMatrix, hasMoreThan4Blocks);

        // Add Sector Trailer
        addSectorTrailerAC(acMatrix);
    }

    /**
     * Add the access condition information of the data blocks to the table
     */
    private void addBlockAC(byte[][] acMatrix, boolean hasMoreThan4Blocks) {
        boolean isKeyBReadable = Common.isKeyBReadable(
                acMatrix[0][3], acMatrix[1][3], acMatrix[2][3]);

        for (int i = 0; i < 3; i++) {
            byte c1 = acMatrix[0][i];
            byte c2 = acMatrix[1][i];
            byte c3 = acMatrix[2][i];

            // Create row and header
            TableRow tr = new TableRow(this);
            String blockHeader;

            if (hasMoreThan4Blocks) {
                blockHeader = getString(R.string.text_block)
                        + ": " + (i * 4 + i) + "-" + (i * 4 + 4 + i);
            } else {
                blockHeader = getString(R.string.text_block) + ": " + i;
            }
            tr.setLayoutParams(new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));

            // Create cells
            TextView location = new TextView(this);
            location.setText(blockHeader);
            TextView read = new TextView(this);
            TextView write = new TextView(this);
            TextView incr = new TextView(this);
            TextView decr = new TextView(this);

            // Set cell texts to colored permissions
            read.setText(getColoredPermissionText(c1, c2, c3,
                    Operations.Read, false, isKeyBReadable));
            write.setText(getColoredPermissionText(c1, c2, c3,
                    Operations.Write, false, isKeyBReadable));
            incr.setText(getColoredPermissionText(c1, c2, c3,
                    Operations.Increment, false, isKeyBReadable));
            decr.setText(getColoredPermissionText(c1, c2, c3,
                    Operations.DecTransRest, false, isKeyBReadable));

            // Add cells to row
            tr.addView(location);
            tr.addView(read);
            tr.addView(write);
            tr.addView(incr);
            tr.addView(decr);

            // Add row to layout
            mLayout.addView(tr, new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
        }
    }

    /**
     * Add the access condition information of the sector trailer (last block) to the table
     */
    private void addSectorTrailerAC(byte[][] acMatrix) {
        byte c1 = acMatrix[0][3];
        byte c2 = acMatrix[1][3];
        byte c3 = acMatrix[2][3];

        // Create rows
        TextView[] read = new TextView[3];
        TextView[] write = new TextView[3];

        for (int i = 0; i < 3; i++) {
            read[i] = new TextView(this);
            write[i] = new TextView(this);
        }

        // Set row texts to colored permissions
        read[0].setText(getColoredPermissionText(c1, c2, c3,
                Operations.ReadKeyA, true, false));
        write[0].setText(getColoredPermissionText(c1, c2, c3,
                Operations.WriteKeyA, true, false));
        read[1].setText(getColoredPermissionText(c1, c2, c3,
                Operations.ReadAC, true, false));
        write[1].setText(getColoredPermissionText(c1, c2, c3,
                Operations.WriteAC, true, false));
        read[2].setText(getColoredPermissionText(c1, c2, c3,
                Operations.ReadKeyB, true, false));
        write[2].setText(getColoredPermissionText(c1, c2, c3,
                Operations.WriteKeyB, true, false));

        // Add rows to layout
        String[] headers = new String[] {"Key A:", "AC Bits:", "Key B:"};
        for (int i = 0; i < 3; i++) {
            TableRow tr = new TableRow(this);
            tr.setLayoutParams(new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
            TextView location = new TextView(this);
            location.setText(headers[i]);
            tr.addView(location);
            tr.addView(read[i]);
            tr.addView(write[i]);
            mLayout.addView(tr, new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
        }
    }

    /**
     * A helper function for {@link #addBlockAC(byte[][], boolean)}
     * and {@link #addSectorTrailerAC(byte[][])}
     */
    private SpannableString getColoredPermissionText(byte c1, byte c2, byte c3,
            Operations op, boolean isSectorTrailer, boolean isKeyBReadable) {
        switch (Common.getOperationInfoForBlock(c1, c2, c3, op,
                isSectorTrailer, isKeyBReadable)) {
            case 0:
                // Never
                return Common.colorString(getString(R.string.text_never),
                        getResources().getColor(R.color.orange));
            case 1:
                // Key A
                return Common.colorString(getString(R.string.text_key_a),
                        getResources().getColor(R.color.yellow));
            case 2:
                // Key B
                return Common.colorString(getString(R.string.text_key_b),
                    getResources().getColor(R.color.yellow));
            case 3:
                // Key A|B
                return Common.colorString(getString(R.string.text_key_ab),
                    getResources().getColor(R.color.light_green));
            case 4:
                // Access Condition Error
                return Common.colorString(getString(R.string.text_ac_error),
                    getResources().getColor(R.color.red));
            default:
                // Error
                return new SpannableString("");
        }
    }
}
