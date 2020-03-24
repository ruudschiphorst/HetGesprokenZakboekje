package nl.politie.predev.android.zakboek;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Base64;

public class PictureActivity extends AppCompatActivity {

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_show_picture);
		if(getIntent().getStringExtra(NoteActivity.EXTRA_MESSAGE_NOTE) !=null) {
			ImageView iv = findViewById(R.id.show_picture_picture);
			byte[] bytes = Base64.getDecoder().decode(getIntent().getStringExtra(NoteActivity.EXTRA_MESSAGE_NOTE));
			Bitmap bitmap = BitmapFactory.decodeByteArray(bytes,0, bytes.length);
			iv.setImageBitmap(bitmap);
		}

	}
}
