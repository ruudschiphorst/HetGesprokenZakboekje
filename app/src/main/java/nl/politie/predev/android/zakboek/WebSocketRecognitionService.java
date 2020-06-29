package nl.politie.predev.android.zakboek;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import android.util.Log;
import android.util.Pair;

//import nl.np.phon.android.speak.ChunkedWebRecSessionBuilder;
//import nl.np.phon.android.speak.Log;
//import nl.np.phon.android.speak.R;
//import nl.np.phon.android.speak.utils.QueryUtils;
//import nl.np.phon.android.speechutils.AudioRecorder;
//import nl.np.phon.android.speechutils.EncodedAudioRecorder;
//import nl.np.phon.android.speechutils.Extras;
//import nl.np.phon.android.speechutils.service.AbstractRecognitionService;
//import nl.np.phon.android.speechutils.utils.PreferenceUtils;

/**
 * Implements RecognitionService, connects to the server via WebSocket.
 */
public class WebSocketRecognitionService  implements AbstractSpeechService{

    // When does the chunk sending start and what is its interval
    private static final int TASK_DELAY_SEND = 100;
    private static final int TASK_INTERVAL_SEND = 200;
    // Limit to the number of hypotheses that the service will return
    // TODO: make configurable
    private static final int MAX_HYPOTHESES = 100;
    // Pretty-print results
    // TODO: make configurable
    private static final boolean PRETTY_PRINT = true;

    private static final String EOD = "EOD";
    private static final String EOS = "EOS";

    private static final String PROTOCOL = "";

    private static final int MSG_RESULT = 1;
    private static final int MSG_ERROR = 2;

    private volatile Looper mSendLooper;
    private volatile Handler mSendHandler;

    private MyHandler mMyHandler;

    private Runnable mSendRunnable;

    public WebSocket mWebSocket;

    private String mUrl;

    private boolean mIsEosSent;
	private static final String PARAMETER_SEPARATOR = "&";
	private static final String NAME_VALUE_SEPARATOR = "=";
    private int mNumBytesSent;
	private EncodedAudioRecorder encodedAudioRecorder;
	private int sampleRate;

    public WebSocketRecognitionService(String contentID, int sampleRate ){
    	configure(contentID, sampleRate);

	}

//    @Override
    protected void configure(String contentID, int sampleRate)  {
    	this.sampleRate = sampleRate;
		mUrl = "wss://stempol.nl:82/speech" + getWsArgs() + getQueryParams("UTF-8", contentID);
		Log.e("bla", mUrl);
        boolean isUnlimitedDuration = true;
        configureHandler(isUnlimitedDuration, true);
        connect();
    }

//    @Override
    protected void connect() {
        startSocket(mUrl);
    }

//    @Override
    protected void disconnect() {
        if (mSendHandler != null) mSendHandler.removeCallbacks(mSendRunnable);
        if (mSendLooper != null) {
            mSendLooper.quit();
            mSendLooper = null;
        }

        if (mWebSocket != null && mWebSocket.isOpen()) {
            mWebSocket.end(); // TODO: or close?
            mWebSocket = null;
        }
//        Log.i("Number of bytes sent: " + mNumBytesSent);
    }

//    @Override
//    protected String getEncoderType() {
//        return PreferenceUtils.getPrefString(getSharedPreferences(), getResources(),
//                R.string.keyImeAudioFormat, R.string.defaultAudioFormat);
//    }

//    @Override
//    protected boolean isAudioCues() {
//        return PreferenceUtils.getPrefBoolean(getSharedPreferences(), getResources(), R.string.keyImeAudioCues, R.bool.defaultImeAudioCues);
//    }

    protected void configureHandler(boolean isUnlimitedDuration, boolean isPartialResults) {
        mMyHandler = new MyHandler(this, isUnlimitedDuration, isPartialResults, listeners);
    }

    private void handleResult(String text) {
        Message msg = new Message();
        msg.what = MSG_RESULT;
        msg.obj = text;
        mMyHandler.sendMessage(msg);
    }

