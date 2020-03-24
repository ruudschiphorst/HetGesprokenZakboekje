package nl.politie.predev.android.zakboek;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
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

public class CredentialsActivity extends AppCompatActivity {

	private EditText username;
	private EditText password;
	private Button ok;
	public static final String PREFS_ZAKBOEKJE = "gesproken_zakboekje_creds";
	public static final String PREFS_USERNAME = "username";
	public static final String PREFS_PASS = "passwd";
	private SharedPreferences settings;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_set_user_pass);
		username = findViewById(R.id.activity_creds_username);
		password = findViewById(R.id.activity_creds_pw);
		ok = findViewById(R.id.activity_creds_ok);

		settings = getSharedPreferences(PREFS_ZAKBOEKJE, 0);
		username.setText(settings.getString(PREFS_USERNAME, "ruud").toString());
		password.setText(settings.getString(PREFS_PASS, "secret").toString());

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

		String json = "{\"username\":\"" + enteredUsername + "\", \"password\":\"" + enteredPassword + "\"}";

		RequestBody body = RequestBody.create(
				MediaType.parse("application/json"), json);

		Request request = new Request.Builder()
				.url("https://stempolextras.westeurope.cloudapp.azure.com:8085/api/auth/generatetoken")
				.post(body)
				.build();

		OkHttpClient client =  new OkHttpClient();  //getUnsafeOkHttpClient();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				Log.e("err", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				if (response.code() == 200) {
					SharedPreferences.Editor editor = settings.edit();
					editor.putString(PREFS_USERNAME, enteredUsername);
					editor.putString(PREFS_PASS,enteredPassword);
					editor.commit();
					finish();
				}else{
					Handler mainHandler = new Handler(getBaseContext().getMainLooper());
					Runnable runnable = new Runnable() {
						@Override
						public void run() {
							wrongUsernamePassword();
						}
					};
					mainHandler.post(runnable);

				}
			}
		});
	}

	private void wrongUsernamePassword() {
		Toast.makeText(this,"Verkeerde username en/of password. Probeer het opnieuw.",Toast.LENGTH_LONG).show();
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
