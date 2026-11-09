package com.goodcom.pos.ui;

import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.goodcom.pos.R;
import com.goodcom.possdk.GcSmartPosHelper;
import com.goodcom.possdk.card.CardSlot;
import com.goodcom.possdk.core.PosResult;

/**
 * Level 2 Module: Smart Card & PSAM (ICC) Activity.
 * Provides direct contact smart card and PSAM slot operations.
 * Non-blocking: SDK operations are invoked directly on the UI thread without ExecutorService.
 */
public class IccPsamActivity extends BasePosDemoActivity implements View.OnClickListener {

    private RadioGroup mRgSlot;
    private EditText mEtApdu;

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_icc_psam;
    }

    @Override
    protected void initViews() {
        mRgSlot = findViewById(R.id.rg_card_slot);
        mEtApdu = findViewById(R.id.et_apdu);

        // Slot Control Buttons
        findViewById(R.id.btn_check_card).setOnClickListener(this);
        findViewById(R.id.btn_power_on).setOnClickListener(this);
        findViewById(R.id.btn_power_off).setOnClickListener(this);

        // APDU & Read Card Buttons
        findViewById(R.id.btn_apdu_random).setOnClickListener(this);
        findViewById(R.id.btn_apdu_select_mf).setOnClickListener(this);
        findViewById(R.id.btn_read_card).setOnClickListener(this);

        findViewById(R.id.btn_clear_log).setOnClickListener(this);
    }

    private int getSelectedSlot() {
        int checkedId = mRgSlot.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_psam2) {
            return CardSlot.PSAM2;
        } else if (checkedId == R.id.rb_user_ic) {
            return CardSlot.IC_USER;
        } else {
            return CardSlot.PSAM1;
        }
    }

    private String getSlotName(int slot) {
        switch (slot) {
            case CardSlot.PSAM1:
                return "PSAM 1";
            case CardSlot.PSAM2:
                return "PSAM 2";
            case CardSlot.IC_USER:
                return "User IC";
            default:
                return "Slot(" + slot + ")";
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_check_card) {
            doCheckCard();
        } else if (id == R.id.btn_power_on) {
            doPowerOn();
        } else if (id == R.id.btn_power_off) {
            doPowerOff();
        } else if (id == R.id.btn_apdu_random) {
            mEtApdu.setText("0084000008");
            appendLog("[APDU Template] Set ISO 7816-4 'Get Challenge' command: 0084000008. Tap 'Read Card' to send.");
        } else if (id == R.id.btn_apdu_select_mf) {
            mEtApdu.setText("00A40000023F00");
            appendLog("[APDU Template] Set ISO 7816-4 'Select Master File' command: 00A40000023F00. Tap 'Read Card' to send.");
        } else if (id == R.id.btn_read_card) {
            doReadCard();
        } else if (id == R.id.btn_clear_log) {
            clearLog();
        }
    }

    // ==================== Slot Operations ====================

    private void doCheckCard() {
        final int slot = getSelectedSlot();
        appendLog("[Check Card] Slot=" + getSlotName(slot) + " checking status...");

        // Direct non-blocking call
        boolean present = GcSmartPosHelper.get().iccIsCardPresent(this, slot);
        appendLog("[Check Card] Slot=" + getSlotName(slot) + " -> " + (present ? "PRESENT (Card In)" : "ABSENT (No Card)"));
    }

    private void doPowerOn() {
        final int slot = getSelectedSlot();
        appendLog("[Power On / Reset] Slot=" + getSlotName(slot) + " resetting card...");

        long start = System.currentTimeMillis();
        int ret = GcSmartPosHelper.get().iccReset(this, slot);
        long elapsed = System.currentTimeMillis() - start;

        if (ret == PosResult.SUCCESS) {
            appendLog("[Power On / Reset] Slot=" + getSlotName(slot) + " SUCCESS (" + elapsed + "ms)");
        } else {
            appendLog("[Power On / Reset] Slot=" + getSlotName(slot) + " FAILED (" + elapsed + "ms), code=" + ret);
        }
    }

    private void doPowerOff() {
        final int slot = getSelectedSlot();
        appendLog("[Power Off] Slot=" + getSlotName(slot) + " powering off...");

        int ret = GcSmartPosHelper.get().iccPowerOff(this, slot);
        appendLog("[Power Off] Slot=" + getSlotName(slot) + " result: " + ret + (ret == 0 ? " (SUCCESS)" : " (FAILED)"));
    }

    // ==================== Read Card (Full APDU Lifecycle) ====================

    private void doReadCard() {
        final int slot = getSelectedSlot();
        final String apduHex = mEtApdu.getText().toString().trim();

        if (TextUtils.isEmpty(apduHex)) {
            Toast.makeText(this, "Please enter APDU command", Toast.LENGTH_SHORT).show();
            return;
        }

        appendLog("------------------------------------------------");
        appendLog("[Read Card] Starting operation on " + getSlotName(slot) + "...");

        // 1. Reset card status (returns result code, perfectly matching ZCS & P3000)
        long startReset = System.currentTimeMillis();
        int resetRet = GcSmartPosHelper.get().iccReset(this, slot);
        long elapsedReset = System.currentTimeMillis() - startReset;

        if (resetRet != PosResult.SUCCESS) {
            appendLog("[Read Card] Step 1 Reset FAILED (" + elapsedReset + "ms), code=" + resetRet);
            Toast.makeText(this, "Card reset failed on " + getSlotName(slot) + " (code: " + resetRet + ")", Toast.LENGTH_SHORT).show();
            return;
        }
        appendLog("[Read Card] Step 1 Reset OK (" + elapsedReset + "ms)");

        // 2. Transmit APDU command
        appendLog("[Read Card] Step 2 Sending APDU: " + apduHex);
        long startApdu = System.currentTimeMillis();
        String response = GcSmartPosHelper.get().iccTransmit(this, slot, apduHex);
        long elapsedApdu = System.currentTimeMillis() - startApdu;

        if (response != null) {
            appendLog("[Read Card] Step 2 Response (" + elapsedApdu + "ms): " + response);
            Toast.makeText(this, "Response: " + response, Toast.LENGTH_SHORT).show();
        } else {
            appendLog("[Read Card] Step 2 Transmit FAILED (" + elapsedApdu + "ms)");
        }

        // 3. Power Off card
        int ret = GcSmartPosHelper.get().iccPowerOff(this, slot);
        appendLog("[Read Card] Step 3 Power Off result: " + ret + (ret == 0 ? " (SUCCESS)" : " (FAILED)"));
        appendLog("[Read Card] Completed.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