    private void handleException(Exception error) {
        Message msg = new Message();
        msg.what = MSG_ERROR;
        msg.obj = error;
        mMyHandler.sendMessage(msg);
    }

    /**
     * Opens the socket and starts recording/sending.
     *
     * @param url Webservice URL
     */
    void startSocket(String url) {
        mIsEosSent = false;
//        Log.i(url);

        AsyncHttpClient.getDefaultInstance().websocket(url, PROTOCOL, new AsyncHttpClient.WebSocketConnectCallback() {
            @Override
            public void onCompleted(Exception ex, final WebSocket webSocket) {
                mWebSocket = webSocket;

                if (ex != null) {
                    handleException(ex);
                    return;
                }

                webSocket.setStringCallback(new WebSocket.StringCallback() {
                    public void onStringAvailable(String s) {
//                        Log.i(s);
                        handleResult(s);
                    }
                });

                webSocket.setClosedCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        if (ex == null) {
                            Log.e("bla","ClosedCallback");
                            for(SpeechRecognitionListener listener : listeners){
								listener.onSpeechEnd();
							}
//                            handleFinish(mIsEosSent);
                        } else {
//                            Log.e("ClosedCallback: ", ex);
                            handleException(ex);
                        }
                    }
                });

                webSocket.setEndCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        if (ex == null) {
                            Log.e("bla","EndCallback");
							for(SpeechRecognitionListener listener : listeners){
								listener.onSpeechEnd();
							}
//                            handleFinish(mIsEosSent);
                        } else {
//                            Log.e("EndCallback: ", ex);
                            handleException(ex);
                        }
                    }
                });
