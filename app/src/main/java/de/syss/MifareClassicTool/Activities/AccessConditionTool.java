package de.syss.MifareClassicTool.Activities;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import de.syss.MifareClassicTool.Common;
import de.syss.MifareClassicTool.R;

/**
 * Decode MIFARE Classic Access Conditions
 * @author Jinhao Jiang
 */
public class AccessConditionTool extends BasicActivity {

    private EditText mAC;
    private Button[] mBlockButtons;

    private boolean mWasKeyBReadable;
    private boolean mIsKeyBReadable;

    private byte[][] mACMatrix;

    private Button mSelectedButton;

    private AlertDialog mDataBlockDialog;
    private AlertDialog mSectorTrailerDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_access_condition_tool);

        // Initialize member variables
        mAC = findViewById(R.id.editTextAccessConditionToolAC);
        mBlockButtons = new Button[4];
        mBlockButtons[0] = findViewById(R.id.buttonAccessConditionToolBlock0);
        mBlockButtons[1] = findViewById(R.id.buttonAccessConditionToolBlock1);
        mBlockButtons[2] = findViewById(R.id.buttonAccessConditionToolBlock2);
        mBlockButtons[3] = findViewById(R.id.buttonAccessConditionToolBlock3);

        // Initialize AC matrix
        mACMatrix = new byte[][] {
                {0, 0, 0, 0},
                {0, 0, 0, 0},
                {0, 0, 0, 1} };

        // Build the dialog for the sector trailer
        String[] items = new String[8];
        for (int i = 0; i < 8; i++) {
            items[i] = getString(getResourceForSectorTrailersByRowNr(i));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, R.layout.list_item_small_text, items);
        ListView lv = new ListView(this);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(
                (parent, view, position, id) -> {
                    // Set button text to the selected Access Conditions
                    mBlockButtons[3].setText(getString(
                            getResourceForSectorTrailersByRowNr(position)));

                    // Set Access Condition bits for sector trailer
                    byte[] acBits = acRowNrToACBits(position, true);
                    mACMatrix[0][3] = acBits[0];
                    mACMatrix[1][3] = acBits[1];
                    mACMatrix[2][3] = acBits[2];

                    // Rebuild the data block dialog
                    mIsKeyBReadable = position < 2 || position == 4;
                    buildDataBlockDialog(true);
                    mSectorTrailerDialog.dismiss();
                });
        mSectorTrailerDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_choose_ac_title)
                .setView(lv)
                .create();

        // Build the dialog with Access Conditions for data blocks
        mIsKeyBReadable = true;
        buildDataBlockDialog(false);
    }

    /**
     * Convert the 3 Access Condition bytes into a more human readable format
     */
    public void onDecode(View view) {
        String ac = mAC.getText().toString();
        if (ac.length() != 6) {
            // Not 3-byte (6-char)
            Toast.makeText(this, R.string.info_ac_not_3_byte,
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!ac.matches("[0-9A-Fa-f]+")) {
            // Not hex
            Toast.makeText(this, R.string.info_ac_not_hex,
                    Toast.LENGTH_LONG).show();
            return;
        }

        byte[][] acMatrix = Common.acBytesToACMatrix(Common.hexStringToByteArray(ac));
        boolean error = false;
        if (acMatrix != null) {
            // Set sector trailer
            byte[] acBits = {acMatrix[0][3], acMatrix[1][3], acMatrix[2][3]};
            int rowNr = acBitsToACRowNr(acBits, true);

            if (rowNr != -1) {
                // Check if key B is readable
                mIsKeyBReadable = rowNr < 2 || rowNr == 4;
                mBlockButtons[3].setText(getString(getResourceForSectorTrailersByRowNr(rowNr)));

                // Set data blocks
                for (int i = 0; i < 3; i++) {
                    acBits = new byte [] {acMatrix[0][i], acMatrix[1][i], acMatrix[2][i]};
                    rowNr = acBitsToACRowNr(acBits, false);
                    if (rowNr == -1) {
                        // ERROR
                        error = true;
                        break;
                    }
                    mBlockButtons[i].setText(getString(getResourceForDataBlocksByRowNr(rowNr)));
                }
            } else {
                // ERROR
                error = true;
            }
        } else {
            // ERROR
            error = true;
        }

        if (error) {
            // Display the error message
            Toast.makeText(this, R.string.info_ac_format_error,
                    Toast.LENGTH_LONG).show();
            return;
        }

        mACMatrix = acMatrix;
        buildDataBlockDialog(false);
    }

    /**
     * Convert the {@link #mACMatrix} to 3 Access Condition bytes
     */
    public void onEncode(View view) {
        mAC.setText(Common.byte2HexString(Common.acMatrixToACBytes(mACMatrix)));
    }

    /**
     * Backup the button
     */
    public void onChooseACforDataBock(View view) {
        mSelectedButton = (Button) view;
        mDataBlockDialog.show();
    }

    /**
     * Show the Access Condition chooser dialog for ({@link #mSectorTrailerDialog})
     */
    public void onChooseACforSectorTrailer(View view) {
        mSectorTrailerDialog.show();
    }

    /**
     * Copy the MIFARE Classic Access Conditions to the Android clipboard
     */
    public void onCopyToClipboard(View view) {
        Common.copyToClipboard(mAC.getText().toString(), this, true);
    }

    /**
     * Paste the content of the Android clipboard to the Access Conditions edit text
     */
    public void onPasteFromClipboard(View view) {
        String text = Common.getFromClipboard(this);
        if (text != null) {
            mAC.setText(text);
        }
    }

    /**
     * Return the resource ID of an Access Condition string for data blocks
     */
    private int getResourceForDataBlocksByRowNr(int rowNr) {
        String prefix = "ac_data_block_";
        if (mIsKeyBReadable) {
            prefix = "ac_data_block_no_keyb_";
        }
        return getResourceForAccessCondition(prefix, rowNr);
    }

    /**
     * Return the resource ID of an Access Condition string for sector trailers
     */
    private int getResourceForSectorTrailersByRowNr(int rowNr) {
        return getResourceForAccessCondition("ac_sector_trailer_", rowNr);
    }

    /**
     * A helper function for {@link #getResourceForDataBlocksByRowNr(int)}
     * and {@link #getResourceForSectorTrailersByRowNr(int)}.
     */
    private int getResourceForAccessCondition(String prefix, int rowNr) {
        return getResources().getIdentifier(
                prefix + rowNr, "string", getPackageName());
    }

    /**
     * Convert the the row number of the Access Condition table
     * to its corresponding access bits C1, C2 and C3
     */
    private byte[] acRowNrToACBits(int rowNr, boolean isSectorTrailer) {
        if (!isSectorTrailer && mIsKeyBReadable && rowNr > 1) {
            switch (rowNr) {
                case 2:
                    return new byte[] {0, 0, 1};
                case 3:
                    return new byte[] {1, 1, 1};
                default:
                    return null;
            }
        }

        switch (rowNr) {
            case 0:
                return new byte[] {0, 0, 0};
            case 1:
                return new byte[] {0, 1, 0};
            case 2:
                return new byte[] {1, 0, 0};
            case 3:
                return new byte[] {1, 1, 0};
            case 4:
                return new byte[] {0, 0, 1};
            case 5:
                return new byte[] {0, 1, 1};
            case 6:
                return new byte[] {1, 0, 1};
            case 7:
                return new byte[] {1, 1, 1};
            default:
                // ERROR
                return null;
        }
    }

    /**
     * Convert the access bits C1, C2 and C3
     * to its corresponding row number in the Access Condition table
     */
    private int acBitsToACRowNr(byte[] acBits, boolean isSectorTrailer) {
        if (acBits != null && acBits.length != 3) {
            return -1;
        }

        if (!isSectorTrailer && mIsKeyBReadable) {
            if (acBits[0] == 0 && acBits[1] == 0 && acBits[2] == 0) {
                return 0;
            } else if (acBits[0] == 0 && acBits[1] == 1 && acBits[2] == 0) {
                return 1;
            } else if (acBits[0] == 0 && acBits[1] == 0 && acBits[2] == 1) {
                return 2;
            } else if (acBits[0] == 1 && acBits[1] == 1 && acBits[2] == 1) {
                return 3;
            }
        } else {
            if (acBits[0] == 0 && acBits[1] == 0 && acBits[2] == 0) {
                return 0;
            } else if (acBits[0] == 0 && acBits[1] == 1 && acBits[2] == 0) {
                return 1;
            } else if (acBits[0] == 1 && acBits[1] == 0 && acBits[2] == 0) {
                return 2;
            } else if (acBits[0] == 1 && acBits[1] == 1 && acBits[2] == 0) {
                return 3;
            } else if (acBits[0] == 0 && acBits[1] == 0 && acBits[2] == 1) {
                return 4;
            } else if (acBits[0] == 0 && acBits[1] == 1 && acBits[2] == 1) {
                return 5;
            } else if (acBits[0] == 1 && acBits[1] == 0 && acBits[2] == 1) {
                return 6;
            } else if (acBits[0] == 1 && acBits[1] == 1 && acBits[2] == 1) {
                return 7;
            }
        }

        // ERROR
        return -1;
    }

    /**
     * Rebuild {@link #mDataBlockDialog} based on {@link #mIsKeyBReadable}
     */
    private void buildDataBlockDialog(boolean resetBlockACs) {
        String[] items;

        if (mIsKeyBReadable && !mWasKeyBReadable) {
            items = new String[4];
            for (int i = 0; i < 4; i++) {
                items[i] = getString(getResourceForDataBlocksByRowNr(i));
            }
            mWasKeyBReadable = true;
        } else if (!mIsKeyBReadable && mWasKeyBReadable){
            items = new String[8];
            for (int i = 0; i < 8; i++) {
                items[i] = getString(getResourceForDataBlocksByRowNr(i));
            }
            mWasKeyBReadable = false;
        } else {
            return;
        }

        if (resetBlockACs) {
            // Reset mACMatrix and update button text
            for (int i = 0; i < 3; i++) {
                mBlockButtons[i].setText(items[0]);
                mACMatrix[0][i] = 0;
                mACMatrix[1][i] = 0;
                mACMatrix[2][i] = 0;
            }
            int r;

            if (mIsKeyBReadable) {
                r = R.string.info_ac_reset_keyb_readable;
            } else {
                r = R.string.info_ac_reset_keyb_not_readable;
            }
            Toast.makeText(this, r, Toast.LENGTH_LONG).show();
        }

        ListAdapter adapter = new ArrayAdapter<>(
                this, R.layout.list_item_small_text, items);
        ListView lv = new ListView(this);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(
                (parent, view, position, id) -> {
                    // Set button text to selected Access Conditions
                    mSelectedButton.setText(getString(
                            getResourceForDataBlocksByRowNr(position)));

                    // Set Access Condition bits for this block
                    byte[] acBits = acRowNrToACBits(position, false);
                    int blockNr = Integer.parseInt(mSelectedButton.getTag().toString());
                    mACMatrix[0][blockNr] = acBits[0];
                    mACMatrix[1][blockNr] = acBits[1];
                    mACMatrix[2][blockNr] = acBits[2];
                    mDataBlockDialog.dismiss();
                });
        mDataBlockDialog =  new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_choose_ac_title)
                .setView(lv)
                .create();
    }
}
