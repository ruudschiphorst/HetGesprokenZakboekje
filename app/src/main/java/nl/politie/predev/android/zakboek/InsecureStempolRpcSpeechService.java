package nl.politie.predev.android.zakboek;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognizeRequest;
import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechGrpc;
import com.google.cloud.speech.v1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.cloud.speech.v1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1.StreamingRecognitionResult;
import com.google.cloud.speech.v1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1.StreamingRecognizeResponse;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.okhttp.OkHttpChannelProvider;
import io.grpc.stub.StreamObserver;


public class InsecureStempolRpcSpeechService extends Service implements AbstractSpeechService {

    private static final String TAG = "StempolRpcSpeechService";

    private static final String HOSTNAME = "40.114.240.242";
    private static final int PORT = 8081;
    private static Handler handler;
    private final SpeechBinder binder = new SpeechBinder();
    private SpeechGrpc.SpeechStub api;
    private StreamObserver<StreamingRecognizeRequest> requestObserver;

    //Ontvangt de responses van de spraakherkenningsservers voor streaming requests
    private final StreamObserver<StreamingRecognizeResponse> responseObserver = new StreamObserver<StreamingRecognizeResponse>() {

        //Ontvangen van een resultaat
        @Override
        public void onNext(StreamingRecognizeResponse response) {
            final StreamingRecognizeResponse finalResponse = response;

            //In een thread, anders loopt de UI vast
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {

                    String text = null;
                    boolean isFinal = false;
                    if (finalResponse.getResultsCount() > 0) {
                        final StreamingRecognitionResult result = finalResponse.getResults(0);
                        isFinal = result.getIsFinal();
                        if (result.getAlternativesCount() > 0) {
                            final SpeechRecognitionAlternative alternative = result.getAlternatives(0);
                            text = alternative.getTranscript();
                        }
                    }
                    //Geef alle abonnees een event
                    for(SpeechRecognitionListener listener : listeners){

                        listener.onSpeechRecognized(text, isFinal, false);
                    }
                }
            });
            thread.start();

        }

        @Override
        public void onError(Throwable t) {
        }

        @Override
        public void onCompleted() {
            for (SpeechRecognitionListener listener : listeners) {
                listener.onSpeechEnd();
            }
        }

    };


    //Ontvangt de server responses voor uploads van files
    private final StreamObserver<RecognizeResponse> fileResponseObserver
            = new StreamObserver<RecognizeResponse>() {
        @Override
        public void onNext(RecognizeResponse response) {
            String text = null;


            if (response.getResultsCount() > 0) {
                final SpeechRecognitionResult result = response.getResults(0);
                if (result.getAlternativesCount() > 0) {
                    final SpeechRecognitionAlternative alternative = result.getAlternatives(0);
                    text = alternative.getTranscript();
                }
            }
            if (text != null) {

                for (SpeechRecognitionListener listener : listeners) {
                    listener.onSpeechRecognized(text, true, true);
                }

            }
        }

        @Override
        public void onError(Throwable t) {
			for (SpeechRecognitionListener listener : listeners) {
				listener.onError("Fout bij aanroepen STeMPol: " + t.getMessage());
			}
        }

        @Override
        public void onCompleted() {
        }

    };


    public static InsecureStempolRpcSpeechService from(IBinder binder) {
        return ((SpeechBinder) binder).getService();
    }

    @Override
    public void onCreate() {

        super.onCreate();
        handler = new Handler();
        initApi();
        for(SpeechRecognitionListener listener : listeners) {
            listener.onStartListening();
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(handler !=null) {

            handler = null;
        }
        // Release the gRPC channel.
        if (api != null) {
            final ManagedChannel channel = (ManagedChannel) api.getChannel();
            if (channel != null && !channel.isShutdown()) {
                try {
                    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                }
            }
            api = null;

        }

    }


    private void initApi() {

        final ManagedChannel channel = new OkHttpChannelProvider()
                .builderForAddress(HOSTNAME, PORT)
                .usePlaintext(true)
                .nameResolverFactory(new DnsNameResolverProvider())
                .build();
        api = SpeechGrpc.newStub(channel);

    }

    private String getDefaultLanguageCode() {
        final Locale locale = Locale.getDefault();
        final StringBuilder language = new StringBuilder(locale.getLanguage());
        final String country = locale.getCountry();
        if (!TextUtils.isEmpty(country)) {
            language.append("-");
            language.append(country);
        }
        return language.toString();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
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
    /**
     * Starts recognizing speech audio.
     *
     * @param sampleRate The sample rate of the audio.
     */
	@Override
    public void startRecognizing(int sampleRate) {

        for(SpeechRecognitionListener listener :listeners){
            listener.onReadyForSpeech();
        }

        if (api == null) {
            Log.w(TAG, "API not ready. Ignoring the request.");
            return;
        }
        // Configure the API
        requestObserver = api.streamingRecognize(responseObserver);
        requestObserver.onNext(StreamingRecognizeRequest.newBuilder()
                .setStreamingConfig(StreamingRecognitionConfig.newBuilder()
                        .setConfig(RecognitionConfig.newBuilder()
                                .setLanguageCode(getDefaultLanguageCode())
                                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                                .setSampleRateHertz(sampleRate)
                                .build())
                        .setInterimResults(true)
                        .setSingleUtterance(true)
                        .build())
                .build());

    }


    /**
     * Recognizes the speech audio. This method should be called every time a chunk of byte buffer
     * is ready.
     *
     * @param data The audio data.
     * @param size The number of elements that are actually relevant in the {@code data}.
     */
	@Override
    public void recognize(byte[] data, int size) {
        if (requestObserver == null) {
            return;
        }

        for(SpeechRecognitionListener listener : listeners) {
            listener.onSpeechStarted();
        }

        // Call the streaming recognition API
        requestObserver.onNext(StreamingRecognizeRequest.newBuilder()
                .setAudioContent(ByteString.copyFrom(data, 0, size))
                .build());
    }

    /**
     * Finishes recognizing speech audio.
     */
	@Override
    public void finishRecognizing() {
        if (requestObserver == null) {
            return;
        }
        requestObserver.onCompleted();
        requestObserver = null;
        for (SpeechRecognitionListener listener : listeners) {
            listener.onSpeechEnd();
        }
    }

    /**
     * Recognize all data from the specified {@link InputStream}.
     *
     * @param stream The audio data.
     */
    public void recognizeInputStream(InputStream stream) {

        try {
            api.recognize(
                    RecognizeRequest.newBuilder()
                            .setConfig(RecognitionConfig.newBuilder()
                                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                                    .setLanguageCode(getDefaultLanguageCode())
                                    .setSampleRateHertz(16000)
                                    .build())
                            .setAudio(RecognitionAudio.newBuilder()
                                    .setContent(ByteString.readFrom(stream))
                                    .build())
                            .build(),
                    fileResponseObserver);
        } catch (IOException e) {
        }
    }
    @Override
	public void stop(){
    	//niets te doen hier
	}


    private class SpeechBinder extends Binder {

        InsecureStempolRpcSpeechService getService() {
            return InsecureStempolRpcSpeechService.this;
        }

    }

}