//				mWebSocket = webSocket;
//                startSending(webSocket);
            }
        });
    }



    private void startSending(final WebSocket webSocket) {
        mNumBytesSent = 0;
        HandlerThread thread = new HandlerThread("WsSendHandlerThread", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        mSendLooper = thread.getLooper();
        mSendHandler = new Handler(mSendLooper);

        // Send chunks to the server
        mSendRunnable = new Runnable() {
            public void run() {
                if (webSocket != null && webSocket.isOpen()) {
                    /* SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    if (prefs.getBoolean("sendEOD", false)) {
                        sendEOD(webSocket);
                    } */
                    AudioRecorder recorder = getAudioRecorder();
                    if (recorder == null || recorder.getState() != AudioRecorder.State.RECORDING) {
//                        Log.i("Sending: EOS (recorder == null)");
                        webSocket.send(EOS);
                        mIsEosSent = true;
                    } else {
                        byte[] buffer = recorder.consumeRecordingAndTruncate();
                        if (recorder instanceof EncodedAudioRecorder) {
                            send(webSocket, ((EncodedAudioRecorder) recorder).consumeRecordingEncAndTruncate());
                        } else {
                            send(webSocket, buffer);
                        }
                        if (buffer.length > 0) {
//                            onBufferReceived(buffer);
                        }
                        boolean success = mSendHandler.postDelayed(this, TASK_INTERVAL_SEND);
                        if (!success) {
//                            Log.i("mSendHandler.postDelayed returned false");
                        }
                    }
                }
            }
        };

        mSendHandler.postDelayed(mSendRunnable, TASK_DELAY_SEND);
    }



    public void send(final WebSocket webSocket, final byte[] buffer) {
    	if(mWebSocket == null){
    		return;
		}
    	Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				if (buffer != null && buffer.length > 0) {
					webSocket.send(buffer);
					mNumBytesSent += buffer.length;
				}
			}
		});
		thread.start();
    }

	@Override
	public void recognize(byte[] data, int size) {
		send(this.mWebSocket,data);
	}

	@Override
	public void startRecognizing(int sampleRate) {
		this.sampleRate = sampleRate;
	}

	@Override
	public void finishRecognizing() {
		if(this.mWebSocket !=null && this.mWebSocket.isOpen()){
			mWebSocket.send(EOS);
		}

		for (SpeechRecognitionListener listener : listeners) {
			listener.onSpeechEnd();
		}
	}

	@Override
	public void addListener(SpeechRecognitionListener listener) {
		listeners.add(listener);
	}

	@Override
	public void removeListeners() {
		for(SpeechRecognitionListener listener: listeners){
			try{
				listeners.remove(listener);
			}catch (Exception e){
				e.printStackTrace();
			}
		}

	}

	@Override
	public void stop(){
    	disconnect();
	}

	private static class MyHandler extends Handler {
        private final WeakReference<WebSocketRecognitionService> mRef;
        private final boolean mIsUnlimitedDuration;
        private final boolean mIsPartialResults;
        private final List<SpeechRecognitionListener> listeners;

        public MyHandler(WebSocketRecognitionService c, boolean isUnlimitedDuration, boolean isPartialResults,List<SpeechRecognitionListener> listeners ) {
            mRef = new WeakReference<>(c);
            mIsUnlimitedDuration = isUnlimitedDuration;
            mIsPartialResults = isPartialResults;
            this.listeners = listeners;
        }

        @Override
        public void handleMessage(Message msg) {
            WebSocketRecognitionService outerClass = mRef.get();
            if (outerClass != null) {
                if (msg.what == MSG_ERROR) {
                    Exception e = (Exception) msg.obj;
                    if (e instanceof TimeoutException) {
                    	for(SpeechRecognitionListener listener:listeners){
                    		listener.onError("timeout");
						}
//                        outerClass.onError(SpeechRecognizer.ERROR_NETWORK_TIMEOUT);
                    } else {
						for(SpeechRecognitionListener listener:listeners){
							listener.onError(e.getMessage());
						}

//                        outerClass.onError(SpeechRecognizer.ERROR_NETWORK);
                    }
                } else if (msg.what == MSG_RESULT) {
                    try {
                        WebSocketResponse response = new WebSocketResponse((String) msg.obj);
                        int statusCode = response.getStatus();
                        if (statusCode == WebSocketResponse.STATUS_SUCCESS && response.isResult()) {
                            WebSocketResponse.Result responseResult = response.parseResult();
                            if (responseResult.isFinal()) {
                                ArrayList<String> hypotheses = responseResult.getHypotheses(MAX_HYPOTHESES, PRETTY_PRINT);
                                if (hypotheses.isEmpty()) {
//                                    Log.i("Empty final result (" + hypotheses + "), stopping");

									for(SpeechRecognitionListener listener:listeners){
										listener.onError("timeout");
									}
//                                    outerClass.onError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT);
                                } else {
                                    // We stop listening unless the caller explicitly asks us to carry on,
                                    // by setting EXTRA_UNLIMITED_DURATION=true
                                    if (mIsUnlimitedDuration) {
										for(SpeechRecognitionListener listener:listeners){
											listener.onSpeechRecognized(hypotheses.get(0),true,false);
										}
										Log.e("bla", hypotheses.get(0));
//                                        outerClass.onPartialResults(toResultsBundle(hypotheses, true));
                                    } else {
                                        outerClass.mIsEosSent = true;
										for(SpeechRecognitionListener listener:listeners){
											listener.onSpeechRecognized(hypotheses.get(0), true, false);
											listener.onSpeechEnd();
										}
//                                        outerClass.onEndOfSpeech();
//                                        outerClass.onResults(toResultsBundle(hypotheses, true));
                                    }
                                }
                            } else {
                                // We fire this only if the caller wanted partial results
                                if (mIsPartialResults) {
                                    ArrayList<String> hypotheses = responseResult.getHypotheses(MAX_HYPOTHESES, PRETTY_PRINT);
                                    if (hypotheses.isEmpty()) {
//                                        Log.i("Empty non-final result (" + hypotheses + "), ignoring");
                                    } else {
										for(SpeechRecognitionListener listener:listeners){
											listener.onSpeechRecognized(hypotheses.get(0), false, false);
											listener.onSpeechEnd();
										}
//                                        outerClass.onPartialResults(toResultsBundle(hypotheses, false));
                                    }
                                }
                            }
                        } else if (statusCode == WebSocketResponse.STATUS_SUCCESS) {
                            // TODO: adaptation_state currently not handled
                        } else if (statusCode == WebSocketResponse.STATUS_ABORTED) {
//                            outerClass.onError(SpeechRecognizer.ERROR_SERVER);
                        } else if (statusCode == WebSocketResponse.STATUS_NOT_AVAILABLE) {
//                            outerClass.onError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY);
                        } else if (statusCode == WebSocketResponse.STATUS_NO_SPEECH) {
//                            outerClass.onError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT);
                        } else if (statusCode == WebSocketResponse.STATUS_NO_VALID_FRAMES) {
//                            outerClass.onError(SpeechRecognizer.ERROR_NO_MATCH);
                        } else {
                            // Server sent unsupported status code, client should be updated
//                            outerClass.onError(SpeechRecognizer.ERROR_CLIENT);
                        }
                    } catch (WebSocketResponse.WebSocketResponseException e) {
                        // This results from a syntactically incorrect server response object
//                        Log.e((String) msg.obj, e);
//                        outerClass.onError(SpeechRecognizer.ERROR_SERVER);
                    }
                }
            }
        }
    }

	private String getWsArgs() {
		return "?content-type=audio/x-raw,+layout=(string)interleaved,+rate=(int)"+ this.sampleRate +",+format=(string)S16LE,+channels=(int)1";
	}

	public static String getQueryParams(String encoding, String contentId)   {
		List<Pair<String, String>> list = new ArrayList<>();
//		flattenBundle("editorInfo_", list, intent.getBundleExtra(Extras.EXTRA_EDITOR_INFO));
		flattenBundle("editorInfo_", list, null);
		listAdd(list, "lang", "nl-NL");
		listAdd(list, "lm", null);
		listAdd(list, "output-lang", null);
		//TODO
		listAdd(list, "user-agent","RecognizerIntentActivity/1.6.90; HMD Global/PLE/00WW_6_19C; null/null");
		listAdd(list, "calling-package", null);
		// listAdd(list, "user-id", builder.getDeviceId());
		listAdd(list, "user-id", "ruudschiphorst@gmail.com");
		listAdd(list, "location", null);
		listAdd(list, "partial", "true");
		listAdd(list, "content-id", contentId);
		if (list.size() == 0) {
			return "";
		}
		try {
			return PARAMETER_SEPARATOR + encodeKeyValuePairs(list, encoding);
		} catch (UnsupportedEncodingException e) {
			return null;
		}
	}

	private static boolean listAdd(List<Pair<String, String>> list, String key, String value) {
		if (value == null || value.length() == 0) {
			return false;
		}
		return list.add(new Pair<>(key, value));
	}
	private static void flattenBundle(String prefix, List<Pair<String, String>> list, Bundle bundle) {
		if (bundle != null) {
			for (String key : bundle.keySet()) {
				Object value = bundle.get(key);
				if (value != null) {
					if (value instanceof Bundle) {
						flattenBundle(prefix + key + "_", list, (Bundle) value);
					} else {
						listAdd(list, prefix + key, toString(value));
					}
				}
			}
		}
	}

	private static String toString(Object obj) {
		if (obj == null) {
			return null;
		}
		return obj.toString();
	}
	private static String encodeKeyValuePairs(final List<Pair<String, String>> parameters, final String encoding) throws UnsupportedEncodingException {
		final StringBuilder result = new StringBuilder();
		for (final Pair<String, String> parameter : parameters) {
			final String encodedName = URLEncoder.encode(parameter.first, encoding);
			final String value = parameter.second;
			final String encodedValue = value != null ? URLEncoder.encode(value, encoding) : "";
			if (result.length() > 0)
				result.append(PARAMETER_SEPARATOR);
			result.append(encodedName);
			result.append(NAME_VALUE_SEPARATOR);
			result.append(encodedValue);
		}
		return result.toString();
	}

	private EncodedAudioRecorder getAudioRecorder(){
    	if(encodedAudioRecorder == null){
			EncodedAudioRecorder audioRecorder = new EncodedAudioRecorder(64000,"audio/x-flac");
			encodedAudioRecorder = audioRecorder;
    	}
    	return  encodedAudioRecorder;
	}

}