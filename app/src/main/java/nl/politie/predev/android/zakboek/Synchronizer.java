package nl.politie.predev.android.zakboek;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class Synchronizer extends Service {


	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
