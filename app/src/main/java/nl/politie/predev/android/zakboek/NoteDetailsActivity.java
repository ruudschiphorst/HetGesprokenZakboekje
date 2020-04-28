package nl.politie.predev.android.zakboek;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class NoteDetailsActivity extends AppCompatActivity {

	private RecyclerView recyclerView;
	private MainRecyclerViewAdapter recycleradapter;
	private RecyclerView.LayoutManager layoutManager;
	private SharedPreferences settings;
	private Note n;
	private List<Note> data = new ArrayList<Note>();

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		settings = getSharedPreferences(PreferencesActivity.PREFS_ZAKBOEKJE, 0);
		setContentView(R.layout.activity_note_details);

		//Spinner populeren
		Spinner spinner = findViewById(R.id.activity_note_details_spinner);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.authorizations, android.R.layout.simple_spinner_item);

		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);

		spinner.setVisibility(View.GONE);

		if (getIntent().getStringExtra(NoteActivity.EXTRA_MESSAGE_NOTE_DETAILS) != null) {
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
			if(n.getGenerated_at() !=null){
				createdAt.setText(n.getGenerated_at().toString());
			} else {
				createdAt.setText("(Nieuw)");
			}
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

		ibSave.setVisibility(View.GONE);

		recyclerView = findViewById(R.id.note_details_recycler);
		recyclerView.setHasFixedSize(true);

		layoutManager = new LinearLayoutManager(this);
		((LinearLayoutManager) layoutManager).setOrientation(RecyclerView.VERTICAL);
		recyclerView.setLayoutManager(layoutManager);

		recycleradapter = new MainRecyclerViewAdapter(new ArrayList<Note>(), getRecyclerViewClickListener());
		recyclerView.setAdapter(recycleradapter);

		getNotesFromServer();

	}

	private MainActivity.RecyclerViewClickListener getRecyclerViewClickListener() {

		MainActivity.RecyclerViewClickListener retval = new MainActivity.RecyclerViewClickListener() {
			@Override
			public void onItemClicked(UUID uuid) {
//				openNoteActivity(uuid.toString());
			}

			@Override
			public boolean onItemLongClicked(UUID uuid, String title, Integer version) {
				goToPreviousVersion(uuid, title, version);
				return true;
			}
		};

		return retval;
	}
	private void goToPreviousVersion(final UUID uuid, String title, final Integer version) {

		new AlertDialog.Builder(this)
				.setTitle("Terug naar vorige versie")
				.setMessage("Wilt terug gaan naar de tekst uit de notitie \"" + title + "\"?")
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

					public void onClick(DialogInterface dialog, int whichButton) {
						getPreviousVersion(uuid, version);
					}
				})
				.setNegativeButton(android.R.string.no, null).show();
	}

	private void getPreviousVersion(UUID uuid, Integer version){

		NoteIdentifier noteIdentifier = new NoteIdentifier();
		noteIdentifier.setNoteID(uuid);
		noteIdentifier.setVersion(version);

		ObjectMapper om = new ObjectMapper();
		String json = null;
		try {
			json = om.writeValueAsString(noteIdentifier);
		} catch (IOException e) {
			e.printStackTrace();
		}

		RequestBody body = RequestBody.create(
				MediaType.parse("application/json"), json);

		Request request = new Request.Builder()
				.url(settings.getString(PreferencesActivity.PREFS_URL_DB, PreferencesActivity.DEFAULT_BASE_HTTPS_URL_DB_API) + "getnotebyidandversion")
				.addHeader("Authorization", AccesTokenRequest.accesTokenRequest.getTokenType() + " " + AccesTokenRequest.accesTokenRequest.getAccessToken())
				.post(body)
				.build();

		OkHttpClient client = new OkHttpClient();  //getUnsafeOkHttpClient();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
//				Log.e("err", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				if (response.code() == 200) {
					String resp = response.body().string();
					ObjectMapper om = new ObjectMapper();
					final Note note = om.readValue(resp, Note.class);

					//uitvoeren op main thread
					Handler mainHandler = new Handler(getBaseContext().getMainLooper());

					Runnable runnable = new Runnable() {
						@Override
						public void run() {
							setPreviousTextAndReturn(note.getNote_text());

						}
					};
					mainHandler.post(runnable);
				} else {
					//TODO
//					Log.e("bla", response.body().string());
				}
			}
		});
	}

	private void setPreviousTextAndReturn(String text){
		n.setNote_text(text);
		okAndReturn();
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
		}
		return false;
	}

	@Override
	public void onBackPressed() {
		okAndReturn();
	}

	private void okAndReturn() {

		Switch switchButton = findViewById(R.id.activity_note_details_private);
		Spinner spinner = findViewById(R.id.activity_note_details_spinner);

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
	private void getNotesFromServer() {

		NoteIdentifier noteIdentifier = new NoteIdentifier();
		noteIdentifier.setNoteID(n.getNoteID());
		noteIdentifier.setVersion(n.getVersion());

		ObjectMapper om = new ObjectMapper();
		String json = null;
		try {
			json = om.writeValueAsString(noteIdentifier);
		} catch (IOException e) {
			e.printStackTrace();
		}

		RequestBody body = RequestBody.create(
				MediaType.parse("application/json"), json);

		Request request = new Request.Builder()
				.url(settings.getString(PreferencesActivity.PREFS_URL_DB, PreferencesActivity.DEFAULT_BASE_HTTPS_URL_DB_API) + "getallversionsofnote")
				.addHeader("Authorization", AccesTokenRequest.accesTokenRequest.getTokenType() + " " + AccesTokenRequest.accesTokenRequest.getAccessToken())
				.post(body)
				.build();

		OkHttpClient client = new OkHttpClient();  //getUnsafeOkHttpClient();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
//				Log.e("err", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				if (response.code() == 200) {
					String resp = response.body().string();
//					Log.e("bla","resp " + resp);
					ObjectMapper om = new ObjectMapper();
					data = Arrays.asList(om.readValue(resp, Note[].class));
					Collections.sort(data);
					//uitvoeren op main thread
					Handler mainHandler = new Handler(getBaseContext().getMainLooper());

					Runnable runnable = new Runnable() {
						@Override
						public void run() {
							recycleradapter.updateData(data);

						}
					};
					mainHandler.post(runnable);
				} else {
					//TODO
//					Log.e("bla", response.body().string());
				}
			}
		});
	}

}
