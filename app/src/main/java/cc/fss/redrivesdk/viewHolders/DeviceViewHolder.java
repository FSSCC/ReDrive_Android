package cc.fss.redrivesdk.viewHolders;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import cc.fss.redrivesdk.R;

/**
 * ReDrive_Android
 * <p>
 * Created by Sławomir Bienia on 15/09/2017.
 * Copyright © 2017 FSS Sp. z o.o. All rights reserved.
 */

public class DeviceViewHolder extends RecyclerView.ViewHolder {
    public final TextView titleTextView;

    public DeviceViewHolder(View itemView) {
        super(itemView);

        titleTextView = itemView.findViewById(R.id.titleTextView);
    }
}
