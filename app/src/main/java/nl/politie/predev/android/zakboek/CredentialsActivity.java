package nl.politie.predev.android.zakboek;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

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
				SharedPreferences.Editor editor = settings.edit();
				editor.putString(PREFS_USERNAME,username.getText().toString());
				editor.putString(PREFS_PASS,password.getText().toString());
				editor.commit();
				finish();
			}
		});

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
