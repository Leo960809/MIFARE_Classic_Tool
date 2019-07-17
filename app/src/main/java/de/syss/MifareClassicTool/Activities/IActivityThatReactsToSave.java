package de.syss.MifareClassicTool.Activities;

/**
 * Interface with callback functions
 * @author Jinhao Jiang
 */
public interface IActivityThatReactsToSave {

    void onSaveSuccessful();

    void onSaveFailure();
}
