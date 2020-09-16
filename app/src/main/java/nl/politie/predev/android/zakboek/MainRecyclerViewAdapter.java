package nl.politie.predev.android.zakboek;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.UUID;

import nl.politie.predev.android.zakboek.model.Note;

public class MainRecyclerViewAdapter extends RecyclerView.Adapter<MainRecyclerViewAdapter.MainRecyclerViewHolder>{

    private List<Note> data;
    private MainActivity.RecyclerViewClickListener recyclerViewClickListener;

	public MainRecyclerViewAdapter(List<Note> data, MainActivity.RecyclerViewClickListener recyclerViewClickListener) {
		this.data = data;
		this.recyclerViewClickListener = recyclerViewClickListener;
	}

	// Create new views (invoked by the layout manager)
	@Override
	public MainRecyclerViewAdapter.MainRecyclerViewHolder onCreateViewHolder(final ViewGroup parent, int viewType) {

		View view = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.main_recyclerview_extensive, parent, false);
		MainRecyclerViewHolder vh = new MainRecyclerViewHolder(view);
		return vh;
	}

	// Return the size of your dataset (invoked by the layout manager)
	@Override
	public int getItemCount() {

		return data.size();
	}

	// Replace the contents of a view (invoked by the layout manager)
	@Override
	public void onBindViewHolder(final MainRecyclerViewHolder holder, int position) {
		// - get element from your dataset at this position
		// - replace the contents of the view with that element
		holder.title.setText(data.get(position).getTitle());
		holder.owner_text.setText(data.get(position).getOwner());
		holder.created_by.setText(data.get(position).getCreated_by());
		holder.created_at.setText(data.get(position).getGenerated_at().toString());

		if(data.get(position).isIs_public()){
			holder.is_public.setText(R.string.yes);
		}else{
			holder.is_public.setText(R.string.no);
		}

		holder.itemView.setTag(data.get(position).getNoteID() + ";" + data.get(position).getVersion());

		holder.itemView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				recyclerViewClickListener.onItemClicked(UUID.fromString(view.getTag().toString().split(";")[0]));
			}
		});
		holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View view) {
				String[] tagParts = view.getTag().toString().split(";");
				return recyclerViewClickListener.onItemLongClicked(UUID.fromString(tagParts[0]), holder.title.getText().toString(), Integer.valueOf(tagParts[1]));
			}
		});
	}

    public static class MainRecyclerViewHolder extends RecyclerView.ViewHolder {
        public TextView title;
        public TextView owner_text;
        public TextView created_by;
        public TextView created_at;
        public TextView is_public;
        public MainRecyclerViewHolder(View v) {
            super(v);
            title = v.findViewById(R.id.main_recycler_title);
            owner_text = v.findViewById(R.id.main_recycler_owner);
            created_by = v.findViewById(R.id.main_recycler_created_by);
            created_at = v.findViewById(R.id.main_recycler_created_at);
            is_public = v.findViewById(R.id.main_recycler_is_public);
        }
    }

    public void updateData(List<Note> data) {
        this.data = data;
        notifyDataSetChanged();
    }

}
