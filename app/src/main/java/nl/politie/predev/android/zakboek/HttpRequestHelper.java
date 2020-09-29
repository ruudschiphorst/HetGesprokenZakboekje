package nl.politie.predev.android.zakboek;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;


import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Date;

import nl.politie.predev.android.zakboek.model.AccesTokenRequest;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpRequestHelper {

	private static SharedPreferences settings;
	private static final String APPLICATION_JSON = "application/json";


	public interface HttpRequestFinishedListener {
		void onResponse(Call call, Response response);
		void onFailure(Call call, IOException e);
		void onError(String message);
	}


	public HttpRequestHelper(SharedPreferences settings){
		this.settings = settings;
	}


	public void getHealth(final HttpRequestFinishedListener listener){

		OkHttpClient client = new OkHttpClient();


		Request request = new Request.Builder()
				.url(ServerUrls.dbUrl + "health")
				.get()
				.build();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				listener.onFailure(call, e);
			}

			@Override
			public void onResponse(Call call, Response response) {
				listener.onResponse(call, response);
				response.body().close();
				response.close();
			}
		});

	}

	public void saveNote(String noteAsJson, final HttpRequestFinishedListener listener) {

		OkHttpClient client = new OkHttpClient();

		RequestBody body = RequestBody.create(
				MediaType.parse(APPLICATION_JSON), noteAsJson);
		Request request = new Request.Builder()
				.url(ServerUrls.dbUrl + "addnote")
				.post(body)
				.addHeader(AuthHeader.key, AuthHeader.value)
				.build();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				listener.onFailure(call, e);
			}

			@Override
			public void onResponse(Call call, Response response) {
				listener.onResponse(call, response);
				response.body().close();
				response.close();
			}
		});
	}

	public void getNoteMostRecentVersion(String noteIdentifierAsString, final HttpRequestFinishedListener listener) {

		OkHttpClient client = new OkHttpClient();

		RequestBody body = RequestBody.create(
				MediaType.parse(APPLICATION_JSON), noteIdentifierAsString);

		Request request = new Request.Builder()
				.url(ServerUrls.dbUrl + "getnote")
				.post(body)
				.addHeader(AuthHeader.key, AuthHeader.value)
				.build();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				listener.onFailure(call, e);
			}

			@Override
			public void onResponse(Call call, Response response) {
				listener.onResponse(call, response);
				response.body().close();
				response.close();
			}
		});
	}

	public void deleteNote(String noteIdentifierAsString, final HttpRequestFinishedListener listener){
		RequestBody body = RequestBody.create(
				MediaType.parse(APPLICATION_JSON), noteIdentifierAsString);

		Request request = new Request.Builder()
				.url(ServerUrls.dbUrl + "deletenotebyid")
				.addHeader(AuthHeader.key, AuthHeader.value)
				.post(body)
				.build();

		OkHttpClient client = new OkHttpClient();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				listener.onFailure(call,e);
			}

			@Override
			public void onResponse(Call call, Response response)  {
				listener.onResponse(call, response);
				response.body().close();
				response.close();
			}
		});
	}

	public void getAllVersionsOfNote(String noteIdentifierAsString, final HttpRequestFinishedListener listener) {

		RequestBody body = RequestBody.create(
				MediaType.parse(APPLICATION_JSON), noteIdentifierAsString);

		Request request = new Request.Builder()
				.url(ServerUrls.dbUrl + "getallversionsofnote")
				.addHeader(AuthHeader.key, AuthHeader.value)
				.post(body)
				.build();

		OkHttpClient client = new OkHttpClient();  //getUnsafeOkHttpClient();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				listener.onFailure(call,e);
			}

			@Override
			public void onResponse(Call call, Response response)  {
				listener.onResponse(call,response);
				response.body().close();
				response.close();
			}
		});
	}

	public void getPreviousVersionsOfNote(String noteIdentifierAsString, final HttpRequestFinishedListener listener){
		RequestBody body = RequestBody.create(
				MediaType.parse(APPLICATION_JSON), noteIdentifierAsString);

		Request request = new Request.Builder()
				.url(ServerUrls.dbUrl + "getnotebyidandversion")
				.addHeader(AuthHeader.key, AuthHeader.value)
				.post(body)
				.build();

		OkHttpClient client = new OkHttpClient();  //getUnsafeOkHttpClient();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				listener.onFailure(call, e);
			}

			@Override
			public void onResponse(Call call, Response response)  {
				listener.onResponse(call, response);
				response.body().close();
				response.close();

			}
		});
	}

	public void getNotesFromServer(String endpoint, final HttpRequestFinishedListener listener){
		//Tijdens opstarten is er nog geen token (of gebruiker heeft nog geen credentials ingevoerd)
		//Alles is asynchroon, dus het kan zijn dat we gewoon even moeten wachten tot de app een token heeft opgehaald
		//En de server een reactie heeft gestuurd. Probeer het 5 seconden lang.
		//Is er geen username en password, dan komt er automatisch een prompt
		int tries = 0;

		while (true) {

			if (AccesTokenRequest.accesTokenRequest == null) {
				if (tries > 10) {
					//Meer dan 10 seconden gewacht -> geen nut.
					listener.onError("Er is een probleem bij het inloggen. Probeer het later nogmaals.");
					return;
				} else {
					tries++;
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			} else {
				//Er is inmiddels een token, ga maar uit de lus
				break;
			}
		}

		OkHttpClient client = new OkHttpClient(); //getUnsafeOkHttpClient();

		Request request = new Request.Builder()
				.url(ServerUrls.dbUrl + endpoint)
				.get()
				.addHeader(AuthHeader.key, AuthHeader.value)
				.build();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				listener.onFailure(call,e);
			}

			@Override
			public void onResponse(Call call, Response response)  {
				listener.onResponse(call, response);
				response.body().close();
				response.close();
			}
		});
	}

	public void getMultimedia(String multimediaID, final HttpRequestFinishedListener listener) {
		OkHttpClient client = new OkHttpClient();

		String json = "{\"multimediaID\": \"" + multimediaID + "\"}";


		RequestBody body = RequestBody.create(
				MediaType.parse("application/json"), json);

		Request request = new Request.Builder()
				.url(ServerUrls.dbUrl + "getmultimedia")
				.post(body)
				.addHeader("Authorization", AccesTokenRequest.accesTokenRequest.getTokenType() + " " + AccesTokenRequest.accesTokenRequest.getAccessToken())
				.build();
		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				listener.onFailure(call, e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				listener.onResponse(call, response);
				response.body().close();
				response.close();
			}
		});
	}

	public void setToken(){

		String username = settings.getString(PreferencesActivity.PREFS_USERNAME, "");
		String password = settings.getString(PreferencesActivity.PREFS_PASS, "");

		String json = "{\"username\":\"" + username + "\", \"password\":\"" + password + "\"}";

		RequestBody body = RequestBody.create(
				MediaType.parse(APPLICATION_JSON), json);

		Request request = new Request.Builder()
				.url(ServerUrls.authUrl)
				.post(body)
				.build();

		OkHttpClient client = new OkHttpClient();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				setToken();
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				if (response.code() == 200) {

					String resp = response.body().string();
					ObjectMapper om = new ObjectMapper();
					AccesTokenRequest.accesTokenRequest = om.readValue(resp, AccesTokenRequest.class);
					AccesTokenRequest.requested_at = new Date();
					response.body().close();
					response.close();
				} else {
					//TODO
				}
			}
		});
	}

	private static class ServerUrls{
		public static String dbUrl = settings.getString(PreferencesActivity.PREFS_URL_DB, PreferencesActivity.DEFAULT_BASE_HTTPS_URL_DB_API) ;
		public static String authUrl = settings.getString(PreferencesActivity.PREFS_URL_AUTH, PreferencesActivity.DEFAULT_BASE_HTTPS_URL_AUTH_API);
	}

	private static class AuthHeader{
		public static String key = "Authorization";
		public static String value = AccesTokenRequest.accesTokenRequest.getTokenType() + " " + AccesTokenRequest.accesTokenRequest.getAccessToken();

	}

}
