package de.syss.MifareClassicTool;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.NfcA;
import android.os.Environment;
import android.preference.PreferenceManager;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;

import de.syss.MifareClassicTool.Activities.IActivityThatReactsToSave;

import static de.syss.MifareClassicTool.Activities.Preferences.Preference.AutoCopyUID;
import static de.syss.MifareClassicTool.Activities.Preferences.Preference.UIDFormat;
import static de.syss.MifareClassicTool.Activities.Preferences.Preference.UseInternalStorage;

/**
 * Common functions and variables
 * @author Jinhao Jiang
 */
public class Common extends Application {

    // The name of the root directory of the app
    public static final String HOME_DIR = "/MifareClassicTool";
    // The name of the key files directory
    public static final String KEYS_DIR = "key-files";
    // The name of the dump files directory
    public static final String DUMPS_DIR = "dump-files";
    // The name of the folder where temporary files are stored
    public static final String TMP_DIR = "tmp";

    // This file contains some standard MIFARE keys
    public static final String STD_KEYS = "std.keys";
    // Some extended keys
    public static final String STD_KEYS_EXTENDED = "extended-std.keys";

    // Possible operations on a MIFARE Classic tag
    public enum Operations {
        Read, Write, Increment, DecTransRest,
        ReadKeyA, ReadKeyB, ReadAC,
        WriteKeyA, WriteKeyB, WriteAC
    }

    private static final String LOG_TAG = Common.class.getSimpleName();

    private static Tag mTag = null;
    private static byte[] mUID = null;

    private static SparseArray<byte[][]> mKeyMap = null;
    private static int mKeyMapFrom = -1;
    private static int mKeyMapTo = -1;

    private static String mVersionCode;

    private static boolean mUseAsEditorOnly = false;
    private static int mHasMifareClassicSupport = 0;
    private static ComponentName mPendingComponentName = null;

    private static NfcAdapter mNfcAdapter;
    private static Context mAppContext;
    private static float mScale;

    @Override
    public void onCreate() {
        super.onCreate();
        mAppContext = getApplicationContext();
        mScale = getResources().getDisplayMetrics().density;

        try {
            mVersionCode = getPackageManager().
                    getPackageInfo(getPackageName(), 0).versionName;
        } catch (NameNotFoundException e) {
            Log.d(LOG_TAG, "Version NOT found :(");
        }
    }

    /**
     * Check if the read/write permission to the external storage is given
     */
    public static boolean hasWritePermissionToExternalStorage(Context context) {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }


