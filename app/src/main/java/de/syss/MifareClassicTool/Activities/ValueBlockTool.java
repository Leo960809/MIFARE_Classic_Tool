package de.syss.MifareClassicTool.Activities;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.Locale;

import de.syss.MifareClassicTool.Common;
import de.syss.MifareClassicTool.R;

/**
 * Decode value blocks from hex format to integer, vice versa
 * @author Jinhao Jiang
 */
public class ValueBlockTool extends BasicActivity {

    private EditText mVB;
    private EditText mVBasInt;
    private EditText mAddr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_value_block_tool);

        mVB = findViewById(R.id.editTextValueBlockToolVB);
        mVBasInt = findViewById(R.id.editTextValueBlockToolVBasInt);
        mAddr = findViewById(R.id.editTextValueBlockAddr);
    }

    /**
     * Decode a value block into integer format
     */
    public void onDecode(View view) {
        String data = mVB.getText().toString();

        if (!Common.isHexAnd16Byte(data, this)) {
            // Not hex and 16-byte
            return;
        }

        if (!Common.isValueBlock(data)) {
             // No value block
            Toast.makeText(this, R.string.info_is_not_vb,
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Decode
        byte[] vbAsBytes = Common.hexStringToByteArray(data.substring(0, 8));
        // Bytes -> Integer -> Reverse
        int vbAsInt = Integer.reverseBytes(ByteBuffer.wrap(vbAsBytes).getInt());
        mVBasInt.setText("" + vbAsInt);
        mAddr.setText(data.substring(24, 26));
    }

    /**
     * Encode integer format into a value block
     */
    @SuppressLint("SetTextI18n")
    public void onEncode(View view) {
        String vbText = mVBasInt.getText().toString();
        String addrText = mAddr.getText().toString();

        if (vbText.equals("")){
            // No integer to encode
            Toast.makeText(this, R.string.info_no_int_to_encode,
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (!addrText.matches("[0-9A-Fa-f]{2}")) {
            // No valid value block
            Toast.makeText(this, R.string.info_addr_not_hex_byte,
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Encode
        // String -> Integer
        int vbAsInt;
        try {
            vbAsInt = Integer.parseInt(vbText);
        } catch (NumberFormatException e) {
            // Number out of range
            String message = getString(R.string.info_invalid_int)
                    + " (Max: " + Integer.MAX_VALUE + ", Min: " + Integer.MIN_VALUE + ")";
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            return;
        }

        // Integer -> Reverse -> Byte array -> Hex string
        String vb = Common.byte2HexString(
                ByteBuffer.allocate(4).putInt(Integer.reverseBytes(vbAsInt)).array());
        // Integer -> Invert -> Reverse -> Byte array -> Hex string
        String vbInverted = Common.byte2HexString(
                ByteBuffer.allocate(4).putInt(Integer.reverseBytes(~vbAsInt)).array());
        String addrInverted = Integer.toHexString(
                ~Integer.parseInt(addrText, 16))
                .toUpperCase(Locale.getDefault()).substring(6, 8);
        mVB.setText(vb + vbInverted + vb + addrText + addrInverted + addrText + addrInverted);

    }

    /**
     * Copy the value Block to the Android clipboard
     */
    public void onCopyToClipboard(View view) {
        Common.copyToClipboard(mVB.getText().toString(), this, true);
    }

    /**
     * Paste the content of the Android clipboard to the value block
     */
    public void onPasteFromClipboard(View view) {
        String text = Common.getFromClipboard(this);
        if (text != null) {
            mVB.setText(text);
        }
    }

}
