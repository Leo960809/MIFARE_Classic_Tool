package de.syss.MifareClassicTool;

import android.content.Context;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.MifareClassic;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

import de.syss.MifareClassicTool.Activities.Preferences.Preference;
import de.syss.MifareClassicTool.Common.Operations;

/**
 * Functions to read, write or analyze MIFARE Classic tags
 * @author Jinhao Jiang
 */
public class MCReader {

    private static final String LOG_TAG = MCReader.class.getSimpleName();


    // Placeholder for missing keys or unreadable blocks
    public static final String NO_KEY = "------------";
    public static final String NO_DATA = "--------------------------------";

    private final MifareClassic mMFC;
    private SparseArray<byte[][]> mKeyMap = new SparseArray<>();
    private int mKeyMapStatus = 0;
    private int mLastSector = -1;
    private int mFirstSector = 0;
    private ArrayList<byte[]> mKeysWithOrder;

    // Initialization
    private MCReader(Tag tag) {
        MifareClassic tmpMFC;

        try {
            tmpMFC = MifareClassic.get(tag);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to create a MIFARE Classic reader for the given tag :(");
            throw e;
        }

        mMFC = tmpMFC;
    }

    /**
     * Get new instance of {@link MCReader}.
     */
    public static MCReader get(Tag tag) {
        MCReader mcr = null;
        if (tag != null) {
            try {
                mcr = new MCReader(tag);
                if (!mcr.isMifareClassic()) {
                    return null;
                }
            } catch (RuntimeException ex) {
                // Should never happen
                return null;
            }
        }
        return mcr;
    }

    /**
     * Read as much as possible from the tag with the given key.
     */
    public SparseArray<String[]> readAsMuchAsPossible(SparseArray<byte[][]> keyMap) {
        SparseArray<String[]> resultSparseArray;

        if (keyMap != null && keyMap.size() > 0) {
            resultSparseArray = new SparseArray<>(keyMap.size());

            for (int i = 0; i < keyMap.size(); i++) {
                String[][] results = new String[2][];
                try {
                    if (keyMap.valueAt(i)[0] != null) {
                        // Read with key A
                        results[0] = readSector(
                                keyMap.keyAt(i), keyMap.valueAt(i)[0], false);
                    }
                    if (keyMap.valueAt(i)[1] != null) {
                        // Read with key B
                        results[1] = readSector(
                                keyMap.keyAt(i), keyMap.valueAt(i)[1], true);
                    }
                } catch (TagLostException e) {
                    return null;
                }
                // Merge results
                if (results[0] != null || results[1] != null) {
                    resultSparseArray.put(keyMap.keyAt(i), mergeSectorData(results[0], results[1]));
                }
            }
            return resultSparseArray;
        }
        return null;
    }

    /**
     * Read as much as possible from the tag
     */
    public SparseArray<String[]> readAsMuchAsPossible() {
        mKeyMapStatus = getSectorCount();
        while (buildNextKeyMapPart() < getSectorCount() - 1);
        return readAsMuchAsPossible(mKeyMap);
    }

    /**
     * Read as much as possible from a single sector with the given key
     */
    public String[] readSector(int sectorIndex, byte[] key, boolean useAsKeyB)
            throws TagLostException {
        // Authentication
        boolean auth = authenticate(sectorIndex, key, useAsKeyB);
        String[] ret = null;

        // Read sector
        if (auth) {
            // Read all blocks
            ArrayList<String> blocks = new ArrayList<>();
            int firstBlock = mMFC.sectorToBlock(sectorIndex);
            int lastBlock = firstBlock + 4;

            if (mMFC.getSize() == MifareClassic.SIZE_4K && sectorIndex > 31) {
                lastBlock = firstBlock + 16;
            }

            for (int i = firstBlock; i < lastBlock; i++) {
                try {
                    byte blockBytes[] = mMFC.readBlock(i);
                    if (blockBytes.length < 16) {
                        throw new IOException();
                    }
                    if (blockBytes.length > 16) {
                        blockBytes = Arrays.copyOf(blockBytes,16);
                    }

                    blocks.add(Common.byte2HexString(blockBytes));
                } catch (TagLostException e) {
                    throw e;
                } catch (IOException e) {
                    Log.d(LOG_TAG, "ERROR while reading Block " + i);
                    blocks.add(NO_DATA);

                    if (!mMFC.isConnected()) {
                        throw new TagLostException("Tag removed during readSector");
                    }
                    // Re-authentication
                    authenticate(sectorIndex, key, useAsKeyB);
                }
            }

            ret = blocks.toArray(new String[blocks.size()]);
            int last = ret.length -1;

            boolean noData = true;
            for (int i = 0; i < ret.length; i++) {
                if (!ret[i].equals(NO_DATA)) {
                    noData = false;
                    break;
                }
            }

            if (noData) {
                ret = null;
            } else {
                // Merge key in last block
                if (!useAsKeyB) {
                    if (isKeyBReadable(Common.hexStringToByteArray(ret[last].substring(12, 20)))) {
                        ret[last] = Common.byte2HexString(key)
                                + ret[last].substring(12, 32);
                    } else {
                        ret[last] = Common.byte2HexString(key)
                                + ret[last].substring(12, 20)
                                + NO_KEY;
                    }
                } else {
                    ret[last] = NO_KEY
                            + ret[last].substring(12, 20)
                            + Common.byte2HexString(key);
                }
            }
        }
        return ret;
    }

    /**
     * Write a block of 16 byte data into the given tag
     */
    public int writeBlock(int sectorIndex, int blockIndex,
                          byte[] data, byte[] key, boolean useAsKeyB) {
        if (getSectorCount() - 1 < sectorIndex) {
            return 1;
        }
        if (mMFC.getBlockCountInSector(sectorIndex) - 1 < blockIndex) {
            return 2;
        }
        if (data.length != 16) {
            return 3;
        }
        if (!authenticate(sectorIndex, key, useAsKeyB)) {
            return 4;
        }

        // Write block
        int block = mMFC.sectorToBlock(sectorIndex) + blockIndex;

        try {
            mMFC.writeBlock(block, data);
        } catch (IOException e) {
            Log.e(LOG_TAG, "ERROR while writing block to tag", e);
            return -1;
        }

        return 0;
    }

    /**
     * Increase or decrease a Value Block
     */
    public int writeValueBlock(int sectorIndex, int blockIndex, int value,
                               boolean increment, byte[] key, boolean useAsKeyB) {
        if (getSectorCount() - 1 < sectorIndex) {
            return 1;
        }
        if (mMFC.getBlockCountInSector(sectorIndex) - 1 < blockIndex) {
            return 2;
        }
        if (!authenticate(sectorIndex, key, useAsKeyB)) {
            return 3;
        }

        // Write Value Block
        int block = mMFC.sectorToBlock(sectorIndex) + blockIndex;

        try {
            if (increment) {
                mMFC.increment(block, value);
            } else {
                mMFC.decrement(block, value);
            }
            mMFC.transfer(block);
        } catch (IOException e) {
            Log.e(LOG_TAG, "ERROR while writing value block to tag", e);
            return -1;
        }

        return 0;
    }

    /**
     * Build key-value pairs in which keys represent the sector
     * and values are one or both of the MIFARE keys (A/B)
     */
    public int buildNextKeyMapPart() {
        boolean error = false;

        if (mKeysWithOrder != null && mLastSector != -1) {
            if (mKeyMapStatus == mLastSector+1) {
                mKeyMapStatus = mFirstSector;
                mKeyMap = new SparseArray<>();
            }

            // Re-authentication setting
            boolean retryAuth = Common.getPreferences().getBoolean(
                    Preference.UseRetryAuthentication.toString(), false);
            int retryAuthCount = Common.getPreferences().getInt(
                    Preference.RetryAuthenticationCount.toString(), 1);

            byte[][] keys = new byte[2][];
            boolean[] foundKeys = new boolean[] {false, false};
            boolean auth;

            // Check next sector against all keys
            keysloop:
            for (int i = 0; i < mKeysWithOrder.size(); i++) {
                byte[] key = mKeysWithOrder.get(i);

                for (int j = 0; j < retryAuthCount+1;) {
                    try {
                        if (!foundKeys[0]) {
                            // Authenticate with Key A
                            auth = mMFC.authenticateSectorWithKeyA(mKeyMapStatus, key);
                            if (auth) {
                                keys[0] = key;
                                foundKeys[0] = true;
                            }
                        }
                        if (!foundKeys[1]) {
                            // Authenticate with Key B
                            auth = mMFC.authenticateSectorWithKeyB(
                                    mKeyMapStatus, key);
                            if (auth) {
                                keys[1] = key;
                                foundKeys[1] = true;
                            }
                        }
                    } catch (Exception e) {
                        Log.d(LOG_TAG, "ERROR while building key map");
                    }

                    if((foundKeys[0] && foundKeys[1]) || !retryAuth) {
                        break;
                    }
                    j++;
                }

                if ((foundKeys[0] && foundKeys[1])) {
                    // Both keys found
                    break;
                }
            }

            if (!error && (foundKeys[0] || foundKeys[1])) {
                // At least one key found
                mKeyMap.put(mKeyMapStatus, keys);

                // Test with the all-F key
                byte[] fKey = Common.hexStringToByteArray("FFFFFFFFFFFF");

                if (mKeysWithOrder.size() > 2) {
                    if (foundKeys[0] && !Arrays.equals(keys[0], fKey)) {
                        mKeysWithOrder.remove(keys[0]);
                        mKeysWithOrder.add(1, keys[0]);
                    }
                    if (foundKeys[1] && !Arrays.equals(keys[1], fKey)) {
                        mKeysWithOrder.remove(keys[1]);
                        mKeysWithOrder.add(1, keys[1]);
                    }
                }
            }
            mKeyMapStatus++;
        } else {
            error = true;
        }

        if (error) {
            mKeyMapStatus = 0;
            mKeyMap = null;
            return -1;
        }

        return mKeyMapStatus - 1;
    }

    /**
     * Merge the result of two {@link #readSector(int, byte[], boolean)}
     * calls on the same sector
     */
    public String[] mergeSectorData(String[] firstResult, String[] secondResult) {
        String[] ret = null;

        if (firstResult != null || secondResult != null) {
            if ((firstResult != null && secondResult != null)
                    && firstResult.length != secondResult.length) {
                return null;
            }

            int length  = (firstResult != null) ? firstResult.length : secondResult.length;
            ArrayList<String> blocks = new ArrayList<>();

            // Merge data blocks
            for (int i = 0; i < length - 1 ; i++) {
                if (firstResult != null
                        && firstResult[i] != null
                        && !firstResult[i].equals(NO_DATA)) {
                    blocks.add(firstResult[i]);
                } else if (secondResult != null
                        && secondResult[i] != null
                        && !secondResult[i].equals(NO_DATA)) {
                    blocks.add(secondResult[i]);
                } else {
                    blocks.add(NO_DATA);
                }
            }

            ret = blocks.toArray(new String[blocks.size() + 1]);
            int last = length - 1;

            // Merge sector trailer
            if (firstResult != null
                    && firstResult[last] != null
                    && !firstResult[last].equals(NO_DATA)) {
                // Take first for sector trailer
                ret[last] = firstResult[last];
                if (secondResult != null
                        && secondResult[last] != null
                        && !secondResult[last].equals(NO_DATA)) {
                    // Merge key from the second result to sector trailer
                    ret[last] = ret[last].substring(0, 20) + secondResult[last].substring(20);
                }
            } else if (secondResult != null
                    && secondResult[last] != null
                    && !secondResult[last].equals(NO_DATA)) {
                // No first result, take second result as sector trailer
                ret[last] = secondResult[last];
            } else {
                ret[last] = NO_DATA;
            }
        }

        return ret;
    }

    /**
     * Check if the tag is writable with the given keys at the given positions
     */
    public HashMap<Integer, HashMap<Integer, Integer>> isWritableOnPositions(
            HashMap<Integer, int[]> pos, SparseArray<byte[][]> keyMap) {
        HashMap<Integer, HashMap<Integer, Integer>> ret = new HashMap<>();

        for (int i = 0; i < keyMap.size(); i++) {
            int sector = keyMap.keyAt(i);

            if (pos.containsKey(sector)) {
                byte[][] keys = keyMap.get(sector);
                byte[] ac;

                // Authentication
                if (keys[0] != null) {
                    if (!authenticate(sector, keys[0], false)) {
                        return null;
                    }
                } else if (keys[1] != null) {
                    if (!authenticate(sector, keys[1], true)) {
                        return null;
                    }
                } else {
                    return null;
                }

                // Read MIFARE access conditions
                int acBlock = mMFC.sectorToBlock(sector) + mMFC.getBlockCountInSector(sector) - 1;

                try {
                    ac = mMFC.readBlock(acBlock);
                } catch (Exception e) {
                    ret.put(sector, null);
                    continue;
                }

                // mMFC.readBlock(i) must return 16 bytes or throw an error
                if (ac.length < 16) {
                    ret.put(sector, null);
                    continue;
                }

                ac = Arrays.copyOfRange(ac, 6, 9);
                byte[][] acMatrix = Common.acBytesToACMatrix(ac);

                if (acMatrix == null) {
                    ret.put(sector, null);
                    continue;
                }
                boolean isKeyBReadable = Common.isKeyBReadable(
                        acMatrix[0][3],
                        acMatrix[1][3],
                        acMatrix[2][3]
                );

                // Check all non-empty blocks
                HashMap<Integer, Integer> blockWithWriteInfo = new HashMap<>();
                for (int block : pos.get(sector)) {
                    if ((block == 3 && sector <= 31) || (block == 15 && sector >= 32)) {
                        // Sector trailer
                        // Are the access bits writable?
                        int acValue = Common.getOperationInfoForBlock(
                                acMatrix[0][3],
                                acMatrix[1][3],
                                acMatrix[2][3],
                                Operations.WriteAC,
                                true, isKeyBReadable);

                        // Is Key A writable?
                        // If so, Key B will be writable with the same key.
                        int keyABValue = Common.getOperationInfoForBlock(
                                acMatrix[0][3],
                                acMatrix[1][3],
                                acMatrix[2][3],
                                Operations.WriteKeyA,
                                true, isKeyBReadable);

                        int result = keyABValue;

                        if (acValue == 0 && keyABValue != 0) {
                            // Write key found, but AC-bits are not writable
                            result += 3;
                        } else if (acValue == 2 && keyABValue == 0) {
                            // Access bits are writable with key B, but keys are not writable
                            result = 6;
                        }
                        blockWithWriteInfo.put(block, result);
                    } else {
                        int acBitsForBlock = block;

                        // Handle MIFARE Classic 4k tags
                        if (sector >= 32) {
                            if (block >= 0 && block <= 4) {
                                acBitsForBlock = 0;
                            } else if (block >= 5 && block <= 9) {
                                acBitsForBlock = 1;
                            } else if (block >= 10 && block <= 14) {
                                acBitsForBlock = 2;
                            }
                        }

                        blockWithWriteInfo.put(
                                block, Common.getOperationInfoForBlock(
                                        acMatrix[0][acBitsForBlock],
                                        acMatrix[1][acBitsForBlock],
                                        acMatrix[2][acBitsForBlock],
                                        Operations.Write,
                                        false, isKeyBReadable));
                    }

                }

                if (blockWithWriteInfo.size() > 0) {
                    ret.put(sector, blockWithWriteInfo);
                }
            }
        }

        return ret;
    }

    /**
     * Set the key files for {@link #buildNextKeyMapPart()}
     */
    public boolean setKeyFile(File[] keyFiles, Context context) {
        boolean hasAllZeroKey = false;
        HashSet<byte[]> keys = new HashSet<>();

        for (File file : keyFiles) {
            String[] lines = Common.readFileLineByLine(file, false, context);

            if (lines != null) {
                for (String line : lines) {
                    if (!line.equals("")
                            && line.length() == 12
                            && line.matches("[0-9A-Fa-f]+")) {
                        if (line.equals("000000000000")) {
                            hasAllZeroKey = true;
                        }

                        try {
                            keys.add(Common.hexStringToByteArray(line));
                        } catch (OutOfMemoryError e) {
                            // Too many keys, out of memory
                            Toast.makeText(context, R.string.info_too_many_keys,
                                    Toast.LENGTH_LONG).show();
                            return false;
                        }
                    }
                }
            }
        }

        if (keys.size() > 0) {
            mKeysWithOrder = new ArrayList<>(keys);
            byte[] zeroKey = Common.hexStringToByteArray("000000000000");

            if (hasAllZeroKey) {
                // Test with the all-F key
                byte[] fKey = Common.hexStringToByteArray("FFFFFFFFFFFF");

                mKeysWithOrder.remove(fKey);
                mKeysWithOrder.add(0, fKey);
            }
        }

        return true;
    }

    /**
     * Set the mapping range for {@link #buildNextKeyMapPart()}
     */
    public boolean setMappingRange(int firstSector, int lastSector) {
        if (firstSector >= 0
                && lastSector < getSectorCount()
                && firstSector <= lastSector) {
            mFirstSector = firstSector;
            mLastSector = lastSector;
            mKeyMapStatus = lastSector + 1;
            return true;
        }

        return false;
    }

    /**
     * Authenticate with the given sector of the tag
     */
    private boolean authenticate(int sectorIndex, byte[] key, boolean useAsKeyB) {
        // Re-uthentication setting
        boolean retryAuth = Common.getPreferences().getBoolean(
                Preference.UseRetryAuthentication.toString(), false);
        int retryCount = Common.getPreferences().getInt(
                Preference.RetryAuthenticationCount.toString(), 1);
        boolean ret = false;

        for (int i = 0; i < retryCount + 1; i++) {
            try {
                if (!useAsKeyB) {
                    // Key A
                    ret = mMFC.authenticateSectorWithKeyA(sectorIndex, key);
                } else {
                    // Key B
                    ret = mMFC.authenticateSectorWithKeyB(sectorIndex, key);
                }
            } catch (IOException e) {
                Log.d(LOG_TAG, "ERROR while authenticating with tag :(");
                return false;
            }

            if (ret || !retryAuth) {
                break;
            }
        }

        return ret;
    }

    /**
     * Check if key B is readable
     */
    private boolean isKeyBReadable(byte[] ac) {
        byte c1 = (byte) ((ac[1] & 0x80) >>> 7);
        byte c2 = (byte) ((ac[2] & 0x08) >>> 3);
        byte c3 = (byte) ((ac[2] & 0x80) >>> 7);

        return c1 == 0
                && (c2 == 0 && c3 == 0)
                || (c2 == 1 && c3 == 0)
                || (c2 == 0 && c3 == 1);
    }

    /**
     * Get the key map built from {@link #buildNextKeyMapPart()} with the given key file
     */
    public SparseArray<byte[][]> getKeyMap() {
        return mKeyMap;
    }

    public boolean isMifareClassic() {
        return mMFC != null;
    }

    /**
     * Return the sector count of the MIFARE Classic tag
     */
    public int getSectorCount() {
        boolean useCustomSectorCount = Common.getPreferences().getBoolean(
                Preference.UseCustomSectorCount.toString(), false);
        if (useCustomSectorCount) {
            return Common.getPreferences().getInt(
                    Preference.CustomSectorCount.toString(), 16);

        }
        return mMFC.getSectorCount();
    }

    /**
     * Check if the reader is connected to the tag
     */
    public boolean isConnected() {
        return mMFC.isConnected();
    }

    /**
     * Connect the reader to the tag
     */
    public void connect() throws Exception {
        final AtomicBoolean error = new AtomicBoolean(false);

        // Already connected?
        if (isConnected()) {
            return;
        }

        // Connect in a worker thread
        Thread t = new Thread(() -> {
            try {
                mMFC.connect();
            } catch (IOException | IllegalStateException ex) {
                error.set(true);
            }
        });
        t.start();

        // Wait for the connection
        try {
            t.join(500);
        } catch (InterruptedException ex) {
            error.set(true);
        }

        if (error.get()) {
            Log.d(LOG_TAG, "ERROR while connecting to tag :(");
            throw new Exception("ERROR while connecting to tag :(");
        }
    }

    /**
     * Close the connection between reader and the tag
     */
    public void close() {
        try {
            mMFC.close();
        } catch (IOException e) {
            Log.d(LOG_TAG, "ERROR while closing connection :(");
        }
    }
}
