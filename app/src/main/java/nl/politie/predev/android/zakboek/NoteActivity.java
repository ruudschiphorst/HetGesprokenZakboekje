package nl.politie.predev.android.zakboek;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class NoteActivity extends AppCompatActivity {

    public static final String NOTE_RESULT = "note_result";
	private boolean voiceInputActive =false;
    private Note n = null;
    TextView title = null;
    TextView textView = null;

    private InsecureStempolRpcSpeechService speechService;
    private VoiceRecorder voiceRecorder;

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
					if(isFinal) {

						//Unk kan overal in de tekst voorkomen. Haal dit er uit.
						text = text.replace("<unk>","").trim();

						//Er worden automatisch spaties en punten geplot. Dit klopt niet altijd, zeker als er <unk>s in het resultaat zitten.
						//Sloop de "verkeerde" spaties er uit
						while(text.contains(" .")) {
							text=text.replace(" .",".");
						}
						//Zorg er wel voor dat we niet de hele string kwijt zijn. Dan hoeft ie niets te doen.
						//Het kan voorkomen dat na trimmen en alle <unk>'s er uit halen, er alleen een punt overblijft. Dat willen we ook niet, dus <= 1
						if(text.length() <= 1) {
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


        ObjectMapper om = new ObjectMapper();

        try {
            n = om.readValue(getIntent().getStringExtra(MainActivity.EXTRA_MESSAGE) , Note.class);
        } catch (IOException e) {
            e.printStackTrace();
        }

        title.setText(n.getTitle());
        textView.setText(n.getNote_text());

        ImageButton ib = findViewById(R.id.note_btn_save);
        ib.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateAndReturnNote();
            }
        });

        final ImageButton ibmic = findViewById(R.id.note_btn_mic);

        ibmic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view){
            	voiceInputActive =! voiceInputActive;
            	if(voiceInputActive) {
            		ibmic.setImageDrawable(getDrawable(android.R.drawable.presence_audio_busy));
            		startVoiceRecorder();
				}else{
            		ibmic.setImageDrawable(getDrawable(android.R.drawable.presence_audio_online));
            		stopVoiceRecorder();
				}
            }
        });

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
        n.setTitle(nn(title.getText().toString(),"<Geen titel>"));
        n.setNote_text(textView.getText().toString());
		n.setGenerated_at(null);
		n.setId(null);
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

        if(value != null) {
            return value.toString();
        }else{
            return valueIfNull;
        }

    }

}
