package nl.politie.predev.android.zakboek;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Display;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.ByteArrayOutputStream;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
	private AbstractSpeechService speechService;
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
	private List<AudioContent> audioBuffer = new ArrayList<AudioContent>();
	private InternetStatusChecker internetStatusChecker;
	private Integer noOfDisconnects =0;
	private int bufferAtPosition = 0;
	private ScheduledExecutorService bufferProcessor;
	private InternetStatus internetStatus;
	private ServiceConnection serviceConnection;
	private Thread internetStatusCheckerThread;
	private static final int BUFFER_INTERVAL_MILLIS = 5;
	private boolean isDrawModeOn;
	private DrawingView drawingView;
	private static final float
			SMALL_BRUSH = 5,
			MEDIUM_BRUSH = 10,
			LARGE_BRUSH = 20;
	private LinearLayout mDrawLayout;
	private static final double MENU_MARGIN_RELATIVE_MODIFIER = 0.3;

	@Override
	protected void onStart() {
		super.onStart();
		initService();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_loading);
		settings = getSharedPreferences(PreferencesActivity.PREFS_ZAKBOEKJE, 0);

		httpRequestHelper = new HttpRequestHelper(settings);
		internetStatusChecker = new InternetStatusChecker(this,"note");
		internetStatusCheckerThread = new Thread(internetStatusChecker);
		internetStatusCheckerThread.start();

		if (internetStatusChecker.haveInternet()) {
			internetStatus = InternetStatus.ONLINE_NO_DISCONNECTS;
		} else {
			internetStatus = InternetStatus.OFFLINE_NEVER_HAD_CONNECTION;
		}

		internetStatusChecker.addListener(new InternetStatusChecker.InternetStatusCheckerListener() {
			@Override
			public void onInternetDisconnected() {
				noOfDisconnects++;
				if (noOfDisconnects < 5) {
					internetStatus = InternetStatus.OFFLINE_HAD_CONNECTION;
				} else {
					internetStatus = InternetStatus.OFFLINE_BAD_CONNECTION;
				}
			}

			@Override
			public void onInternetReconnected() {
				checkIfAddressCanBeResolved();
			}
		});

		if (getIntent().getStringExtra(MainActivity.EXTRA_MESSAGE) != null) {
			openNote(getIntent().getStringExtra(MainActivity.EXTRA_MESSAGE));
		} else {
			initViews();
			n = new Note();
			noteMultimedia = new ArrayList<Multimedia>();
			n.setMultimedia(new ArrayList<Multimedia>());
			title.setText("Nieuwe notitie");
		}
		bufferProcessor = getBufferProcessor();


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

	private enum InternetStatus {
		ONLINE_NO_DISCONNECTS,            //Online en verbinding is nooit verbroken
		ONLINE_HAD_DISCONNECTS,            //Online, maar verbinding is ooit weggevallen
		ONLINE_BAD_CONNECTION,            //Online, maar > 5 disconnects
		OFFLINE_NEVER_HAD_CONNECTION,    //Offline begonnen, internet nooit aanwezig geweest
		OFFLINE_HAD_CONNECTION,            //Offline, maar heb ooit internet gehad
		OFFLINE_BAD_CONNECTION            //Offline en meer dan 5 disconnects gehad
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onStop() {
		super.onStop();
		stopService();
		stopVoiceRecorder();
		if(!bufferProcessor.isShutdown()){
			bufferProcessor.shutdown();
		}
		if(!internetStatusCheckerThread.isInterrupted()){
			internetStatusCheckerThread.interrupt();
		}
	}

	@Override
	public void onBackPressed() {
		bufferProcessor.shutdown();
		internetStatusCheckerThread.interrupt();
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

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
		}
		return false;
	}

	private boolean useGrpc(){
		//TODO
		if(settings.getString(PreferencesActivity.PREFS_CONNECTION_METHOD, getString(R.string.connection_method_grpc)).equalsIgnoreCase(getString(R.string.connection_method_grpc))) {
			return true;
		}
		return false;
	}

	private void initService() {
		if(useGrpc()){
			bindService(new Intent(this, InsecureStempolRpcSpeechService.class), getServiceConnection(), BIND_AUTO_CREATE);
		}else{
			this.speechService = new WebSocketRecognitionService(UUID.randomUUID().toString(), 16000, settings);
			this.speechService.addListener(mSpeechServiceListener);
		}
	}

	private void stopService(){
		if(useGrpc()){
			unbindService(serviceConnection);
		}else{
			speechService.stop();
		}
	}

	public void toggleDrawMenu() {

		View formatTextSliderView = findViewById(R.id.formatTextSlider);
		View drawPanelSliderView = findViewById(R.id.drawPanelSlider);

		if (drawPanelSliderView.getVisibility() == View.VISIBLE) {
			drawPanelSliderView.setVisibility(View.GONE);
		} else {
			if (formatTextSliderView.getVisibility() == View.VISIBLE) {
				formatTextSliderView.setVisibility(View.GONE);
			}
			hideSoftKeyboard();
			drawPanelSliderView.setVisibility(View.VISIBLE);
		}

		// After changes:
		setDrawModeOn(drawPanelSliderView.getVisibility() == View.VISIBLE);
	}

	private void hideSoftKeyboard() {
		if (this.getCurrentFocus() != null) {
			try {
				InputMethodManager imm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(this.getCurrentFocus().getApplicationWindowToken(), 0);
			} catch (RuntimeException e) {
				//ignore
			}
		}
	}

	private void setDrawModeOn(boolean isOn) {
		isDrawModeOn = isOn;
	}

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
				//TODO RUUD HIER
				processVoice(data, size);