    /**
     * Check if external storage is available for read/write
     */
    public static boolean isExternalStorageMounted() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    public static boolean isExternalStorageWritableErrorToast(Context context) {
        if (!isExternalStorageMounted()) {
            Toast.makeText(context, R.string.info_no_external_storage,
                    Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    /**
     * Create a file with a path that consists of its storage
     */
    public static File getFileFromStorage(String relativePath) {
        File file;
        boolean isUseInternalStorage = getPreferences().getBoolean(
                UseInternalStorage.toString(), false);

        if (isUseInternalStorage) {
            // Use internal storage
            file = new File(mAppContext.getFilesDir() + relativePath);
        } else {
            // Use external storage (DEFAULT)
            file = new File(Environment.getExternalStorageDirectory() + relativePath);
        }

        return file;
    }

    /**
     * Read the given file line by line
     */
    public static String[] readFileLineByLine(File file, boolean readComments, Context context) {
        BufferedReader br = null;
        String[] ret = null;

        if (file != null  && isExternalStorageMounted() && file.exists()) {
            try {
                br = new BufferedReader(new FileReader(file));
                String line;
                ArrayList<String> linesArray = new ArrayList<>();

                while ((line = br.readLine()) != null)   {
                    // Ignore empty lines and comments if readComments == false
                    if ( !line.equals("")
                            && (readComments || !line.startsWith("#"))) {
                        try {
                            linesArray.add(line);
                        } catch (OutOfMemoryError e) {
                            Toast.makeText(context, R.string.info_file_too_big,
                                    Toast.LENGTH_LONG).show();
                            return null;
                        }
                    }
                }

                if (linesArray.size() > 0) {
                    ret = linesArray.toArray(new String[linesArray.size()]);
                } else {
                    ret = new String[] {""};
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "ERROR while reading from file "
                        + file.getPath() + " :(" , e);
                ret = null;
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    }
                    catch (IOException e) {
                        Log.e(LOG_TAG, "ERROR while closing file :(", e);
                        ret = null;
                    }
                }
            }
        }

        return ret;
    }

    /**
     * Check if the file already exists
     * If so, present a dialog with the options: "Replace", "Append" or "Cancel"
     *
     * @see #saveFile(File, String[], boolean)
     * @see #saveFileAppend(File, String[], boolean)
     */
    public static void checkFileExistenceAndSave(final File file,
            final String[] lines, final boolean isDump, final Context context,
            final IActivityThatReactsToSave activity) {
        if (file.exists()) {
            // Save conflict for dump file or key file?
            int message = R.string.dialog_save_conflict_keyfile;
            if (isDump) {
                message = R.string.dialog_save_conflict_dump;
            }

            // File already exists
            // Replace? Append? Cancel?
            new AlertDialog.Builder(context)
            .setTitle(R.string.dialog_save_conflict_title)
            .setMessage(message)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(R.string.action_replace,
                    (dialog, which) -> {
                        // Replace
                        if (Common.saveFile(file, lines, false)) {
                            Toast.makeText(context, R.string.info_save_successful,
                                    Toast.LENGTH_LONG).show();
                            activity.onSaveSuccessful();
                        } else {
                            Toast.makeText(context, R.string.info_save_error,
                                    Toast.LENGTH_LONG).show();
                            activity.onSaveFailure();
                        }
                    })
            .setNeutralButton(R.string.action_append,
                    (dialog, which) -> {
                        // Append
                        if (Common.saveFileAppend(file, lines, isDump)) {
                            Toast.makeText(context, R.string.info_save_successful,
                                    Toast.LENGTH_LONG).show();
                            activity.onSaveSuccessful();
                        } else {
                            Toast.makeText(context, R.string.info_save_error,
                                    Toast.LENGTH_LONG).show();
                            activity.onSaveFailure();
                        }
                    })
            .setNegativeButton(R.string.action_cancel,
                    (dialog, id) -> {
                        // Cancel
                        activity.onSaveFailure();
                    }).show();
        } else {
            if (Common.saveFile(file, lines, false)) {
                Toast.makeText(context, R.string.info_save_successful,
                        Toast.LENGTH_LONG).show();
                activity.onSaveSuccessful();
            } else {
                Toast.makeText(context, R.string.info_save_error,
                        Toast.LENGTH_LONG).show();
                activity.onSaveFailure();
            }
        }
    }

    /**
     * Append an array of strings to the given file
     */
    public static boolean saveFileAppend(File file, String[] lines, boolean comment) {
        if (comment) {
            // Append to an existing file
            String[] newLines = new String[lines.length + 4];
            System.arraycopy(lines, 0, newLines, 4, lines.length);
            newLines[1] = "";
            newLines[2] = "# Append #######################";
            newLines[3] = "";
            lines = newLines;
        }
        return saveFile(file, lines, true);
    }

    /**
     * Write an array of strings to the given file
     */
    public static boolean saveFile(File file, String[] lines, boolean append) {
        boolean noError = true;

        if (file != null && lines != null && isExternalStorageMounted()) {
            BufferedWriter bw = null;

            try {
                bw = new BufferedWriter(new FileWriter(file, append));
                // Add new line before appending
                if (append) {
                    bw.newLine();
                }

                int i;
                for (i = 0; i < lines.length - 1; i++) {
                    bw.write(lines[i]);
                    bw.newLine();
                }
                bw.write(lines[i]);
            } catch (IOException | NullPointerException ex) {
                Log.e(LOG_TAG, "ERROR while writing to '" + file.getName() + "' :(", ex);
                noError = false;
            } finally {
                if (bw != null) {
                    try {
                        bw.close();
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "ERROR while closing file :(", e);
                        noError = false;
                    }
                }
            }
        } else {
            noError = false;
        }

        return noError;
    }



