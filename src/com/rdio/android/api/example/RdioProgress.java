package com.rdio.android.api.example;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;

public class RdioProgress extends DialogFragment {

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		LayoutInflater inflater = getActivity().getLayoutInflater();
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

		String message = getArguments().getString("message");
		builder.setMessage(message);
		builder.setView(inflater.inflate(R.layout.dialog_progress, null));

		return builder.create();
	}
}
