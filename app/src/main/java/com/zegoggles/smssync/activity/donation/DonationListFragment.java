package com.zegoggles.smssync.activity.donation;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import android.text.TextUtils;

import com.android.billingclient.api.SkuDetails;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.Dialogs;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

import static android.R.string.cancel;

public class DonationListFragment extends Dialogs.BaseFragment {
    static final String SKUS = "skus";
    private SkuSelectionListener listener;

    interface SkuSelectionListener {
        void selectedSku(SkuDetails sku);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof SkuSelectionListener) {
            listener = (SkuSelectionListener) context;
        } else {
            throw new IllegalArgumentException("Context does not implement SkuSelectionListener");
        }
    }

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final ArrayList<Sku> skus = getArguments().getParcelableArrayList(SKUS);

        return new AlertDialog.Builder(getContext())
            .setTitle(R.string.ui_dialog_donate_message)
            .setItems(getOptions(skus), new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        listener.selectedSku(new SkuDetails(skus.get(which).getOriginalJson()));
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            })
            .setNegativeButton(cancel, new OnClickListener() {
                @Override public void onClick(DialogInterface dialogInterface, int which) {
                    onCancel(dialogInterface);
                }
            })
            .create();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        getActivity().finish();
    }

    private CharSequence[] getOptions(List<Sku> skus) {
        List<String> options = new ArrayList<String>();
        for (final Sku sku : skus) {
            String item = sku.getTitle();
            if (!TextUtils.isEmpty(sku.getPrice())) {
                item += "  " + sku.getPrice();
            }
            options.add(item);
        }
        String[] items = new String[options.size()];
        options.toArray(items);
        return items;
    }
}
