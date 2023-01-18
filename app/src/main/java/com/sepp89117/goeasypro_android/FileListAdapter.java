package com.sepp89117.goeasypro_android;

import static com.sepp89117.goeasypro_android.GoProDevice.BT_CONNECTED;
import static com.sepp89117.goeasypro_android.GoProDevice.BT_NOT_CONNECTED;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Locale;

public class FileListAdapter extends ArrayAdapter<GoMediaFile> implements View.OnClickListener {
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
    public void onClick(View v) {

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

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        View rowView = inflater.inflate(R.layout.filelist_item, null, true);

        GoMediaFile goMediaFile = goMediaFiles.get(position);

        DateFormat f = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault());

        TextView name = rowView.findViewById(R.id.file_name_text);
        TextView date = rowView.findViewById(R.id.file_date_text);
        TextView size = rowView.findViewById(R.id.file_size_text);
        ImageView tn = rowView.findViewById(R.id.thumbNail_view);

        name.setText(goMediaFile.fileName);
        date.setText(f.format(goMediaFile.lastModified));
        size.setText(readableFileSize(goMediaFile.fileByteSize));

        if(goMediaFile.thumbNail != null)
            tn.setImageBitmap(goMediaFile.thumbNail);

        return rowView;
    }

    public static String readableFileSize(long size) {
        if(size <= 0) return "0";
        final String[] units = new String[] { "B", "kB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
