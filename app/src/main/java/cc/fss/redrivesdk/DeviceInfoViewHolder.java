package cc.fss.redrivesdk;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

/**
 * ReDrive_Android
 * <p>
 * Created by Sławomir Bienia on 15/09/2017.
 * Copyright © 2017 FSS Sp. z o.o. All rights reserved.
 */

class DeviceInfoViewHolder extends RecyclerView.ViewHolder {
    public TextView titleTextView;

    public DeviceInfoViewHolder(View itemView) {
        super(itemView);
        titleTextView = itemView.findViewById(R.id.titleTextView);
    }
}
