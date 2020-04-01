package nl.politie.predev.android.zakboek;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class NoteDetailsActivity extends AppCompatActivity {

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_note_details);

		//Spinner populeren
		Spinner spinner = findViewById(R.id.activity_note_details_spinner);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.authorizations, android.R.layout.simple_spinner_item);

		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);

		if (getIntent().getStringExtra(NoteActivity.EXTRA_MESSAGE_NOTE_DETAILS) != null) {
			Note n = null;
			ObjectMapper om = new ObjectMapper();
			try {
				n = om.readValue(getIntent().getStringExtra(NoteActivity.EXTRA_MESSAGE_NOTE_DETAILS), Note.class);
			} catch (IOException e) {
				e.printStackTrace();
			}
			//TODO niet volledige hardcoded meuk
			if(n.getAutorisatieniveau() == 3) {
				spinner.setSelection(1);
			}else {
				spinner.setSelection(0);
			}

			TextView createdBy = findViewById(R.id.activity_note_details_created_by);
			TextView createdAt = findViewById(R.id.activity_note_details_created_at);
			TextView owner = findViewById(R.id.activity_note_details_owner);
			Switch isPrivate = findViewById(R.id.activity_note_details_private);

			createdBy.setText(n.getCreated_by());
			createdAt.setText(n.getGenerated_at().toString());
			owner.setText(n.getOwner());
			isPrivate.setChecked(n.isIs_public());

		}

		ImageButton ibSave = findViewById(R.id.activity_note_details_ib_save);
		ibSave.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				okAndReturn();
			}
		});


	}

	private void okAndReturn() {

		Switch switchButton = findViewById(R.id.activity_note_details_private);
		Spinner spinner = findViewById(R.id.activity_note_details_spinner);

		Note n = new Note();

		n.setIs_public(switchButton.isChecked());

		int spinnerPosition = spinner.getSelectedItemPosition();

		//TODO hard coded meuk
		switch (spinnerPosition){
			case 0:
				n.setGrondslag(8.0);
				n.setAutorisatieniveau(1);
				n.setAfhandelcode(11);
				break;
			case 1:
				n.setGrondslag(8.0);
				n.setAutorisatieniveau(3);
				n.setAfhandelcode(11);
				break;
			default:
				n.setGrondslag(8.0);
				n.setAutorisatieniveau(1);
				n.setAfhandelcode(11);
				break;
		}

		String json ="";
		ObjectMapper om = new ObjectMapper();

		try {
			json = om.writeValueAsString(n);
		} catch (IOException e) {
			e.printStackTrace();
		}

		Intent returnIntent = new Intent();
		returnIntent.putExtra("result", json);
		setResult(Activity.RESULT_OK, returnIntent);
		finish();

	}
}
