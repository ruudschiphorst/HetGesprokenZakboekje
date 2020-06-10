package nl.politie.predev.android.zakboek;

public interface SpeechRecognitionListener {

	//We beginnen met luisteren
	void onStartListening();
	//We zijn klaar om te ontvangen
	void onReadyForSpeech();
	//Men is aan het praten
	void onSpeechStarted();
	//Er komt herkende tekst terug
	void onSpeechRecognized(String text, boolean isFinal, boolean fromUpload);
	//Er wordt geen spraak meer gehoord
	void onSpeechEnd();
	//Er wordt een error gegooid
	void onError(String message);
}
