package nl.politie.predev.android.zakboek;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import nl.politie.predev.android.zakboek.model.AccesTokenRequest;
import nl.politie.predev.android.zakboek.model.Multimedia;
import nl.politie.predev.android.zakboek.model.Note;
import nl.politie.predev.android.zakboek.model.NoteIdentifier;
import okhttp3.Call;
import okhttp3.Response;

public class NoteActivity extends AppCompatActivity {

	public static final String EXTRA_MESSAGE_NOTE = "EXTRA_MESSAGE_NOTE";
	public static final String EXTRA_MESSAGE_NOTE_DETAILS = "EXTRA_MESSAGE_NOTE_DETAILS";
	private static final int CAMERA_REQUEST = 1888;
	private static final int NOTE_DETAILS_REQUEST = 1241;
	private boolean voiceInputActive = false;
	private Note n = null;
	private TextView title = null;
	private TextView textView = null;
	private TextView nonFinalText = null;
	private InsecureStempolRpcSpeechService speechService;
	private VoiceRecorder voiceRecorder;
	private List<Multimedia> noteMultimedia;// = new ArrayList<Multimedia>();
	private NoteRecyclerViewAdapter adapter;
	private RecyclerView recyclerView;
	private RecyclerView.LayoutManager layoutManager;
	private List<String> createdImages = new ArrayList<String>();
	private SharedPreferences settings;
	private String currentPhotoPath;
	private VisualizerView visualizerView;
	private boolean dirty;
	private HttpRequestHelper httpRequestHelper;

	public interface RecyclerViewClickListener {
		public void onItemClicked(String multimediaID);

	}

	private RecyclerViewClickListener getRecyclerViewClickListener() {
		RecyclerViewClickListener retval = new RecyclerViewClickListener() {
			@Override
			public void onItemClicked(String multimediaID) {
				openFoto(multimediaID);
			}
		};
		return retval;
	}

	private void openFoto(String multimediaID) {
		Intent intent = new Intent(this, PictureActivity.class);
		intent.putExtra(EXTRA_MESSAGE_NOTE, multimediaID);
		startActivity(intent);
	}

