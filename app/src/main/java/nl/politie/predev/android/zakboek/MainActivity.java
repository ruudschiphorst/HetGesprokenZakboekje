package nl.politie.predev.android.zakboek;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

	private RecyclerView recyclerView;
	private MainRecyclerViewAdapter adapter;
	private RecyclerView.LayoutManager layoutManager;
	private AccesTokenRequest atr;
	List<Note> data = new ArrayList<Note>();
	private ScheduledExecutorService tokenRefresher;
	public static final String EXTRA_MESSAGE = "ZAKBOEKJE_NOTE";
	public static final int NOTE_ACTIVITY_RESULT = 1;
	private static final String BASE_HTTPS_URL_DB_API = "https://40.114.240.242:8086/";


	public interface RecyclerViewClickListener {
		public void onItemClicked(UUID uuid);
	}


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);

		recyclerView = (RecyclerView) findViewById(R.id.activity__main_recycler);
		recyclerView.setHasFixedSize(true);

		layoutManager = new LinearLayoutManager(this);
		((LinearLayoutManager) layoutManager).setOrientation(RecyclerView.VERTICAL);
		recyclerView.setLayoutManager(layoutManager);

		adapter = new MainRecyclerViewAdapter(new ArrayList<Note>(), getRecyclerViewClickListener());
		recyclerView.setAdapter(adapter);

		FloatingActionButton fabAdd = findViewById(R.id.activity_main_add);
		fabAdd.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				openNoteActivity(null);
			}
		});

	}

	private RecyclerViewClickListener getRecyclerViewClickListener() {

		RecyclerViewClickListener retval = new RecyclerViewClickListener() {
			@Override
			public void onItemClicked(UUID uuid) {
				openNote(uuid);
			}
		};
		return retval;
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (tokenRefresher == null || tokenRefresher.isShutdown()) {
			tokenRefresher = getTokenRefresher();
		}
		getNotesFromServer();
	}


	public void openNote(UUID noteUUID) {

		if (atr == null) {

			Toast.makeText(getBaseContext(), "Nog geen accesstoken. Moment geduld...", Toast.LENGTH_SHORT);
			return;

		}

		OkHttpClient client = getUnsafeOkHttpClient();

		String json = "{\"noteID\": \"" + noteUUID.toString() + "\", \"version\": null}";

		RequestBody body = RequestBody.create(
				MediaType.parse("application/json"), json);

		Request request = new Request.Builder()
				.url(BASE_HTTPS_URL_DB_API + "getnote")
				.post(body)
				.addHeader("Authorization", atr.getTokenType() + " " + atr.getAccessToken())
				.build();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				Log.e("err", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				String resp = response.body().string();
				Log.e("bla", resp);
				openNoteActivity(resp);
			}
		});

	}

	private void openNoteActivity(String note) {

		Intent intent = new Intent(this, NoteActivity.class);
		intent.putExtra(EXTRA_MESSAGE, note);
		startActivityForResult(intent, NOTE_ACTIVITY_RESULT);

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == NOTE_ACTIVITY_RESULT) {
			if (resultCode == Activity.RESULT_OK) {
				String resultNote = data.getStringExtra(NoteActivity.NOTE_RESULT);
				saveNote(resultNote);
			} else {

			}
		}
	}

	private void getNotesFromServer() {

		//Tijdens opstarten is er nog geen token (of gebruiker heeft nog geen credentials ingevoerd)
		//Alles is asynchroon, dus het kan zijn dat we gewoon even moeten wachten tot de app een token heeft opgehaald
		//En de server een reactie heeft gestuurd. Probeer het 5 seconden lang.
		//Is er geen username en password, dan komt er automatisch een prompt
		int tries = 0;

		while(true){

			if(atr == null) {
				if(tries > 5) {
					//Meer dan 5 seconden gewacht -> geen nut. Stop maar met proberen en wacht tot onResume() opnieuw wordt aangeroepen,
					//bijvoorbeeld omdat gebruiker het password scherm heeft bijgewerkt.
					return;
				}else{
					tries++;
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}else{
				//Er is inmiddels een token, ga maar uit de lus
				break;
			}
		}

		if (atr == null) {
			try {
				//Gebeurt asynchroon, dus even geduld om te proberen of het lukt
				Thread.sleep(2500);
			} catch (InterruptedException e) {
				//TODO
				e.printStackTrace();
			}
			if(atr == null) {
				return;
			}
		}

		OkHttpClient client = getUnsafeOkHttpClient();

		Request request = new Request.Builder()
				.url(BASE_HTTPS_URL_DB_API + "getall")
				.get()
				.addHeader("Authorization", atr.getTokenType() + " " + atr.getAccessToken())
				.build();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				Log.e("err", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				String resp = response.body().string();
				ObjectMapper om = new ObjectMapper();
				data = Arrays.asList(om.readValue(resp, Note[].class));
				Collections.sort(data);
				//uitvoeren op main thread
				Handler mainHandler = new Handler(getBaseContext().getMainLooper());

				Runnable runnable = new Runnable() {
					@Override
					public void run() {
						adapter.updateData(data);
					}
				};
				mainHandler.post(runnable);
			}
		});

	}

	private void saveNote(String note) {

		
		OkHttpClient client = getUnsafeOkHttpClient();

		RequestBody body = RequestBody.create(
				MediaType.parse("application/json"), note);
		Request request = new Request.Builder()
				.url(BASE_HTTPS_URL_DB_API + "addnote")
				.post(body)
				.addHeader("Authorization", atr.getTokenType() + " " + atr.getAccessToken())
				.build();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				Log.e("err", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {

				Handler mainHandler = new Handler(getBaseContext().getMainLooper());
				Runnable runnable = new Runnable() {
					@Override
					public void run() {
						getNotesFromServer();
					}
				};
				mainHandler.post(runnable);
			}
		});

	}

	//TODO
	private static OkHttpClient getUnsafeOkHttpClient() {
		try {
			// Create a trust manager that does not validate certificate chains
			final TrustManager[] trustAllCerts = new TrustManager[]{
					new X509TrustManager() {
						@Override
						public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
						}

						@Override
						public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
						}

						@Override
						public java.security.cert.X509Certificate[] getAcceptedIssuers() {
							return new java.security.cert.X509Certificate[]{};
						}
					}
			};

			// Install the all-trusting trust manager
			final SSLContext sslContext = SSLContext.getInstance("SSL");
			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
			// Create an ssl socket factory with our all-trusting manager
			final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

			OkHttpClient.Builder builder = new OkHttpClient.Builder();
			builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
			builder.hostnameVerifier(new HostnameVerifier() {
				@Override
				public boolean verify(String hostname, SSLSession session) {
					return true;
				}
			});

			OkHttpClient okHttpClient = builder.build();
			return okHttpClient;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private ScheduledExecutorService getTokenRefresher() {

		SharedPreferences settings = getSharedPreferences(CredentialsActivity.PREFS_ZAKBOEKJE, 0);
		String username = settings.getString(CredentialsActivity.PREFS_USERNAME, "").toString();
		String password = settings.getString(CredentialsActivity.PREFS_PASS, "").toString();

		//Geen username + pass = prompt gebruiker
		if (username.equalsIgnoreCase("") || password.equalsIgnoreCase("")) {
			Intent intent = new Intent(getApplicationContext(), CredentialsActivity.class);
			startActivity(intent);
		} else {

			ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

			scheduler.scheduleAtFixedRate(new Runnable() {
				public void run() {
					setToken();
				}
			}, 0, 10, TimeUnit.MINUTES);

			return scheduler;
		}
		return null;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		tokenRefresher.shutdown();
	}

	private void setToken() {

		Log.e("bla", "setting token...");

		SharedPreferences settings = getSharedPreferences(CredentialsActivity.PREFS_ZAKBOEKJE, 0);
		String username = settings.getString(CredentialsActivity.PREFS_USERNAME, "").toString();
		String password = settings.getString(CredentialsActivity.PREFS_PASS, "").toString();

		String json = "{\"username\":\"" + username + "\", \"password\":\"" + password + "\"}";

		RequestBody body = RequestBody.create(
				MediaType.parse("application/json"), json);

		Request request = new Request.Builder()
				.url("https://40.114.240.242:8085/api/auth/generatetoken")
				.post(body)
				.build();

		OkHttpClient client = getUnsafeOkHttpClient();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				Log.e("err", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				if (response.code() == 200) {
					String resp = response.body().string();
					ObjectMapper om = new ObjectMapper();
					atr = om.readValue(resp, AccesTokenRequest.class);
				}else{
					//TODO
				}
			}
		});
	}
}
