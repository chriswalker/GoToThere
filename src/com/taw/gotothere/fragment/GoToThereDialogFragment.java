/**
 * 
 */
package com.taw.gotothere.fragment;

import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;

/**
 * Fragment wrapper for various dialogs displayed within the app.
 * @author chris
 */
public class GoToThereDialogFragment extends DialogFragment {
    /** Global field to contain the dialog. */
    private Dialog dialog;
    
    /**
     * Default constructor. Sets the dialog field to null.
     */
    public GoToThereDialogFragment() {
        super();
        dialog = null;
    }
    /**
     *  Set the dialog to display
     * @param dialog
     */
    public void setDialog(Dialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return dialog;
    }
}
