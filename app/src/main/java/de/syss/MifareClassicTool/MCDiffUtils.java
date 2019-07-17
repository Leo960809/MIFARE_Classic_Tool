package de.syss.MifareClassicTool;

import android.util.SparseArray;

import java.util.ArrayList;

/**
 * Functions to compare two dumps
 * @author Jinhao Jiang
 */
public class MCDiffUtils {

    /**
     * Compare two dumps and get a list of all different indices
     */
    public static SparseArray<Integer[][]> diffIndices(
            SparseArray<String[]> dump1, SparseArray<String[]> dump2) {
        SparseArray<Integer[][]> ret = new SparseArray<>();

        for (int i = 0; i < dump1.size(); i++) {
            String[] sector1 = dump1.valueAt(i);
            int sectorNr = dump1.keyAt(i);
            String[] sector2 = dump2.get(sectorNr);

            if (sector2 == null) {
                ret.put(sectorNr, new Integer[0][0]);
                continue;
            }

            Integer[][] diffSector = new Integer[sector1.length][];
            for (int j = 0; j < sector1.length; j++) {
                ArrayList<Integer> diffIndices = new ArrayList<>();

                for (int k = 0; k < sector1[j].length(); k++) {
                    if (sector1[j].charAt(k) != sector2[j].charAt(k)) {
                        diffIndices.add(k);
                    }
                }

                if (diffIndices.size() == 0) {
                    diffSector[j] = new Integer[0];
                } else {
                    diffSector[j] = diffIndices.toArray(new Integer[diffIndices.size()]);
                }
            }
            ret.put(sectorNr, diffSector);
        }

        // Are there any sector that only appear in dump2?
        for (int i = 0; i < dump2.size(); i++) {
            int sectorNr = dump2.keyAt(i);

            if (dump1.get(sectorNr) == null) {
                ret.put(sectorNr, new Integer[1][0]);
            }
        }

        return ret;
    }
}
