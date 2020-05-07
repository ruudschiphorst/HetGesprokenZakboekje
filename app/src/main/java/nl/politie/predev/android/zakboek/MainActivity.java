package nl.politie.predev.android.zakboek;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import nl.politie.predev.android.zakboek.model.AccesTokenRequest;
import nl.politie.predev.android.zakboek.model.Note;
import nl.politie.predev.android.zakboek.model.NoteIdentifier;
import okhttp3.Call;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

	private RecyclerView recyclerView;
	private MainRecyclerViewAdapter adapter;
	private RecyclerView.LayoutManager layoutManager;
	List<Note> data = new ArrayList<Note>();
	private ScheduledExecutorService tokenRefresher;
	public static final String EXTRA_MESSAGE = "ZAKBOEKJE_NOTE";
	public static final int NOTE_ACTIVITY_RESULT = 1;
	private boolean deleteMode = false;
	private FloatingActionButton fabDeleteMode;
	private SharedPreferences settings;
	private static final int REQUEST_CODE = 200;
	private int selectedFilter = 0;
	private HttpRequestHelper httpRequestHelper;

	public interface RecyclerViewClickListener {
		void onItemClicked(UUID uuid);

		boolean onItemLongClicked(UUID uuid, String title, Integer version);

	}


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_loading);
		requestPermissionsIfNotGranted();

		settings = getSharedPreferences(PreferencesActivity.PREFS_ZAKBOEKJE, 0);
		httpRequestHelper = new HttpRequestHelper(settings, this);
	}

	private void initViews() {

		setContentView(R.layout.activity_main);

		recyclerView = findViewById(R.id.activity__main_recycler);
		recyclerView.setHasFixedSize(true);

		layoutManager = new LinearLayoutManager(this);
		((LinearLayoutManager) layoutManager).setOrientation(RecyclerView.VERTICAL);
		recyclerView.setLayoutManager(layoutManager);

		adapter = new MainRecyclerViewAdapter(new ArrayList<Note>(), getRecyclerViewClickListener());
		recyclerView.setAdapter(adapter);

		FloatingActionButton fabAdd = findViewById(R.id.activity_main_add);
		fabAdd.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				openNoteActivity(null);
			}
		});

		FloatingActionButton fabSettings = findViewById(R.id.activity_main_password);
		fabSettings.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent i = new Intent(view.getContext(), PreferencesActivity.class);
				startActivity(i);
			}
		});

		fabDeleteMode = findViewById(R.id.activity_main_deletemode);

		setDeleteModeVisuals();

		fabDeleteMode.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Handler mainHandler = new Handler(getBaseContext().getMainLooper());
				deleteMode = !deleteMode;
				setDeleteModeVisuals();
				if (deleteMode) {
					Runnable runnable = new Runnable() {
						@Override
						public void run() {
							Toast.makeText(getBaseContext(), "Delete modus AAN. Tik lang op een notitie om deze te verwijderen.", Toast.LENGTH_LONG).show();
						}
					};
					mainHandler.post(runnable);
				} else {
					Runnable runnable = new Runnable() {
						@Override
						public void run() {
							Toast.makeText(getBaseContext(), "Delete modus UIT.", Toast.LENGTH_LONG).show();
						}
					};
					mainHandler.post(runnable);
				}
			}
		});

		Spinner spinner = findViewById(R.id.activity_main_filters_spinner);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.filters, android.R.layout.simple_spinner_item);

		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);

		spinner.setSelection(selectedFilter);

		spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
				if (i != selectedFilter) {
					selectedFilter = i;
					getNotesFromServer();
				}

			}

			@Override
			public void onNothingSelected(AdapterView<?> adapterView) {

			}
		});

		ImageButton ib = findViewById(R.id.activity_main_search);
		ib.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Spinner spinner = findViewById(R.id.activity_main_filters_spinner);
				selectedFilter = spinner.getSelectedItemPosition();
				setContentView(R.layout.activity_loading);
				getNotesFromServer();
			}
		});

	}

	private void setDeleteModeVisuals() {
		if (deleteMode) {
			fabDeleteMode.setImageDrawable(getDrawable(android.R.drawable.ic_delete));
		} else {
			fabDeleteMode.setImageDrawable(getDrawable(android.R.drawable.ic_menu_delete));
		}
	}

	private RecyclerViewClickListener getRecyclerViewClickListener() {

		RecyclerViewClickListener retval = new RecyclerViewClickListener() {
			@Override
			public void onItemClicked(UUID uuid) {
				openNoteActivity(uuid.toString());
			}

			@Override
			public boolean onItemLongClicked(UUID uuid, String title, Integer version) {
				if (deleteMode) {
					deleteNote(uuid, title);
					return true;
				} else {
					return false;
				}
			}
		};

		return retval;
	}

	private void deleteNote(final UUID uuid, String title) {

		new AlertDialog.Builder(this)
				.setTitle("Notitie verwijderen")
				.setMessage("Weet u zeker dat u de notitie \"" + title + "\" wilt verwijderen?")
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

					public void onClick(DialogInterface dialog, int whichButton) {
						sendDeleteRequestToServer(uuid);
					}
				})
				.setNegativeButton(android.R.string.no, null).show();
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (tokenRefresher == null || tokenRefresher.isShutdown() || AccesTokenRequest.shouldRefresh()) {
			tokenRefresher = getTokenRefresher();
		}
		getNotesFromServer();
	}

	private void openNoteActivity(String note) {

		Intent intent = new Intent(this, NoteActivity.class);
		intent.putExtra(EXTRA_MESSAGE, note);
		startActivityForResult(intent, NOTE_ACTIVITY_RESULT);

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == NOTE_ACTIVITY_RESULT) {
			if (resultCode == Activity.RESULT_OK) {
				setContentView(R.layout.activity_loading);
				getNotesFromServer();
			} else {

			}
		}
	}

	private void sendDeleteRequestToServer(UUID uuid) {

		setContentView(R.layout.activity_loading);

		NoteIdentifier noteIdentifier = new NoteIdentifier();
		noteIdentifier.setNoteID(uuid);

		ObjectMapper om = new ObjectMapper();
		String json = null;
		try {
			json = om.writeValueAsString(noteIdentifier);
		} catch (IOException e) {
			e.printStackTrace();
		}

		HttpRequestHelper.HttpRequestFinishedListener listener = new HttpRequestHelper.HttpRequestFinishedListener(){

			@Override
			public void onResponse(Call call, Response response) {
				getNotesFromServer();
			}

			@Override
			public void onFailure(Call call, IOException e) {

			}

			@Override
			public void onError(String message) {

			}
		};

		httpRequestHelper.deleteNote(json,listener);

	}

	private void getNotesFromServer() {

		String endpoint;

		//TODO hard coded meuk
		switch (selectedFilter) {
			case 0:
				endpoint = "getmynotes";
				break;
			case 1:
				endpoint = "getall";
				break;
			case 2:
				endpoint = "getpublicnotes";
				break;
			case 3:
				endpoint = "getmypublicnotes";
				break;
			default:
				endpoint = "getall";
				break;
		}

		HttpRequestHelper.HttpRequestFinishedListener listener = new HttpRequestHelper.HttpRequestFinishedListener() {
			@Override
			public void onResponse(Call call, Response response) {
				try{
					String resp = response.body().string();
					ObjectMapper om = new ObjectMapper();
					data = Arrays.asList(om.readValue(resp, Note[].class));
					Collections.sort(data);
				}catch (Exception e) {
					final Context context = getBaseContext();
					final Exception ex = e;
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(context, ex.getMessage(), Toast.LENGTH_LONG);
						}
					});
				}
				//uitvoeren op main thread
				Handler mainHandler = new Handler(getBaseContext().getMainLooper());

				Runnable runnable = new Runnable() {
					@Override
					public void run() {
						initViews();
						adapter.updateData(data);

					}
				};
				mainHandler.post(runnable);
			}

			@Override
			public void onFailure(Call call, IOException e) {
				final Context context = getBaseContext();
				final IOException ex = e;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(context, ex.getMessage(), Toast.LENGTH_LONG);
					}
				});
			}

			@Override
			public void onError(String message) {
				final Context context = getBaseContext();
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(context, "Er is een probleem bij het inloggen. Probeer het later nogmaals.", Toast.LENGTH_LONG);
					}
				});
			}
		};
		httpRequestHelper.getNotesFromServer(endpoint, listener);
	}

	private ScheduledExecutorService getTokenRefresher() {

		String username = settings.getString(PreferencesActivity.PREFS_USERNAME, "");
		String password = settings.getString(PreferencesActivity.PREFS_PASS, "");

		//Geen username + pass = prompt gebruiker
		if (username.equalsIgnoreCase("") || password.equalsIgnoreCase("")) {
			Intent intent = new Intent(getApplicationContext(), PreferencesActivity.class);
			startActivity(intent);
		} else {

			ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

			scheduler.scheduleAtFixedRate(new Runnable() {
				public void run() {
					httpRequestHelper.setToken();
				}
			}, 0, 10, TimeUnit.MINUTES);

			return scheduler;
		}
		return null;
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		tokenRefresher.shutdown();
		tokenRefresher = null;
		AccesTokenRequest.accesTokenRequest = null;
		AccesTokenRequest.requested_at = null;
	}

	private void requestPermissionsIfNotGranted() {

		if ((ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) ||
				ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE);
		}
	}

}
