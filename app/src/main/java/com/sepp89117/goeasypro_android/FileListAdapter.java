package com.sepp89117.goeasypro_android;

import static com.sepp89117.goeasypro_android.MyApplication.getReadableFileSize;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Locale;

public class FileListAdapter extends ArrayAdapter<GoMediaFile> {
    Context context;
    private final ArrayList<GoMediaFile> goMediaFiles;
    private static LayoutInflater inflater = null;

    public FileListAdapter(ArrayList<GoMediaFile> goMediaFiles, Context context) {
        super(context, R.layout.filelist_item, goMediaFiles);

        this.context = context;
        this.goMediaFiles = goMediaFiles;
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return goMediaFiles.size();
    }

    @Override
    public GoMediaFile getItem(int position) {
        return goMediaFiles.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    static class ViewHolder {
        private TextView name;
        private TextView date;
        private TextView size;
        private TextView multishot_size;
        private ImageView tn;
        private LinearLayout multishot_layout;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder mViewHolder;

        if (convertView == null) {
            mViewHolder = new ViewHolder();
            convertView = inflater.inflate(R.layout.filelist_item, null, true);

            mViewHolder.name = convertView.findViewById(R.id.file_name_text);
            mViewHolder.date = convertView.findViewById(R.id.file_date_text);
            mViewHolder.size = convertView.findViewById(R.id.file_size_text);
            mViewHolder.multishot_size = convertView.findViewById(R.id.multishot_size);
            mViewHolder.tn = convertView.findViewById(R.id.thumbNail_view);
            mViewHolder.multishot_layout = convertView.findViewById(R.id.multishot_layout);

            convertView.setTag(mViewHolder);
        } else {
            mViewHolder = (ViewHolder) convertView.getTag();
        }
        
        GoMediaFile goMediaFile = goMediaFiles.get(position);
        DateFormat f = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault());

        mViewHolder.name.setText(goMediaFile.fileName);
        mViewHolder.date.setText(f.format(goMediaFile.lastModified));
        mViewHolder.size.setText(getReadableFileSize(goMediaFile.fileByteSize));

        if(goMediaFile.isGroup) {
            mViewHolder.multishot_size.setText(String.valueOf(goMediaFile.groupLength));
            mViewHolder.multishot_layout.setVisibility(View.VISIBLE);
        } else {
            mViewHolder.multishot_layout.setVisibility(View.INVISIBLE);
        }

        if(goMediaFile.thumbNail != null)
            mViewHolder.tn.setImageBitmap(goMediaFile.thumbNail);

        return convertView;
    }
}
