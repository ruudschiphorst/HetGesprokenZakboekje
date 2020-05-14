package nl.politie.predev.android.zakboek;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

public class InternetStatusChecker implements Runnable{

	private boolean internetFailed = false;
	private Context context;               //Ik moet een context hebben, anders kan ik de status niet ophalen
	private List<InternetStatusCheckerListener> listeners = Lists.newArrayList();
	private boolean firstRun=true;

	public InternetStatusChecker(Context context) {
		this.context = context;

	}

	public void addListener(InternetStatusCheckerListener listener) {
		listeners.add(listener);
	}

	public void removeListener(InternetStatusCheckerListener listener) {
		if(listeners.contains(listener)) {
			listeners.remove(listener);
		}
	}
	public void removeListeners() {
		this.listeners = new ArrayList<InternetStatusCheckerListener>();
	}

	@Override
	public void run() {

		while(!Thread.interrupted()){

			if(haveInternet()) {
				//Alleen reconnect event sturen als de verbinding daadwerkelijk is hersteld, niet een event sturen als hij gewoon goed draait
				if(internetFailed) {
					internetFailed = false;
					for(InternetStatusCheckerListener listener: listeners){
						listener.onInternetReconnected();
					}
				}else{
					//Doe niets. Internet is nu goed en was ook al goed
				}
			}else{
				//Geen internets
				if(!internetFailed) {
					internetFailed = true;
					for(InternetStatusCheckerListener listener: listeners){
						listener.onInternetDisconnected();
					}
				}else{
					//Doe niets. Internet was al failed en is het nu nog steeds.
				}
			}
		}
		Thread.currentThread().interrupt();
	}

	public boolean haveInternet(){

		boolean haveConnectedWifi = false;
		boolean haveConnectedMobile = false;

		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

		Network[] networks = cm.getAllNetworks();
		NetworkInfo networkInfo;
		for (Network mNetwork : networks) {
			networkInfo = cm.getNetworkInfo(mNetwork);
			if(networkInfo != null) {
				if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI){
					haveConnectedWifi = networkInfo.isConnected();
				}
				if (networkInfo.getType()== ConnectivityManager.TYPE_MOBILE){
					haveConnectedMobile = networkInfo.isConnected();

				}
			}else{
				haveConnectedWifi=false;
				haveConnectedMobile = false;
			}
		}

		if(haveConnectedWifi || haveConnectedMobile) {
			return true;
		}else{
			return false;
		}

	}

	//Deze twee events publiceren we
	public interface InternetStatusCheckerListener {
		void onInternetDisconnected();
		void onInternetReconnected();
	}
}