	private final VoiceRecorder.Callback voiceCallback = new VoiceRecorder.Callback() {

		@Override
		public void onVoiceStart() {
			if (speechService != null) {
				speechService.startRecognizing(voiceRecorder.getSampleRate());
			}
		}

		@Override
		public void onVoice(final byte[] data, final int size) {
			if (speechService != null) {
				speechService.recognize(data, size);
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						visualizerView.addAmplitude(getMaxAmplitude(data, size)); // update the VisualizeView
						visualizerView.invalidate(); // refresh the VisualizerView
					}
				});
			}
		}

		@Override
		public void onVoiceEnd() {
			if (speechService != null) {
				speechService.finishRecognizing();
			}
		}

	};

	private int getMaxAmplitude(byte[] data, int size) {

		int maxHeard = 0;

		for (int i = 0; i < size - 1; i += 2) {
			// The buffer has LINEAR16 in little endian.
			int s = data[i + 1];
			if (s < 0) s *= -1;
			s <<= 8;
			s += Math.abs(data[i]);
			if (s > maxHeard) {
				maxHeard = s;
			}
		}
		return maxHeard;
	}

	private final ServiceConnection serviceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName, IBinder binder) {
			speechService = InsecureStempolRpcSpeechService.from(binder);
			speechService.addListener(mSpeechServiceListener);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			speechService.finishRecognizing();
			speechService = null;
		}

	};

	private final InsecureStempolRpcSpeechService.InsecureRpcSpeechServiceListener mSpeechServiceListener =
			new InsecureStempolRpcSpeechService.InsecureRpcSpeechServiceListener() {
				@Override
				public void onStartListening() {

				}

				@Override
				public void onReadyForSpeech() {

				}

				@Override
				public void onSpeechStarted() {

				}

				@Override
				public void onSpeechRecognized(String text, boolean isFinal, boolean fromUpload) {

					//Unk kan overal in de tekst voorkomen. Haal dit er uit.
					text = text.replace("<unk>", "").trim();

					//Er worden automatisch spaties en punten geplot. Dit klopt niet altijd, zeker als er <unk>s in het resultaat zitten.
					//Sloop de "verkeerde" spaties er uit
					while (text.contains(" .")) {
						text = text.replace(" .", ".");
					}
					while (text.contains("  ")) {
						text = text.replace("  ", " ");
					}
					//Zorg er wel voor dat we niet de hele string kwijt zijn. Dan hoeft ie niets te doen.
					//Het kan voorkomen dat na trimmen en alle <unk>'s er uit halen, er alleen een punt overblijft. Dat willen we ook niet, dus <= 1
					if (text.length() <= 1) {
						return;
					}


					final String preparedText = text;
					if (isFinal) {

						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								String plottedText;
								String existingText = textView.getText().toString();
								if (existingText.endsWith(" ")) {
									plottedText = existingText + preparedText + " ";
								} else {
									plottedText = existingText + " " + preparedText + " ";
								}
								textView.setText(plottedText);
								nonFinalText.setText("");
							}
						});
					} else {
						//Tussenresultaten
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								nonFinalText.setText(preparedText);
							}
						});
					}
				}

				@Override
				public void onSpeechEnd() {

				}

				@Override
				public void onError(final String message) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(getBaseContext(), message, Toast.LENGTH_LONG).show();
						}
					});
				}

			};

	@Override
	protected void onStart() {
		super.onStart();
		bindService(new Intent(this, InsecureStempolRpcSpeechService.class), serviceConnection, BIND_AUTO_CREATE);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_loading);
		settings = getSharedPreferences(PreferencesActivity.PREFS_ZAKBOEKJE, 0);

		httpRequestHelper = new HttpRequestHelper(settings);

		if (getIntent().getStringExtra(MainActivity.EXTRA_MESSAGE) != null) {
			openNote(getIntent().getStringExtra(MainActivity.EXTRA_MESSAGE));
		} else {
			initViews();
			n = new Note();
			noteMultimedia = new ArrayList<Multimedia>();
			n.setMultimedia(new ArrayList<Multimedia>());
			title.setText("Nieuwe notitie");
		}
	}

	private void initViews() {

		setContentView(R.layout.activity_note);

		title = findViewById(R.id.note_tv_title);
		textView = findViewById(R.id.note_tv_text);
		nonFinalText = findViewById(R.id.note_tv_nonfinal_text);

		final ImageButton ibMic = findViewById(R.id.note_btn_mic);

		ibMic.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				voiceInputActive = !voiceInputActive;
				if (voiceInputActive) {
					ibMic.setImageDrawable(getDrawable(android.R.drawable.presence_audio_busy));
					startVoiceRecorder();
				} else {
					ibMic.setImageDrawable(getDrawable(android.R.drawable.presence_audio_online));
					stopVoiceRecorder();
				}
			}
		});

		final FloatingActionButton fabCamera = findViewById(R.id.note_fab_camera);
		fabCamera.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				captureCameraImage();
			}
		});

		recyclerView = (RecyclerView) findViewById(R.id.note_recycler_view);
		recyclerView.setHasFixedSize(true);

		layoutManager = new LinearLayoutManager(this);
		((LinearLayoutManager) layoutManager).setOrientation(RecyclerView.VERTICAL);
		recyclerView.setLayoutManager(layoutManager);

		adapter = new NoteRecyclerViewAdapter(noteMultimedia, getRecyclerViewClickListener());
		recyclerView.setAdapter(adapter);

		visualizerView = findViewById(R.id.visualizer);
		visualizerView.setBarColor(getColor(R.color.colorPrimary));
		visualizerView.setVisibility(View.GONE);

		nonFinalText.setVisibility(View.GONE);

		FloatingActionButton fabNoteDetails = findViewById(R.id.note_fab_details);
		fabNoteDetails.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				openNoteDetails();
			}
		});

		textView.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void afterTextChanged(Editable editable) {
				dirty = true;
			}
		});

	}

	private void openNoteDetails() {

		//Maak een kleine kopie van de huidige note met alleen dat wat we nodig hebben
		//Reden: ik wil geen enorm object met mogelijk vele MB's aan foto's over de lijn knallen.
		//Tevens: Android zelf vindt dat ook niet leuk

		dirty = true;

		Note noteToPass = new Note();
		noteToPass.setNoteID(n.getNoteID());
		noteToPass.setVersion(n.getVersion());
		noteToPass.setOwner(n.getOwner());
		noteToPass.setCreated_by(n.getCreated_by());
		noteToPass.setGenerated_at(n.getGenerated_at());
		noteToPass.setGrondslag(n.getGrondslag());
		noteToPass.setAutorisatieniveau(n.getAutorisatieniveau());
		noteToPass.setAfhandelcode(n.getAfhandelcode());
		noteToPass.setIs_public(n.isIs_public());

		ObjectMapper om = new ObjectMapper();
		String noteToPassAsString = "";
		try {
			noteToPassAsString = om.writeValueAsString(noteToPass);
		} catch (IOException e) {
			e.printStackTrace();
		}

		//"mini notitie" als string naar details passen
		Intent intent = new Intent(this, NoteDetailsActivity.class);
		intent.putExtra(EXTRA_MESSAGE_NOTE_DETAILS, noteToPassAsString);
		startActivityForResult(intent, NOTE_DETAILS_REQUEST);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {

			try {
				//Full res foto's worden altijd door Android weggeschreven als file, daar kun je niet omheen: anders krijg je alleen de thumbnail
				//Ik wil het als byte[] hebben, zodat ik het als base64 string in de JSON kan zetten
				byte[] imagedata = Files.readAllBytes(Paths.get(currentPhotoPath));
				createdImages.add(currentPhotoPath);

				Multimedia multimedia = new Multimedia();
				multimedia.setContent(Base64.getEncoder().encodeToString(imagedata));
				multimedia.setThumbnailContent(multimedia.getContent());
				multimedia.setLocalFilePath(currentPhotoPath);

				noteMultimedia.add(multimedia);
				adapter.updateData(noteMultimedia);
				return;
			} catch (Exception e) {
//				Log.e("Error", e.getMessage());
			}
		}

		if (requestCode == NOTE_DETAILS_REQUEST && resultCode == Activity.RESULT_OK) {
			ObjectMapper om = new ObjectMapper();
			String returnedNoteAsString = data.getStringExtra("result");
			try {
				//Waarden uit detail scherm overnemen
				Note returnedNote = om.readValue(returnedNoteAsString, Note.class);
				n.setIs_public(returnedNote.isIs_public());
				n.setGrondslag(returnedNote.getGrondslag());
				n.setAutorisatieniveau(returnedNote.getAutorisatieniveau());
				n.setAfhandelcode(returnedNote.getAfhandelcode());
				if (returnedNote.getNote_text() != null && !returnedNote.getNote_text().equals("")) {
					n.setNote_text(returnedNote.getNote_text());
					textView.setText(returnedNote.getNote_text());
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

		}


	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onStop() {
		super.onStop();
		stopVoiceRecorder();
		unbindService(serviceConnection);
	}

	@Override
	public void onBackPressed() {
		if (dirty) {
			updateAndReturnNote();
		} else {
			finish();
		}
	}


	@Override
	protected void onDestroy() {
		super.onDestroy();
		//Verwijder foto's die zijn gemaakt
		for (String multimediaFile : createdImages) {
			try {
				Files.delete(Paths.get(multimediaFile));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void updateAndReturnNote() {

		//TODO constant
		n.setTitle(nn(title.getText().toString(), "<Geen titel>"));
		n.setNote_text(textView.getText().toString());
		//Laten genereren
		n.setGenerated_at(null);
		//Ook laten genereren
		n.setId(null);
		if (noteMultimedia.size() > 0) {
			n.setMultimedia(noteMultimedia);
		}

		ObjectMapper om = new ObjectMapper();
		String note = null;

		try {
			note = om.writeValueAsString(n);
		} catch (IOException e) {
			e.printStackTrace();
		}
		saveNote(note);

	}

	private void startVoiceRecorder() {
		if (voiceRecorder != null) {
			voiceRecorder.stop();
		}
		visualizerView.setVisibility(View.VISIBLE);
		nonFinalText.setVisibility(View.VISIBLE);
		voiceRecorder = new VoiceRecorder(voiceCallback);
		voiceRecorder.start();
	}

	private void stopVoiceRecorder() {
		if (voiceRecorder != null) {
			voiceRecorder.stop();
			voiceRecorder = null;
		}
		visualizerView.setVisibility(View.GONE);
		nonFinalText.setVisibility(View.GONE);
		if (speechService != null) {
			speechService.finishRecognizing();
		}
	}

	//NotNull
	private String nn(Object value, String valueIfNull) {

		if (value != null) {
			return value.toString();
		} else {
			return valueIfNull;
		}

	}

	private void captureCameraImage() {
		Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		if (cameraIntent.resolveActivity(getPackageManager()) != null) {
			// Create the File where the photo should go
			File photoFile = null;
			try {
				photoFile = createImageFile();
			} catch (IOException ex) {
				// Error occurred while creating the File
			}
			// Continue only if the File was successfully created
			if (photoFile != null) {
				dirty = true;
				Uri photoURI = FileProvider.getUriForFile(this,
						"com.example.android.fileprovider",
						photoFile);
				cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
				startActivityForResult(cameraIntent, CAMERA_REQUEST);
			}
		}
	}

	private File createImageFile() throws IOException {
		// Create an image file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String imageFileName = "JPEG_" + timeStamp + "_";
		File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
		File image = File.createTempFile(
				imageFileName,  /* prefix */
				".jpg",         /* suffix */
				storageDir      /* directory */
		);

		// Save a file: path for use with ACTION_VIEW intents
		currentPhotoPath = image.getAbsolutePath();
		return image;
	}

	private void saveNote(String note) {

		HttpRequestHelper.HttpRequestFinishedListener listener = new HttpRequestHelper.HttpRequestFinishedListener() {
			@Override
			public void onResponse(Call call, Response response) {
				Handler mainHandler = new Handler(getBaseContext().getMainLooper());
				Runnable runnable = new Runnable() {
					@Override
					public void run() {
						Intent returnIntent = new Intent();
						setResult(Activity.RESULT_OK, returnIntent);
						finish();
					}
				};
				mainHandler.post(runnable);
			}

			@Override
			public void onFailure(Call call, IOException e) {

			}

			@Override
			public void onError(String message) {

			}
		};

		httpRequestHelper.saveNote(note, listener);

	}

	private void openNote(String noteUUID) {

		if (AccesTokenRequest.accesTokenRequest == null) {
			return;
		}

		NoteIdentifier noteIdentifier = new NoteIdentifier();
		noteIdentifier.setNoteID(UUID.fromString(noteUUID));
		ObjectMapper om = new ObjectMapper();
		String json = "";

		try {
			json = om.writeValueAsString(noteIdentifier);
		} catch (IOException e) {
//			Log.e("Error", e.getMessage());
		}

		HttpRequestHelper.HttpRequestFinishedListener listener = new HttpRequestHelper.HttpRequestFinishedListener() {
			@Override
			public void onResponse(Call call, Response response) {
				try {
					final String resp = response.body().string();
					Handler mainHandler = new Handler(getBaseContext().getMainLooper());
					Runnable runnable = new Runnable() {
						@Override
						public void run() {
							handleFetchedNote(resp);
						}
					};
					mainHandler.post(runnable);
				} catch (Exception e) {
					//TODO
				}
			}

			@Override
			public void onFailure(Call call, IOException e) {

			}

			@Override
			public void onError(String message) {

			}
		};
		httpRequestHelper.getNoteMostRecentVersion(json,listener);
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

	private void handleFetchedNote(String note) {

		initViews();
		ObjectMapper om = new ObjectMapper();
		try {
			n = om.readValue(note, Note.class);
			if (n.getMultimedia() != null) {
				this.noteMultimedia = n.getMultimedia();
			} else {
				this.noteMultimedia = new ArrayList<Multimedia>();
			}
			adapter.updateData(noteMultimedia);
		} catch (Exception e) {
//			Log.e("Err", "Error", e);
			finish();
		}

		title.setText(n.getTitle());
		textView.setText(n.getNote_text());
		dirty = false;
	}

}
