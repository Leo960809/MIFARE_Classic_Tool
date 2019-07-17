package de.syss.MifareClassicTool.Activities;

import android.app.Activity;
import android.content.Intent;

import de.syss.MifareClassicTool.Common;

/**
 * @author Jinhao Jiang
 */
public abstract class BasicActivity extends Activity {

    @Override
    public void onResume() {
        super.onResume();
        Common.setPendingComponentName(this.getComponentName());
        Common.enableNfcForegroundDispatch(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        Common.disableNfcForegroundDispatch(this);
    }

    @Override
    public void onNewIntent(Intent intent) {
        int typeCheck = Common.treatAsNewTag(intent, this);
        if (typeCheck == -1 || typeCheck == -2) {
            Intent i = new Intent(this, TagInfoTool.class);
            startActivity(i);
        }
    }
}
