package nl.politie.predev.android.zakboek;

import java.util.ArrayList;
import java.util.List;

public interface AbstractSpeechService {

	List<SpeechRecognitionListener> listeners = new ArrayList<SpeechRecognitionListener>();
	public void recognize(byte[] data, int size);
	public void startRecognizing(int sampleRate);
	public void finishRecognizing();
	public void addListener(SpeechRecognitionListener listener);
	public void removeListeners();
	public void stop();

}
