package com.zegoggles.smssync.activity.donation;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.Dialogs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.zegoggles.smssync.activity.donation.DonationActivity.DEBUG_IAB;

public class DonationListFragment extends Dialogs.BaseFragment {
    static final String SKUS = "skus";
    private SkuSelectionListener listener;

    interface SkuSelectionListener {
        void selectedSku(String sku);
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

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final ArrayList<Sku> skus = getArguments().getParcelableArrayList(SKUS);
        return new AlertDialog.Builder(getContext())
                .setTitle(R.string.ui_dialog_donate_message)
                .setItems(getOptions(skus), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String selectedSku;
                        if (DEBUG_IAB) {
                            if (which < Sku.Test.SKUS.length) {
                                selectedSku = Sku.Test.SKUS[which].getSku();
                            } else {
                                selectedSku = skus.get(which - Sku.Test.SKUS.length).getSku();
                            }
                        } else {
                            selectedSku = skus.get(which).getSku();
                        }
                        listener.selectedSku(selectedSku);
                    }
                }).setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        getActivity().finish();
                    }
                }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        getActivity().finish();
                    }
                })
                .create();
    }

    private CharSequence[] getOptions(List<Sku> skus) {
        final List<Sku> sortedSkus = new ArrayList<Sku>(skus);
        Collections.sort(sortedSkus);
        List<String> options = new ArrayList<String>();
        if (DEBUG_IAB) {
            for (Sku testSku : Sku.Test.SKUS) {
                options.add(testSku.getDescription());
            }
        }
        for (final Sku sku : sortedSkus) {
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
