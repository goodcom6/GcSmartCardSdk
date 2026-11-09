package com.goodcom.pos.ui;

import android.content.Intent;
import android.view.View;

import com.goodcom.pos.R;

/**
 * Level 1: Card Reader module. Entry to Card Search and Smart Card / PSAM.
 */
public class CardReaderActivity extends BasePosDemoActivity implements View.OnClickListener {

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_card_reader;
    }

    @Override
    protected void initViews() {
        findViewById(R.id.card_menu_search).setOnClickListener(this);
        findViewById(R.id.card_menu_icc_psam).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.card_menu_search) {
            startActivity(new Intent(this, CardSearchActivity.class));
        } else if (id == R.id.card_menu_icc_psam) {
            startActivity(new Intent(this, IccPsamActivity.class));
        }
    }
}
