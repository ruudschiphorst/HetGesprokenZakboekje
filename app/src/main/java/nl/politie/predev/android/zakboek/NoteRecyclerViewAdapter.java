package nl.politie.predev.android.zakboek;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class NoteRecyclerViewAdapter extends RecyclerView.Adapter<NoteRecyclerViewAdapter.NoteRecyclerViewHolder>{

    private List<Multimedia> data;
    private NoteActivity.RecyclerViewClickListener recyclerViewClickListener;

    public static class NoteRecyclerViewHolder extends RecyclerView.ViewHolder {
        public ImageView imageview;
        public NoteRecyclerViewHolder(ImageView v) {
            super(v);
            imageview = v;
        }
    }

    public NoteRecyclerViewAdapter(List<Multimedia> data, NoteActivity.RecyclerViewClickListener recyclerViewClickListener) {
        this.data = data;
        this.recyclerViewClickListener = recyclerViewClickListener;
    }

    public void updateData(List<Multimedia> data) {
        this.data = data;
        notifyDataSetChanged();
    }

    // Create new views (invoked by the layout manager)
    @Override
    public NoteRecyclerViewAdapter.NoteRecyclerViewHolder onCreateViewHolder(final ViewGroup parent, int viewType) {
        // create a new view

        ImageView v = (ImageView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.note_recyclerview_imageview, parent, false);
        NoteRecyclerViewHolder vh = new NoteRecyclerViewHolder(v);

        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(final NoteRecyclerViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
		byte[] bytes = Base64.getDecoder().decode(data.get(position).getContent());
		Bitmap bitmap = BitmapFactory.decodeByteArray(bytes,0, bytes.length);
        holder.imageview.setImageBitmap(bitmap);
        if(data.get(position).getMultimediaID() !=null) {
        	holder.imageview.setTag(data.get(position).getMultimediaID().toString());
		}else{
        	holder.imageview.setTag(data.get(position).getLocalFilePath());
		}
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ImageView iv = (ImageView) view;

                recyclerViewClickListener.onItemClicked(iv.getTag().toString());
            }
        });
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
		if(data ==null)	{
			return 0;
		}
        return data.size();
    }


}
