package nl.politie.predev.android.zakboek;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PreferencesActivity extends AppCompatActivity {

	private EditText username;
	private EditText password;
	private EditText url_db;
	private EditText url_auth;
	private Button ok;
	public static final String PREFS_ZAKBOEKJE = "gesproken_zakboekje_creds";
	public static final String PREFS_USERNAME = "username";
	public static final String PREFS_PASS = "passwd";
	public static final String PREFS_URL_AUTH = "auth_url";
	public static final String PREFS_URL_DB = "db_url";
	public static final String DEFAULT_BASE_HTTPS_URL_DB_API = "https://stempolextras.westeurope.cloudapp.azure.com:8086/";
	public static final String DEFAULT_BASE_HTTPS_URL_AUTH_API = "https://stempolextras.westeurope.cloudapp.azure.com:8085/api/auth/generatetoken";
	private SharedPreferences settings;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_set_prefs);
		username = findViewById(R.id.activity_creds_username);
		password = findViewById(R.id.activity_creds_pw);
		url_db = findViewById(R.id.activity_creds_db);
		url_auth = findViewById(R.id.activity_creds_auth);
		ok = findViewById(R.id.activity_creds_ok);

		settings = getSharedPreferences(PREFS_ZAKBOEKJE, 0);
		username.setText(settings.getString(PREFS_USERNAME, "").toString());
		password.setText(settings.getString(PREFS_PASS, "").toString());
		url_db.setText(settings.getString(PREFS_URL_DB, DEFAULT_BASE_HTTPS_URL_DB_API));
		url_auth.setText(settings.getString(PREFS_URL_AUTH, DEFAULT_BASE_HTTPS_URL_AUTH_API));

		ok.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				testAndFinish();
			}
		});

	}

	private void testAndFinish() {

		final String enteredUsername = username.getText().toString();
		final String enteredPassword = password.getText().toString();
		final String enteredAuthURL = url_auth.getText().toString();
		final String enteredDbURL = url_db.getText().toString();

		String json = "{\"username\":\"" + enteredUsername + "\", \"password\":\"" + enteredPassword + "\"}";

		RequestBody body = RequestBody.create(
				MediaType.parse("application/json"), json);

		Request request = new Request.Builder()
				.url(enteredAuthURL)
				.post(body)
				.build();

		OkHttpClient client =  new OkHttpClient();  //getUnsafeOkHttpClient();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				Handler mainHandler = new Handler(getBaseContext().getMainLooper());
				Runnable runnable = new Runnable() {
					@Override
					public void run() {
						wrongUsernamePasswordOrUrl();
					}
				};
				mainHandler.post(runnable);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				if (response.code() == 200) {
					SharedPreferences.Editor editor = settings.edit();
					editor.putString(PREFS_USERNAME, enteredUsername);
					editor.putString(PREFS_PASS, enteredPassword);
					editor.putString(PREFS_URL_AUTH, enteredAuthURL);
					editor.putString(PREFS_URL_DB, enteredDbURL);
					editor.commit();
					finish();
				}else{
					Handler mainHandler = new Handler(getBaseContext().getMainLooper());
					Runnable runnable = new Runnable() {
						@Override
						public void run() {
							wrongUsernamePasswordOrUrl();
						}
					};
					mainHandler.post(runnable);

				}
			}
		});
	}

	private void wrongUsernamePasswordOrUrl() {
		Toast.makeText(this,"Verkeerde username, password of URL. Probeer het opnieuw.",Toast.LENGTH_LONG).show();
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}
}