    /**
     * Get the shared preferences with application context for saving/loading values
     */
    public static SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(mAppContext);
    }

    public static void enableNfcForegroundDispatch(Activity targetActivity) {
        if (mNfcAdapter != null && mNfcAdapter.isEnabled()) {

            Intent intent = new Intent(targetActivity, targetActivity.getClass()).addFlags(
                            Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    targetActivity, 0, intent, 0);
            mNfcAdapter.enableForegroundDispatch(
                    targetActivity, pendingIntent, null, new String[][] {
                            new String[] { NfcA.class.getName() }
                    });
        }
    }

    public static void disableNfcForegroundDispatch(Activity targetActivity) {
        if (mNfcAdapter != null && mNfcAdapter.isEnabled()) {
            mNfcAdapter.disableForegroundDispatch(targetActivity);
        }
    }

    /**
     * Treat intent with a new tag
     *
     * @see #mTag
     * @see #mUID
     * @see #checkMifareClassicSupport(Tag, Context)
     */
    public static int treatAsNewTag(Intent intent, Context context) {
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            setTag(tag);
            boolean isCopyUID = getPreferences().getBoolean(AutoCopyUID.toString(), false);

            if (isCopyUID) {
                int format = getPreferences().getInt(UIDFormat.toString(), 0);
                String fmtUID = byte2String(tag.getId(),format);

                Toast.makeText(context, "UID " + context.getResources().getString(
                        R.string.info_copied_to_clipboard).toLowerCase() + " (" + fmtUID + ")",
                        Toast.LENGTH_SHORT).show();

                copyToClipboard(fmtUID, context, false);
            } else {
                String id = context.getResources().getString(R.string.info_new_tag_found)
                        + " (UID: " + byte2HexString(tag.getId()) + ")";
                Toast.makeText(context, id, Toast.LENGTH_LONG).show();
            }
            return checkMifareClassicSupport(tag, context);
        }

        return -4;
    }

    /**
     * Check if the device supports the MIFARE Classic technology
     *
     * @see #mHasMifareClassicSupport
     * @see #mUseAsEditorOnly
     */
    public static boolean hasMifareClassicSupport() {
        if (mHasMifareClassicSupport != 0) {
            return mHasMifareClassicSupport == 1;
        }

        if (NfcAdapter.getDefaultAdapter(mAppContext) == null) {
            mUseAsEditorOnly = true;
            mHasMifareClassicSupport = -1;
            return false;
        }

        mHasMifareClassicSupport = 1;
        return true;
    }

    /**
     * Check if the tag and the device support the MIFARE Classic technology
     */
    public static int checkMifareClassicSupport(Tag tag, Context context) {
        if (tag == null || context == null) {
            return -3;
        }

        if (Arrays.asList(tag.getTechList()).contains(MifareClassic.class.getName())) {
            return 0;
        } else {
            // If the device does not support MIFARE Classic?
            // Check if the SAK of the tag indicate that it's a MIFARE Classic tag
            NfcA nfca = NfcA.get(tag);
            byte sak = (byte)nfca.getSak();

            if ((sak >> 1 & 1) == 1) {
                // RFU
                return -2;
            } else {
                // SAK bit 4 = 1?
                if ((sak >> 3 & 1) == 1) {
                    // SAK bit 5 = 1?
                    if((sak >> 4 & 1) == 1) {
                        // MIFARE Classic 4k
                        // MIFARE SmartMX 4K
                        // MIFARE PlusS 4K SL1
                        // MIFARE PlusX 4K SL1
                        return -1;
                    } else {
                        // SAK bit 1 = 1?
                        if ((sak & 1) == 1) {
                            // MIFARE Mini
                            return -1;
                        } else {
                            // MIFARE Classic 1k
                            // MIFARE SmartMX 1k
                            // MIFARE PlusS 2K SL1
                            // MIFARE PlusX 2K SL2
                            return -1;
                        }
                    }
                } else {
                    return -2;
                }
            }
        }
    }

    /**
     * Open another app
     */
    public static boolean openApp(Context context, String packageName) {
        PackageManager manager = context.getPackageManager();

        try {
            Intent intent = manager.getLaunchIntentForPackage(packageName);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Create a {@link MCReader} for the given MIFARE Classic tag
     */
    public static MCReader checkForTagAndCreateReader(Context context) {
        MCReader reader;
        boolean tagLost = false;

        if (mTag != null && (reader = MCReader.get(mTag)) != null) {
            try {
                reader.connect();
            } catch (Exception e) {
                tagLost = true;
            }

            if (!tagLost && !reader.isConnected()) {
                reader.close();
                tagLost = true;
            }

            if (!tagLost) {
                return reader;
            }
        }

        // ERROR: No tag found
        Toast.makeText(context, R.string.info_no_tag_found, Toast.LENGTH_LONG).show();
        return null;
    }

    /**
     * Depending on the provided access conditions, this method will return
     * with which key you can achieve the ({@link Operations}) you select
     */
    public static int getOperationInfoForBlock(byte c1, byte c2, byte c3,
            Operations op, boolean isSectorTrailer, boolean isKeyBReadable) {
        // Is sector trailer?
        if (isSectorTrailer) {
            if (op != Operations.ReadKeyA
                    && op != Operations.ReadKeyB
                    && op != Operations.ReadAC
                    && op != Operations.WriteKeyA
                    && op != Operations.WriteKeyB
                    && op != Operations.WriteAC) {
                // No sector trailer permission
                return 4;
            }
            if (c1 == 0 && c2 == 0 && c3 == 0) {
                if (op == Operations.WriteKeyA
                        || op == Operations.WriteKeyB
                        || op == Operations.ReadKeyB
                        || op == Operations.ReadAC) {
                    return 1;
                }
                return 0;
            } else if (c1 == 0 && c2 == 1 && c3 == 0) {
                if (op == Operations.ReadKeyB
                        || op == Operations.ReadAC) {
                    return 1;
                }
                return 0;
            } else if (c1 == 1 && c2 == 0 && c3 == 0) {
                if (op == Operations.WriteKeyA
                        || op == Operations.WriteKeyB) {
                    return 2;
                }
                if (op == Operations.ReadAC) {
                    return 3;
                }
                return 0;
            } else if (c1 == 1 && c2 == 1 && c3 == 0) {
                if (op == Operations.ReadAC) {
                    return 3;
                }
                return 0;
            } else if (c1 == 0 && c2 == 0 && c3 == 1) {
                if (op == Operations.ReadKeyA) {
                    return 0;
                }
                return 1;
            } else if (c1 == 0 && c2 == 1 && c3 == 1) {
                if (op == Operations.ReadAC) {
                    return 3;
                }
                if (op == Operations.ReadKeyA
                        || op == Operations.ReadKeyB) {
                    return 0;
                }
                return 2;
            } else if (c1 == 1 && c2 == 0 && c3 == 1) {
                if (op == Operations.ReadAC) {
                    return 3;
                }
                if (op == Operations.WriteAC) {
                    return 2;
                }
                return 0;
            } else if (c1 == 1 && c2 == 1 && c3 == 1) {
                if (op == Operations.ReadAC) {
                    return 3;
                }
                return 0;
            } else {
                return -1;
            }
        } else {
            // Data block
            if (op != Operations.Read
                    && op != Operations.Write
                    && op != Operations.Increment
                    && op != Operations.DecTransRest) {
                // No data block permission
                return -1;
            }
            if (c1 == 0 && c2 == 0 && c3 == 0) {
                return (isKeyBReadable) ? 1 : 3;
            } else if (c1 == 0 && c2 == 1 && c3 == 0) {
                if (op == Operations.Read) {
                    return (isKeyBReadable) ? 1 : 3;
                }
                return 0;
            } else if (c1 == 1 && c2 == 0 && c3 == 0) {
                if (op == Operations.Read) {
                    return (isKeyBReadable) ? 1 : 3;
                }
                if (op == Operations.Write) {
                    return 2;
                }
                return 0;
            } else if (c1 == 1 && c2 == 1 && c3 == 0) {
                if (op == Operations.Read
                        || op == Operations.DecTransRest) {
                    return (isKeyBReadable) ? 1 : 3;
                }
                return 2;
            } else if (c1 == 0 && c2 == 0 && c3 == 1) {
                if (op == Operations.Read
                        || op == Operations.DecTransRest) {
                    return (isKeyBReadable) ? 1 : 3;
                }
                return 0;
            } else if (c1 == 0 && c2 == 1 && c3 == 1) {
                if (op == Operations.Read || op == Operations.Write) {
                    return 2;
                }
                return 0;
            } else if (c1 == 1 && c2 == 0 && c3 == 1) {
                if (op == Operations.Read) {
                    return 2;
                }
                return 0;
            } else if (c1 == 1 && c2 == 1 && c3 == 1) {
                return 0;
            } else {
                return -1;
            }
        }
    }

    /**
     * Check if Key B is readable
     */
    public static boolean isKeyBReadable(byte c1, byte c2, byte c3) {
        return c1 == 0
                && ((c2 == 0 && c3 == 0)
                || (c2 == 1 && c3 == 0)
                || (c2 == 0 && c3 == 1));
    }

    /**
     * Convert the access condition bytes to
     * a matrix containing the resolved C1, C2 and C3 for each block
     */
    public static byte[][] acBytesToACMatrix(byte acBytes[]) {
        // ACs correct or not?
        byte[][] acMatrix = new byte[3][4];

        if (acBytes.length > 2
                && (byte)((acBytes[1] >>> 4) & 0x0F) == (byte)((acBytes[0]^0xFF) & 0x0F)
                && (byte)(acBytes[2] & 0x0F) == (byte)(((acBytes[0]^0xFF) >>> 4) & 0x0F)
                && (byte)((acBytes[2] >>> 4) & 0x0F) == (byte)((acBytes[1]^0xFF) & 0x0F)) {
            // C1
            for (int i = 0; i < 4; i++) {
                acMatrix[0][i] = (byte)((acBytes[1] >>> 4 + i) & 0x01);
            }
            // C2
            for (int i = 0; i < 4; i++) {
                acMatrix[1][i] = (byte)((acBytes[2] >>> i) & 0x01);
            }
            // C3
            for (int i = 0; i < 4; i++) {
                acMatrix[2][i] = (byte)((acBytes[2] >>> 4 + i) & 0x01);
            }
            return acMatrix;
        }

        return null;
    }

    /**
     * Convert a matrix with access conditions bits into normal bytes
     */
    public static byte[] acMatrixToACBytes(byte acMatrix[][]) {
        if (acMatrix != null && acMatrix.length == 3) {
            for (int i = 0; i < 3; i++) {
                if (acMatrix[i].length != 4)
                    return null;
            }
        } else {
            return null;
        }

        byte[] acBytes = new byte[3];

        // Byte 6, Bit 0-3
        acBytes[0] = (byte)((acMatrix[0][0]^0xFF) & 0x01);
        acBytes[0] |= (byte)(((acMatrix[0][1]^0xFF) << 1) & 0x02);
        acBytes[0] |= (byte)(((acMatrix[0][2]^0xFF) << 2) & 0x04);
        acBytes[0] |= (byte)(((acMatrix[0][3]^0xFF) << 3) & 0x08);
        // Byte 6, Bit 4-7
        acBytes[0] |= (byte)(((acMatrix[1][0]^0xFF) << 4) & 0x10);
        acBytes[0] |= (byte)(((acMatrix[1][1]^0xFF) << 5) & 0x20);
        acBytes[0] |= (byte)(((acMatrix[1][2]^0xFF) << 6) & 0x40);
        acBytes[0] |= (byte)(((acMatrix[1][3]^0xFF) << 7) & 0x80);

        // Byte 7, Bit 0-3
        acBytes[1] = (byte)((acMatrix[2][0]^0xFF) & 0x01);
        acBytes[1] |= (byte)(((acMatrix[2][1]^0xFF) << 1) & 0x02);
        acBytes[1] |= (byte)(((acMatrix[2][2]^0xFF) << 2) & 0x04);
        acBytes[1] |= (byte)(((acMatrix[2][3]^0xFF) << 3) & 0x08);
        // Byte 7, Bit 4-7
        acBytes[1] |= (byte)((acMatrix[0][0] << 4) & 0x10);
        acBytes[1] |= (byte)((acMatrix[0][1] << 5) & 0x20);
        acBytes[1] |= (byte)((acMatrix[0][2] << 6) & 0x40);
        acBytes[1] |= (byte)((acMatrix[0][3] << 7) & 0x80);

        // Byte 8, Bit 0-3
        acBytes[2] = (byte)(acMatrix[1][0] & 0x01);
        acBytes[2] |= (byte)((acMatrix[1][1] << 1) & 0x02);
        acBytes[2] |= (byte)((acMatrix[1][2] << 2) & 0x04);
        acBytes[2] |= (byte)((acMatrix[1][3] << 3) & 0x08);
        // Byte 8, Bit 4-7
        acBytes[2] |= (byte)((acMatrix[2][0] << 4) & 0x10);
        acBytes[2] |= (byte)((acMatrix[2][1] << 5) & 0x20);
        acBytes[2] |= (byte)((acMatrix[2][2] << 6) & 0x40);
        acBytes[2] |= (byte)((acMatrix[2][3] << 7) & 0x80);

        return acBytes;
    }

    /**
     * Check if a given hex string is pure hex and 16-byte long
     */
    public static boolean isHexAnd16Byte(String hexString, Context context) {
        if (!hexString.matches("[0-9A-Fa-f]+")) {
            // Not hex
            Toast.makeText(context, R.string.info_not_hex_data, Toast.LENGTH_LONG).show();
            return false;
        }

        if (hexString.length() != 32) {
            // Not 16-byte
            Toast.makeText(context, R.string.info_not_16_byte, Toast.LENGTH_LONG).show();
            return false;
        }

        return true;
    }

    /**
     * Check if the given block is a value block
     */
    public static boolean isValueBlock(String hexString) {
        byte[] b = Common.hexStringToByteArray(hexString);

        if (b.length == 16) {
            if ((b[0] == b[8] && (byte)(b[0]^0xFF) == b[4])
                    && (b[1] == b[9] && (byte)(b[1]^0xFF) == b[5])
                    && (b[2] == b[10] && (byte)(b[2]^0xFF) == b[6])
                    && (b[3] == b[11] && (byte)(b[3]^0xFF) == b[7])
                    && (b[12] == b[14] && b[13] == b[15]
                    && (byte)(b[12]^0xFF) == b[13])) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if all blocks contain valid data
     */
    public static int isValidDump(String[] lines, boolean ignoreAsterisk) {
        ArrayList<Integer> knownSectors = new ArrayList<>();
        int blocksSinceLastSectorHeader = 4;
        boolean is16BlockSector = false;

        if (lines == null || lines.length == 0) {
            return 6;
        }

        for (String line : lines) {
            if ((!is16BlockSector && blocksSinceLastSectorHeader == 4)
                    || (is16BlockSector && blocksSinceLastSectorHeader == 16)) {
                // A sector header is expected
                if (!line.matches("^\\+Sector: [0-9]{1,2}$")) {
                    // Not a valid sector length or not a valid sector header
                    return 1;
                }

                int sector;
                try {
                    sector = Integer.parseInt(line.split(": ")[1]);
                } catch (Exception ex) {
                    return 1;
                }

                if (sector < 0 || sector > 39) {
                    // Sector out of range
                    return 4;
                }
                if (knownSectors.contains(sector)) {
                    return 5;
                }

                knownSectors.add(sector);
                is16BlockSector = (sector >= 32);
                blocksSinceLastSectorHeader = 0;
                continue;
            }

            if (line.startsWith("*") && ignoreAsterisk) {
                // Ignore line and move to the next sector
                is16BlockSector = false;
                blocksSinceLastSectorHeader = 4;
                continue;
            }

            if (!line.matches("[0-9A-Fa-f-]+")) {
                // Not pure hex or NO_DATA
                return 2;
            }
            if (line.length() != 32) {
                // Not 32-char per line
                return 3;
            }
            blocksSinceLastSectorHeader++;
        }

        return 0;
    }

    /**
     * Toast messages with ERROR information
     *
     * @see #isValidDump(String[], boolean)
     */
    public static void isValidDumpErrorToast(int errorCode, Context context) {
        switch (errorCode) {
        case 1:
            Toast.makeText(context, R.string.info_valid_dump_not_4_or_16_lines,
                    Toast.LENGTH_LONG).show();
            break;
        case 2:
            Toast.makeText(context, R.string.info_valid_dump_not_hex,
                    Toast.LENGTH_LONG).show();
            break;
        case 3:
            Toast.makeText(context, R.string.info_valid_dump_not_16_bytes,
                    Toast.LENGTH_LONG).show();
            break;
        case 4:
            Toast.makeText(context, R.string.info_valid_dump_sector_range,
                    Toast.LENGTH_LONG).show();
            break;
        case 5:
            Toast.makeText(context, R.string.info_valid_dump_double_sector,
                    Toast.LENGTH_LONG).show();
            break;
        case 6:
            Toast.makeText(context, R.string.info_valid_dump_empty_dump,
                    Toast.LENGTH_LONG).show();
            break;
        }
    }

    /**
     * Reverse a byte array
     */
    public static void reverseByteArrayInPlace(byte[] array) {
        for(int i = 0; i < array.length / 2; i++) {
            byte temp = array[i];
            array[i] = array[array.length - i - 1];
            array[array.length - i - 1] = temp;
        }
    }


    /**
     * Convert a byte array to a string
     */
    public static String byte2String(byte[] bytes, int fmt) {
        switch(fmt) {
            case 1:
                return hex2Dec(byte2HexString(bytes));
            case 2:
                byte[] revBytes = bytes.clone();
                reverseByteArrayInPlace(revBytes);
                return hex2Dec(byte2HexString(revBytes));
        }

        return byte2HexString(bytes);
    }

    /**
     * Convert a hex string to a decimal string
     */
    public static String hex2Dec(String hexString) {
        String ret;

        if (hexString == null || hexString.isEmpty()) {
            ret = "0";
        } else if (hexString.length() <= 14) {
            ret = Long.toString(Long.parseLong(hexString, 16));
        } else {
            BigInteger bigInteger = new BigInteger(hexString , 16);
            ret = bigInteger.toString();
        }

        return ret;
    }

    /**
     * Convert an array of bytes into a string of hex values
     */
    public static String byte2HexString(byte[] bytes) {
        StringBuilder ret = new StringBuilder();

        if (bytes != null) {
            for (Byte b : bytes) {
                ret.append(String.format("%02X", b.intValue() & 0xFF));
            }
        }

        return ret.toString();
    }

    /**
     * Convert a string of hex data into a byte array
     */
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];

        try {
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                     + Character.digit(s.charAt(i + 1), 16));
            }
        } catch (Exception e) {
            Log.d(LOG_TAG, "Argument(s) given should be a HEX string");
        }

        return data;
    }

    /**
     * Create a colored string.
     */
    public static SpannableString colorString(String data, int color) {
        SpannableString ret = new SpannableString(data);
        ret.setSpan(new ForegroundColorSpan(color), 0, data.length(), 0);
        return ret;
    }

    /**
     * Copy a text to the Android clipboard
     */
    public static void copyToClipboard(String text, Context context, boolean showMsg) {
        if (!text.equals("")) {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) context.getSystemService(
                            Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip =
                    android.content.ClipData.newPlainText("MIFARE classic tool data", text);
            clipboard.setPrimaryClip(clip);

            if (showMsg) {
                Toast.makeText(context, R.string.info_copied_to_clipboard,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Get the content of the Android clipboard
     */
    public static String getFromClipboard(Context context) {
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) context.getSystemService(
                        Context.CLIPBOARD_SERVICE);

        if (clipboard.getPrimaryClip() != null
                && clipboard.getPrimaryClip().getItemCount() > 0
                && clipboard.getPrimaryClipDescription().hasMimeType(
                    android.content.ClipDescription.MIMETYPE_TEXT_PLAIN)
                && clipboard.getPrimaryClip().getItemAt(0) != null
                && clipboard.getPrimaryClip().getItemAt(0).getText() != null) {
            return clipboard.getPrimaryClip().getItemAt(0).getText().toString();
        }

        return null;
    }

    /**
     * Share a file from the "tmp" directory
     *
     * @see #TMP_DIR
     */
    public static void shareTmpFile(Context context, File file) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Uri uri;

        try {
            uri = FileProvider.getUriForFile(
                    context, context.getPackageName() + ".fileprovider", file);
        } catch (IllegalArgumentException ex) {
            Toast.makeText(context, R.string.info_share_error, Toast.LENGTH_SHORT).show();
            return;
        }

        intent.setDataAndType(uri, "text/plain");
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        context.startActivity(Intent.createChooser(
                intent, context.getText(R.string.dialog_share_title)));
    }

    /**
     * Copy file
     */
    public static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;

        while((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    /**
     * Convert dips to pixels
     */
    public static int dpToPx(int dp) {
        return (int) (dp * mScale + 0.5f);
    }

    /**
     * Calculate the BCC of a 4 byte UID
     */
    public static byte calcBCC(byte[] uid) throws IllegalArgumentException {
        if (uid.length != 4) {
            throw new IllegalArgumentException("UID length is NOT 4 bytes :(");
        }

        byte bcc = uid[0];
        for(int i = 1; i < uid.length; i++) {
            bcc = (byte)(bcc ^ uid[i]);
        }
        return bcc;
    }

    public static Tag getTag() {
        return mTag;
    }

    public static void setTag(Tag tag) {
        mTag = tag;
        mUID = tag.getId();
    }

    public static NfcAdapter getNfcAdapter() {
        return mNfcAdapter;
    }

    public static void setNfcAdapter(NfcAdapter nfcAdapter) {
        mNfcAdapter = nfcAdapter;
    }

    public static void setUseAsEditorOnly(boolean value) {
        mUseAsEditorOnly = value;
    }

    public static SparseArray<byte[][]> getKeyMap() {
        return mKeyMap;
    }

    public static void setKeyMapRange (int from, int to){
        mKeyMapFrom = from;
        mKeyMapTo = to;
    }

    public static int getKeyMapRangeFrom() {
        return mKeyMapFrom;
    }

    public static int getKeyMapRangeTo() {
        return mKeyMapTo;
    }

    public static void setKeyMap(SparseArray<byte[][]> value) {
        mKeyMap = value;
    }

    public static void setPendingComponentName(ComponentName pendingActivity) {
        mPendingComponentName = pendingActivity;
    }

    public static ComponentName getPendingComponentName() {
        return mPendingComponentName;
    }

    public static byte[] getUID() {
        return mUID;
    }

    public static boolean isValidBCC(byte[] uid, byte bcc) {
        return calcBCC(uid) == bcc;
    }

    public static String getVersionCode() {
        return mVersionCode;
    }

    public static boolean useAsEditorOnly() {
        return mUseAsEditorOnly;
    }
}
