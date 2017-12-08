package de.cineaste.android.fragment;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import de.cineaste.android.R;

public class UserInputFragment extends DialogFragment
		implements TextView.OnEditorActionListener, View.OnClickListener {

	private EditText editText;

	public interface UserNameListener {
		void onFinishUserDialog(String userName);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {

		View view = inflater.inflate(R.layout.fragment_user_input, container);
		Button okBtn = view.findViewById(R.id.ok_tv);
		editText = view.findViewById(R.id.username_et);

		okBtn.setOnClickListener(this);
		editText.setOnEditorActionListener(this);
		editText.requestFocus();
		if (getDialog().getWindow() == null) {
			return view;
		}
		getDialog().getWindow()
				.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
		getDialog().setTitle(R.string.enter_username);
		getDialog().setCancelable(false);

		return view;
	}

	@Override
	public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		getNameAndDismiss();

		return true;
	}

	@Override
	public void onClick(View v) {
		getNameAndDismiss();
	}

	private void getNameAndDismiss() {
		String input = editText.getText().toString().trim();

		if (!input.isEmpty()) {
			UserNameListener activity = (UserNameListener) getActivity();
			if (activity == null) {
				return;
			}
			activity.onFinishUserDialog(input);
			this.dismiss();
		}
	}
}
