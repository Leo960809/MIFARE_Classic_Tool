package de.syss.MifareClassicTool.Activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import de.syss.MifareClassicTool.Common;
import de.syss.MifareClassicTool.R;

/**
 * Display tag info
 * @author Jinhao Jiang
 */
public class TagInfoTool extends BasicActivity {

    private LinearLayout mLayout;
    private TextView mErrorMessage;
    private int mMFCSupport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag_info_tool);

        mLayout = findViewById(R.id.linearLayoutTagInfoTool);
        mErrorMessage = findViewById(R.id.textTagInfoToolErrorMessage);
        updateTagInfo(Common.getTag());
    }

    @Override
    public void onNewIntent(Intent intent) {
        Common.treatAsNewTag(intent, this);
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
            updateTagInfo(Common.getTag());
        }
    }

    /**
     * Show a dialog with further information
     */
    public void onReadMore(View view) {
        int titleID = 0;
        int messageID = 0;

        if (mMFCSupport == -1) {
            // Device does not support MIFARE Classic
            titleID = R.string.dialog_no_mfc_support_device_title;
            messageID = R.string.dialog_no_mfc_support_device;
        } else if (mMFCSupport == -2) {
            // Tag does not support MIFARE Classic
            titleID = R.string.dialog_no_mfc_support_tag_title;
            messageID = R.string.dialog_no_mfc_support_tag;
        }

        CharSequence styledText = Html.fromHtml(getString(messageID));
        AlertDialog ad = new AlertDialog.Builder(this)
                .setTitle(titleID)
                .setMessage(styledText)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.action_ok,
                        (dialog, which) -> {
                        })
                .show();

        // Make links clickable
        ((TextView)ad.findViewById(android.R.id.message))
                .setMovementMethod(LinkMovementMethod.getInstance());
    }

    /**
     * Update and display the tag information
     */
    private void updateTagInfo(Tag tag) {
        if (tag != null) {
            // Check MIFARE Classic support
            mMFCSupport = Common.checkMifareClassicSupport(tag, this);

            mLayout.removeAllViews();
            // Display generic info
            TextView headerGenericInfo = new TextView(this);
            headerGenericInfo.setText(Common.colorString(
                    getString(R.string.text_generic_info),
                    getResources().getColor(R.color.blue)));
            headerGenericInfo.setTextAppearance(this,
                    android.R.style.TextAppearance_Large);
            headerGenericInfo.setGravity(Gravity.CENTER_HORIZONTAL);

            int pad = Common.dpToPx(5);
            headerGenericInfo.setPadding(pad, pad, pad, pad);
            mLayout.addView(headerGenericInfo);
            TextView genericInfo = new TextView(this);
            genericInfo.setPadding(pad, pad, pad, pad);
            genericInfo.setTextAppearance(this,
                    android.R.style.TextAppearance_Medium);
            mLayout.addView(genericInfo);

            String uid = Common.byte2HexString(tag.getId());
            int uidLen = tag.getId().length;

            uid += " (" + uidLen + " byte";
            if (uidLen == 7) {
                uid += ", CL2";
            } else if (uidLen == 10) {
                uid += ", CL3";
            }
            uid += ")";

            NfcA nfca = NfcA.get(tag);

            byte[] atqaBytes = nfca.getAtqa();
            atqaBytes = new byte[] {atqaBytes[1], atqaBytes[0]};
            String atqa = Common.byte2HexString(atqaBytes);
            byte[] sakBytes = new byte[] {
                    (byte)((nfca.getSak() >> 8) & 0xFF), (byte)(nfca.getSak() & 0xFF)
            };
            String sak;
            // Print the first SAK byte (only if it is not 0)
            if (sakBytes[0] != 0) {
                sak = Common.byte2HexString(sakBytes);
            } else {
                sak = Common.byte2HexString(new byte[] {sakBytes[1]});
            }
            String ats = "-";
            IsoDep iso = IsoDep.get(tag);
            if (iso != null ) {
                byte[] atsBytes = iso.getHistoricalBytes();
                if (atsBytes != null && atsBytes.length > 0) {
                    ats = Common.byte2HexString(atsBytes);
                }
            }

            // Identify tag type
            int tagTypeResourceID = getTagIdentifier(atqa, sak, ats);
            String tagType;
            if (tagTypeResourceID == R.string.tag_unknown && mMFCSupport > -2) {
                tagType = getString(R.string.tag_unknown_mf_classic);
            } else {
                tagType = getString(tagTypeResourceID);
            }

            int hc = getResources().getColor(R.color.blue);
            genericInfo.setText(TextUtils.concat(Common.colorString(
                    getString(R.string.text_uid) + ":", hc),
                    "\n", uid, "\n",
                    Common.colorString(getString(R.string.text_rf_tech) + ":", hc),
                    "\n", getString(R.string.text_rf_tech_14a), "\n",
                    Common.colorString(getString(R.string.text_atqa) + ":", hc),
                    "\n", atqa, "\n",
                    Common.colorString(getString(R.string.text_sak) + ":", hc),
                    "\n", sak, "\n",
                    Common.colorString(getString(R.string.text_ats) + ":", hc),
                    "\n", ats, "\n",
                    Common.colorString(getString(R.string.text_tag_type_and_manuf) + ":", hc),
                    "\n", tagType));

            // Warning: Tag type might be wrong
            if (tagTypeResourceID != R.string.tag_unknown) {
                TextView tagTypeInfo = new TextView(this);
                tagTypeInfo.setPadding(pad, 0, pad, pad);
                tagTypeInfo.setText("(" + getString(R.string.text_tag_type_guess) + ")");
                mLayout.addView(tagTypeInfo);
            }

            LinearLayout layout = findViewById(R.id.linearLayoutTagInfoToolSupport);
            // Check MIFARE Classic support
            if (mMFCSupport == 0) {
                // Display MIFARE Classic info
                TextView headerMifareInfo = new TextView(this);
                headerMifareInfo.setText(Common.colorString(
                        getString(R.string.text_mf_info),
                        getResources().getColor(R.color.blue)));
                headerMifareInfo.setTextAppearance(this,
                        android.R.style.TextAppearance_Large);
                headerMifareInfo.setGravity(Gravity.CENTER_HORIZONTAL);
                headerMifareInfo.setPadding(pad, pad * 2, pad, pad);
                mLayout.addView(headerMifareInfo);
                TextView mifareInfo = new TextView(this);
                mifareInfo.setPadding(pad, pad, pad, pad);
                mifareInfo.setTextAppearance(this,
                        android.R.style.TextAppearance_Medium);
                mLayout.addView(mifareInfo);

                // Get MIFARE info
                MifareClassic mfc = MifareClassic.get(tag);
                String size = "" + mfc.getSize();
                String sectorCount = "" + mfc.getSectorCount();
                String blockCount = "" + mfc.getBlockCount();
                mifareInfo.setText(TextUtils.concat(
                        Common.colorString(getString(R.string.text_mem_size) + ":", hc),
                        "\n", size, " byte\n",
                        Common.colorString(getString(R.string.text_block_size) + ":", hc),
                        // Block size is always 16-byte on MIFARE Classic Tags
                        "\n", "" + MifareClassic.BLOCK_SIZE, " byte\n",
                        Common.colorString(getString(R.string.text_sector_count) + ":", hc),
                        "\n", sectorCount, "\n",
                        Common.colorString(getString(R.string.text_block_count) + ":", hc),
                        "\n", blockCount));
                layout.setVisibility(View.GONE);
            } else if (mMFCSupport == -1) {
                // No MIFARE Classic Support
                mErrorMessage.setText(R.string.text_no_mfc_support_device);
                layout.setVisibility(View.VISIBLE);
            } else if (mMFCSupport == -2) {
                // The tag does not support MIFARE Classic
                mErrorMessage.setText(R.string.text_no_mfc_support_tag);
                layout.setVisibility(View.VISIBLE);
            }
        } else {
            // No tag found
            TextView text = new TextView(this);
            int pad = Common.dpToPx(5);
            text.setPadding(pad, pad, 0, 0);
            text.setTextAppearance(this, android.R.style.TextAppearance_Large);
            text.setText(getString(R.string.text_no_tag));
            mLayout.removeAllViews();
            mLayout.addView(text);
            Toast.makeText(this, R.string.info_no_tag_found,
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Get the tag type resource ID from ATQA + SAK + ATS
     */
    private int getTagIdentifier(String atqa, String sak, String ats) {
        String prefix = "tag_";
        ats = ats.replace("-", "");

        // Check ATQA + SAK + ATS
        int ret = getResources().getIdentifier(
                prefix + atqa + sak + ats, "string", getPackageName());

        if (ret == 0) {
            // Check ATQA + SAK
            ret = getResources().getIdentifier(
                    prefix + atqa + sak, "string", getPackageName());
        }

        if (ret == 0) {
            // Check ATQA
            ret = getResources().getIdentifier(
                    prefix + atqa, "string", getPackageName());
        }

        if (ret == 0) {
            // Match not found
            return R.string.tag_unknown;
        }

        return ret;
    }
}