//				speechService.recognize(data, size);
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

	private ServiceConnection getServiceConnection() {
		serviceConnection = new ServiceConnection() {

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
		return serviceConnection;
	}

	private final SpeechRecognitionListener mSpeechServiceListener =
			new SpeechRecognitionListener() {
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

					final String preparedText = SpeechResultCleaner.cleanResult(text);
					if (isFinal && !preparedText.trim().equalsIgnoreCase("")) {

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
//							Toast.makeText(getBaseContext(), message, Toast.LENGTH_LONG).show();
						}
					});
				}

			};


	private void initViews() {

		setContentView(R.layout.activity_note);

		title = findViewById(R.id.note_tv_title);
		textView = findViewById(R.id.note_tv_text);
		nonFinalText = findViewById(R.id.note_tv_nonfinal_text);
		drawingView =findViewById(R.id.drawing);

		mDrawLayout = findViewById(R.id.drawPanelSlider);
		ViewGroup.LayoutParams paramsDrawPanel = mDrawLayout.getLayoutParams();
		paramsDrawPanel.height = calculateMenuMargin();

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

		recyclerView = findViewById(R.id.note_recycler_view);
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

		FloatingActionButton fabDrawMenu = findViewById(R.id.note_fab_draw);
		fabDrawMenu.setOnClickListener(new View.OnClickListener() {
		   @Override
		   public void onClick(View view) {
				toggleDrawMenu();
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

		textView.setOnTouchListener(new View.OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (isDrawModeOn) {
					dirty = true;
					drawingView.draw(event.getX() + textView.getX(), event.getY() + textView.getY(), event.getAction());
					return true;
				} else {
					return false;
				}
			}
		});

		FloatingActionButton fabMail = findViewById(R.id.note_fab_mail);
		fabMail.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if(textView.getText() !=null){
					Intent intent = new Intent(Intent.ACTION_SENDTO);
					intent.setData(Uri.parse("mailto:")); // only email apps should handle this
	//				intent.putExtra(Intent.EXTRA_EMAIL, new String[] {"mijnadres@ergens.nl"});
					intent.putExtra(Intent.EXTRA_SUBJECT, "Transcriptie " + nn(title.getText().toString(),""));
					intent.putExtra(Intent.EXTRA_TEXT, textView.getText().toString());
					if (intent.resolveActivity(getPackageManager()) != null) {
						startActivity(intent);
					}
				}

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


	private void updateAndReturnNote() {

		//TODO constant
		n.setTitle(nn(title.getText().toString(), "<Geen titel>"));
		n.setNote_text(textView.getText().toString());
		//Laten genereren
		n.setGenerated_at(null);
		//Ook laten genereren
		n.setId(null);
		processDrawingforSaving();

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
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(getBaseContext(), "Fout bij openen van notitie. Controleer de verbinding", Toast.LENGTH_LONG).show();
					}
				});
			}

			@Override
			public void onError(String message) {

			}
		};
		httpRequestHelper.getNoteMostRecentVersion(json, listener);
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
			finish();
		}

		if(n==null || n.getTitle() == null || n.getNote_text() == null ) {
			Toast.makeText(this,"Er is een fout opgetreden bij het ophalen van de notitie. Probeer het nogmaals.", Toast.LENGTH_SHORT).show();
			finish();
		}

		title.setText(n.getTitle());
		textView.setText(n.getNote_text());
		dirty = false;
	}

	private void processVoice(byte[] audio, int size) {
		AudioContent content = new AudioContent();
		content.setData(audio);
		content.setSize(size);
		audioBuffer.add(content);
	}

	private ScheduledExecutorService getBufferProcessor() {
		ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

		scheduler.scheduleAtFixedRate(new Runnable() {
			public void run() {
				//Nu online en geen rammellende verbinding = doorgaan
				if (internetStatus == InternetStatus.ONLINE_HAD_DISCONNECTS || internetStatus == InternetStatus.ONLINE_NO_DISCONNECTS) {
					if (internetStatus == InternetStatus.ONLINE_HAD_DISCONNECTS) {

						speechService.startRecognizing(voiceRecorder.getSampleRate());
					}
					if (audioBuffer.size() >= (bufferAtPosition + 1)) {
						byte[] audio = audioBuffer.get(bufferAtPosition).getData();
						int audioSize = audioBuffer.get(bufferAtPosition).getSize();
						bufferAtPosition++;
						speechService.recognize(audio, audioSize);
					}
					//Momenteel offline, maar verbidning heeft gewerkt. Laat audio maar in buffer en probeer het nogmaals als verbinding weer terug is
					//Doe nu dus niets
				} else if (internetStatus == InternetStatus.OFFLINE_HAD_CONNECTION) {
					//Niets doen
				} else {
					//Rammelende verbinding of nooit verbinding gehad. Schrijf weg naar file (TODO)

				}
			}
		}, 0, BUFFER_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);

		return scheduler;
	}

	private void checkIfAddressCanBeResolved() {
		HttpRequestHelper.HttpRequestFinishedListener listener = new HttpRequestHelper.HttpRequestFinishedListener() {
			@Override
			public void onResponse(Call call, Response response) {
				if (noOfDisconnects > 5) {
					internetStatus = InternetStatus.ONLINE_BAD_CONNECTION;
				} else {
					internetStatus = InternetStatus.ONLINE_HAD_DISCONNECTS;
				}
//				initService();
			}

			@Override
			public void onFailure(Call call, IOException e) {
				checkIfAddressCanBeResolved();
			}

			@Override
			public void onError(String message) {

			}
		};

		httpRequestHelper.getHealth(listener);
	}

	private void processDrawingforSaving(){
		Bitmap drawing = drawingView.getCanvasBitmap();
		Bitmap blankDrawing = Bitmap.createBitmap(drawing.getWidth(), drawing.getHeight(), drawing.getConfig());
		if(drawing.sameAs(blankDrawing)){
			return;
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		drawing.compress(Bitmap.CompressFormat.JPEG,100,baos);
		byte[] asBytes = baos.toByteArray();
		try{
			baos.close();
		}catch (IOException e){
			Toast.makeText(this,"Error closing ByteArrayOutputStream: " + e.getMessage(), Toast.LENGTH_LONG);
		}
		Multimedia multimedia = new Multimedia();
		multimedia.setContent(Base64.getEncoder().encodeToString(asBytes));
		multimedia.setThumbnailContent(multimedia.getContent());
		noteMultimedia.add(multimedia);

	}

	private class AudioContent {

		private int size;
		private byte[] data;

		public byte[] getData() {
			return data;
		}

		public void setData(byte[] data) {
			this.data = data;
		}

		public int getSize() {
			return size;
		}

		public void setSize(int size) {
			this.size = size;
		}
	}
	/**
	 * Method used to change drawing color
	 */
	public void changeColor(View v) {

		if (v.getTag().toString().equals("black")) {
			drawingView.setPaintColor(Color.BLACK);
		} else if (v.getTag().toString().equals("red")) {
			drawingView.setPaintColor(Color.RED);
		} else if (v.getTag().toString().equals("blue")) {
			drawingView.setPaintColor(Color.BLUE);
		} else if (v.getTag().toString().equals("green")) {
			drawingView.setPaintColor(Color.GREEN);
		} else if (v.getTag().toString().equals("yellow")) {
			drawingView.setPaintColor(Color.YELLOW);
		}
	}

	/**
	 * Method used to change brush size
	 */
	public void changeBrushSize(View v) {

		if (v.getTag().toString().equals("small")) {
			drawingView.setBrushSize(SMALL_BRUSH);
		} else if (v.getTag().toString().equals("medium")) {
			drawingView.setBrushSize(MEDIUM_BRUSH);
		} else if (v.getTag().toString().equals("large")) {
			drawingView.setBrushSize(LARGE_BRUSH);
		}
	}

	/**
	 * Method used to change erase mode
	 * Handled by erase and paint button
	 */
	public void eraseOrPaintMode(View v) {
		drawingView.setErase(v.getTag().toString().equals("erase"));
	}

	public void wipeCanvas(View v) {
		drawingView.startNew();
	}

	private int calculateMenuMargin() {
		WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
		Display display = wm.getDefaultDisplay();
		Point size = new Point();
		display.getSize(size);
		int height = size.y;
		return (int) Math.round(height * MENU_MARGIN_RELATIVE_MODIFIER);
	}
}
