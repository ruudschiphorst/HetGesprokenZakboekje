package nl.politie.predev.android.zakboek;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class NoteActivity extends AppCompatActivity {

	public static final String EXTRA_MESSAGE_NOTE = "EXTRA_MESSAGE_NOTE";
	private static final int CAMERA_REQUEST = 1888;
	public static final String NOTE_RESULT = "note_result";
	private boolean voiceInputActive = false;
	private Note n = null;
	private TextView title = null;
	private TextView textView = null;
	private InsecureStempolRpcSpeechService speechService;
	private VoiceRecorder voiceRecorder;
	private List<Multimedia> noteMultimedia;// = new ArrayList<Multimedia>();
	private NoteRecyclerViewAdapter adapter;
	private RecyclerView recyclerView;
	private RecyclerView.LayoutManager layoutManager;

	public interface RecyclerViewClickListener {
		public void onItemClicked(String imageContent);
	}

	private RecyclerViewClickListener getRecyclerViewClickListener() {
		RecyclerViewClickListener retval = new RecyclerViewClickListener() {
			@Override
			public void onItemClicked(String imageContent) {
				openFoto(imageContent);
			}
		};
		return retval;
	}

	private void openFoto(String imageContent){
		Intent intent = new Intent(this, PictureActivity.class);
		intent.putExtra(EXTRA_MESSAGE_NOTE, imageContent);
		startActivity(intent);
	}

	private final VoiceRecorder.Callback voiceCallback = new VoiceRecorder.Callback() {

		@Override
		public void onVoiceStart() {
//			showStatus(true);
			if (speechService != null) {
				speechService.startRecognizing(voiceRecorder.getSampleRate());
			}
		}

		@Override
		public void onVoice(byte[] data, int size) {
			if (speechService != null) {
				speechService.recognize(data, size);
			}
		}

		@Override
		public void onVoiceEnd() {
//			showStatus(false);
			if (speechService != null) {
				speechService.finishRecognizing();
			}
		}

	};

	private final ServiceConnection serviceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName, IBinder binder) {
			speechService = InsecureStempolRpcSpeechService.from(binder);
			speechService.addListener(mSpeechServiceListener);
//			mStatus.setVisibility(View.VISIBLE);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
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
					if (isFinal) {

						//Unk kan overal in de tekst voorkomen. Haal dit er uit.
						text = text.replace("<unk>", "").trim();

						//Er worden automatisch spaties en punten geplot. Dit klopt niet altijd, zeker als er <unk>s in het resultaat zitten.
						//Sloop de "verkeerde" spaties er uit
						while (text.contains(" .")) {
							text = text.replace(" .", ".");
						}
						//Zorg er wel voor dat we niet de hele string kwijt zijn. Dan hoeft ie niets te doen.
						//Het kan voorkomen dat na trimmen en alle <unk>'s er uit halen, er alleen een punt overblijft. Dat willen we ook niet, dus <= 1
						if (text.length() <= 1) {
							return;
						}

						final String preparedText = text;
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								textView.setText(textView.getText().toString() + " " + preparedText);
							}
						});
					}
				}

				@Override
				public void onSpeechEnd() {

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
		setContentView(R.layout.activity_note);

		title = findViewById(R.id.note_tv_title);
		textView = findViewById(R.id.note_tv_text);

		if(getIntent().getStringExtra(MainActivity.EXTRA_MESSAGE) !=null) {
			ObjectMapper om = new ObjectMapper();
			try {
				String note = getIntent().getStringExtra(MainActivity.EXTRA_MESSAGE);
				Log.e("bla",note);
				n = om.readValue(note, Note.class);
				this.noteMultimedia = n.getMultimedia();
			} catch (Exception e) {
				Log.e("Err", "Error", e);
				finish();
			}

			title.setText(n.getTitle());
			textView.setText(n.getNote_text());
		}else{
			n = new Note();
			title.setText("Nieuwe notitie");
		}
		ImageButton ib = findViewById(R.id.note_btn_save);
		ib.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				updateAndReturnNote();
			}
		});

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

	}

	private void captureCameraImage() {
		Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
		startActivityForResult(cameraIntent, CAMERA_REQUEST);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {

			Bitmap bitmap = (Bitmap) data.getExtras().get("data");

//			int size = bitmap.getRowBytes() * bitmap.getHeight();
//			ByteBuffer byteBuffer = ByteBuffer.allocate(size);
//			bitmap.copyPixelsToBuffer(byteBuffer);
//			byte[] byteArray = byteBuffer.array();

			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			bitmap.compress(Bitmap.CompressFormat.JPEG,100,stream);
			byte[] byteArray = stream.toByteArray();
			try {
				stream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			Multimedia multimedia = new Multimedia();
			multimedia.setContent(Base64.getEncoder().encodeToString(byteArray));
			noteMultimedia.add(multimedia);
			adapter.updateData(noteMultimedia);
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
	protected void onDestroy() {
		super.onDestroy();
	}

	private void updateAndReturnNote() {

		//TODO constant
		n.setTitle(nn(title.getText().toString(), "<Geen titel>"));
		n.setNote_text(textView.getText().toString());
		n.setGenerated_at(null);
		n.setId(null);
		if(noteMultimedia.size() >0) {
			n.setMultimedia(noteMultimedia);
		}
		ObjectMapper om = new ObjectMapper();
		String note = null;

		try {
			note = om.writeValueAsString(n);
		} catch (IOException e) {
			e.printStackTrace();
		}

		Intent returnIntent = new Intent();
		returnIntent.putExtra(NOTE_RESULT, note);
		setResult(Activity.RESULT_OK, returnIntent);
		finish();
	}

	private void startVoiceRecorder() {
		if (voiceRecorder != null) {
			voiceRecorder.stop();
		}
		voiceRecorder = new VoiceRecorder(voiceCallback);
		voiceRecorder.start();
	}

	private void stopVoiceRecorder() {
		if (voiceRecorder != null) {
			voiceRecorder.stop();
			voiceRecorder = null;
		}
	}

	private String nn(Object value, String valueIfNull) {

		if (value != null) {
			return value.toString();
		} else {
			return valueIfNull;
		}

	}
}
