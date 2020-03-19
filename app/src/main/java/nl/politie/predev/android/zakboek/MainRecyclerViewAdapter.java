package nl.politie.predev.android.zakboek;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.util.Log;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.UUID;

public class MainRecyclerViewAdapter extends RecyclerView.Adapter<MainRecyclerViewAdapter.MainRecyclerViewHolder>{

    private List<Note> data;
    private MainActivity.RecyclerViewClickListener recyclerViewClickListener;

    public static class MainRecyclerViewHolder extends RecyclerView.ViewHolder {
        public TextView textView;
        public MainRecyclerViewHolder(TextView v) {
            super(v);
            textView = v;
        }
    }

    public MainRecyclerViewAdapter(List<Note> data, MainActivity.RecyclerViewClickListener recyclerViewClickListener) {
        this.data = data;
        this.recyclerViewClickListener = recyclerViewClickListener;
    }

    public void updateData(List<Note> data) {
        this.data = data;
        notifyDataSetChanged();
    }

    // Create new views (invoked by the layout manager)
    @Override
    public MainRecyclerViewAdapter.MainRecyclerViewHolder onCreateViewHolder(final ViewGroup parent, int viewType) {
        // create a new view

        TextView v = (TextView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.main_recyclerview_textview, parent, false);
        MainRecyclerViewHolder vh = new MainRecyclerViewHolder(v);

        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(final MainRecyclerViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        holder.textView.setText(data.get(position).getTitle());
        holder.textView.setTag(data.get(position).getNoteID());
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView tv = (TextView) view;
                recyclerViewClickListener.onItemClicked(UUID.fromString(tv.getTag().toString()));
                Log.i("bla",tv.getTag().toString());
            }
        });
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {

        return data.size();
    }


}
