package com.goodcom.pos.ui;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.goodcom.pos.R;
import com.goodcom.pos.utils.HexUtil;
import com.goodcom.possdk.GcSmartPosHelper;
import com.goodcom.possdk.card.CardInfo;
import com.goodcom.possdk.card.CardSearchType;
import com.goodcom.possdk.card.CardSlot;
import com.goodcom.possdk.card.CardType;
import com.goodcom.possdk.card.OnCardSearchListener;
import com.goodcom.possdk.core.PosResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Level 2 Module: Card Search Activity.
 * Primary Mode: Universal Bitmask search (MAG | ICC | RF).
 * Advanced Mode: Dynamic hardware channel selection via getSupportedCardSearchTypes().
 */
public class CardSearchActivity extends BasePosDemoActivity implements View.OnClickListener {

    private static final int COLOR_DEFAULT_GRAY = Color.parseColor("#888888");
    private static final int COLOR_ACTIVE_DARK = Color.parseColor("#212121");

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    // Primary Bitmask Controls (Single-choice RadioGroup)
    private RadioGroup mRgCardType;
    private Button mBtnStartBitmask;

    // Advanced Hardware Channel Controls
    private TextView mTvModeCountHint;
    private Spinner mSpSearchTypes;
    private Button mBtnStartChannel;

    // Details View
    private TextView mTvDetectedDetails;

    private final List<CardSearchType> mSearchTypeList = new ArrayList<>();
    private ArrayAdapter<CardSearchType> mSpinnerAdapter;

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_card_search;
    }

    @Override
    protected void initViews() {
        // Primary single-choice controls
        mRgCardType = findViewById(R.id.rg_card_type);
        mBtnStartBitmask = findViewById(R.id.btn_start_search_bitmask);
        mBtnStartBitmask.setOnClickListener(this);
        findViewById(R.id.btn_cancel_search).setOnClickListener(this);

        // Hardware channel controls
        mTvModeCountHint = findViewById(R.id.tv_mode_count_hint);
        mSpSearchTypes = findViewById(R.id.sp_card_search_types);
        mBtnStartChannel = findViewById(R.id.btn_start_search_channel);
        mBtnStartChannel.setOnClickListener(this);

        // Details & Logs
        mTvDetectedDetails = findViewById(R.id.tv_detected_details);
        findViewById(R.id.btn_clear_log).setOnClickListener(this);

        // Set default text style
        resetDetectedDetails();

        mSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, mSearchTypeList);
        mSpSearchTypes.setAdapter(mSpinnerAdapter);

        // Load hardware channels if already connected
        if (GcSmartPosHelper.get().isConnected()) {
            loadSupportedSearchTypesAsync();
        }
    }

    @Override
    protected void onConnectionChanged(boolean connected) {
        if (connected) {
            loadSupportedSearchTypesAsync();
        }
    }

    private void resetDetectedDetails() {
        if (mTvDetectedDetails != null) {
            mTvDetectedDetails.setText("Waiting for card swipe / tap / insertion...");
            mTvDetectedDetails.setTextColor(COLOR_DEFAULT_GRAY);
        }
    }

    /**
     * Fetch supported hardware channels safely.
     * Guarantees that existing items are never cleared if already loaded, preserving user's selection.
     */
    private void loadSupportedSearchTypesAsync() {
        // If already populated, do not clear or recreate to avoid resetting user's selected item
        if (!mSearchTypeList.isEmpty()) {
            return;
        }

        if (mTvModeCountHint != null) {
            mTvModeCountHint.setText("Loading hardware channels...");
        }

        new Thread(() -> {
            List<CardSearchType> types = GcSmartPosHelper.get().getSupportedCardSearchTypes(CardSearchActivity.this);
            mMainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (types != null && !types.isEmpty() && mSearchTypeList.isEmpty()) {
                    mSearchTypeList.addAll(types);
                    mSpinnerAdapter.notifyDataSetChanged();
                    mTvModeCountHint.setText("Supported hardware channels (" + types.size() + " modes):");
                    appendLog("Loaded " + types.size() + " hardware channels.");
                } else if (mSearchTypeList.isEmpty()) {
                    mTvModeCountHint.setText("Hardware channels loading or service connecting...");
                }
            });
        }).start();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_start_search_bitmask) {
            doStartSearchBitmask();
        } else if (id == R.id.btn_start_search_channel) {
            doStartSearchChannel();
        } else if (id == R.id.btn_cancel_search) {
            doCancelSearch();
        } else if (id == R.id.btn_clear_log) {
            clearLog();
            resetDetectedDetails();
        }
    }

    /**
     * Primary approach: Standard card search using single-choice card type.
     */
    private void doStartSearchBitmask() {
        final int checkedId = mRgCardType.getCheckedRadioButtonId();
        final int cardMask;
        final String typeDesc;

        if (checkedId == R.id.rb_card_icc) {
            cardMask = CardType.ICC;
            typeDesc = "ICC";
        } else if (checkedId == R.id.rb_card_rf) {
            cardMask = CardType.RF;
            typeDesc = "RF";
        } else {
            // Default: MAG
            cardMask = CardType.MAG;
            typeDesc = "MAG";
        }

        // Always cancel any ongoing search first to avoid conflicting callbacks
        GcSmartPosHelper.get().cancelSearchCard(this);

        appendLog("------------------------------------------------");
        appendLog("[Card Search] Starting: " + typeDesc + " (mask=0x" + Integer.toHexString(cardMask) + ")");
        mTvDetectedDetails.setText("Searching... Please swipe, tap, or insert card.");
        mTvDetectedDetails.setTextColor(COLOR_DEFAULT_GRAY);

        int ret = GcSmartPosHelper.get().searchCard(this, cardMask, 15, mCardSearchListener);
        appendLog("[Bitmask Search] Return Code = " + ret);
    }

    /**
     * Advanced approach: Specific hardware channel selected from getSupportedCardSearchTypes().
     */
    private void doStartSearchChannel() {
        if (mSearchTypeList.isEmpty()) {
            loadSupportedSearchTypesAsync();
            Toast.makeText(this, "Loading hardware channels, please try again in a moment", Toast.LENGTH_SHORT).show();
            return;
        }

        final CardSearchType selected = (CardSearchType) mSpSearchTypes.getSelectedItem();
        if (selected == null) {
            Toast.makeText(this, "Please select a channel", Toast.LENGTH_SHORT).show();
            return;
        }

        // Always cancel any ongoing search first to avoid conflicting callbacks
        GcSmartPosHelper.get().cancelSearchCard(this);

        appendLog("------------------------------------------------");
        appendLog("[Channel Search] Starting: " + selected.getName() + " [key=" + selected.getKey() + "]");
        mTvDetectedDetails.setText("Searching channel " + selected.getName() + "...");
        mTvDetectedDetails.setTextColor(COLOR_DEFAULT_GRAY);

        int ret = GcSmartPosHelper.get().searchCard(this, selected, 15, mCardSearchListener);
        appendLog("[Channel Search] Return Code = " + ret);
    }

    /**
     * Listener implementation that enforces strict execution on UI Main Thread.
     */
    private final OnCardSearchListener mCardSearchListener = new OnCardSearchListener() {
        @Override
        public void onCardFound(CardInfo info) {
            mMainHandler.post(() -> {
                appendLog("================ [CARD DETECTED] ================");
                appendLog(" Card Type    : " + formatCardType(info.getCardType()));
                appendLog(" Card Slot    : " + info.getCardSlot());
                if (info.getCardNo() != null)
                    appendLog(" Card No      : " + info.getCardNo());
                if (info.getExpiredDate() != null)
                    appendLog(" Expire Date  : " + info.getExpiredDate());
                if (info.getServiceCode() != null)
                    appendLog(" Service Code : " + info.getServiceCode());
                if (info.getTrack1() != null)
                    appendLog(" Track1       : " + info.getTrack1());
                if (info.getTrack2() != null)
                    appendLog(" Track2       : " + info.getTrack2());
                if (info.getTrack3() != null)
                    appendLog(" Track3       : " + info.getTrack3());
                if (info.getAtr() != null)
                    appendLog(" ATR          : " + info.getAtr());
                if (info.getUid() != null)
                    appendLog(" UID          : " + HexUtil.bytesToHexString(info.getUid()));
                appendLog(" Result Code  : " + info.getResultCode());
                appendLog("================================================");

                StringBuilder sb = new StringBuilder();
                sb.append("Card Type: ").append(formatCardType(info.getCardType())).append("\n");
                sb.append("Card Slot: ").append(info.getCardSlot()).append("\n");
                if (info.getCardNo() != null)
                    sb.append("Card No: ").append(info.getCardNo()).append("\n");
                if (info.getExpiredDate() != null)
                    sb.append("Expire Date: ").append(info.getExpiredDate()).append("\n");
                if (info.getUid() != null)
                    sb.append("UID: ").append(HexUtil.bytesToHexString(info.getUid())).append("\n");
                if (info.getAtr() != null)
                    sb.append("ATR: ").append(info.getAtr()).append("\n");
                sb.append("Status: Success (Code ").append(info.getResultCode()).append(")");

                mTvDetectedDetails.setText(sb.toString());
                mTvDetectedDetails.setTextColor(COLOR_ACTIVE_DARK);

                Toast.makeText(CardSearchActivity.this, "Card Detected: " + formatCardType(info.getCardType()), Toast.LENGTH_SHORT).show();

                // If IC card or PSAM card, automatically trigger APDU reading
                if (info.getCardType() == CardType.ICC || info.getCardType() == CardType.PSAM1 || info.getCardType() == CardType.PSAM2) {
                    autoReadSmartCardAsync(info);
                }
            });
        }

        @Override
        public void onError(int errorCode, String errorMessage) {
            mMainHandler.post(() -> {
                appendLog("[Card Search] Error: code=" + errorCode + ", msg=" + errorMessage);
                mTvDetectedDetails.setText("Error: code=" + errorCode + "\n" + errorMessage);
                mTvDetectedDetails.setTextColor(COLOR_ACTIVE_DARK);
            });
        }

        @Override
        public void onTimeout() {
            mMainHandler.post(() -> {
                appendLog("[Card Search] Timeout: No card detected within 15 seconds.");
                mTvDetectedDetails.setText("Search Timed Out (no card detected).");
                mTvDetectedDetails.setTextColor(COLOR_ACTIVE_DARK);
            });
        }
    };

    /**
     * Automatic smart card APDU read-out upon card detection.
     */
    private void autoReadSmartCardAsync(CardInfo info) {
        final int slot = (info.getCardType() == CardType.PSAM1) ? CardSlot.PSAM1 :
                (info.getCardType() == CardType.PSAM2 ? CardSlot.PSAM2 : CardSlot.IC_USER);
        final String slotName = (info.getCardType() == CardType.PSAM1) ? "PSAM 1" :
                (info.getCardType() == CardType.PSAM2 ? "PSAM 2" : "User IC (Slot 0)");

        final String sendApdu;
        final String apduDesc;
        if (info.getCardType() == CardType.ICC) {
            // Universal standard PBOC/EMV PSE select command: "1PAY.SYS.DDF01"
            sendApdu = "00A404000E315041592E5359532E444446303100";
            apduDesc = "Select PSE (1PAY.SYS.DDF01)";
        } else {
            // ISO 7816-4 Get Challenge (8 bytes random)
            sendApdu = "0084000008";
            apduDesc = "Get Random (8 bytes)";
        }

        appendLog("[Auto Read] Resetting card on " + slotName + "...");
        long startReset = System.currentTimeMillis();
        int resetRet = GcSmartPosHelper.get().iccReset(CardSearchActivity.this, slot);
        long elapsedReset = System.currentTimeMillis() - startReset;

        if (resetRet != PosResult.SUCCESS) {
            appendLog("[Auto Read] Card reset failed on " + slotName + " (" + elapsedReset + "ms), code=" + resetRet);
            return;
        }

        appendLog("[Auto Read] Reset OK (" + elapsedReset + "ms)");
        appendLog("[Auto Read] Sending APDU [" + apduDesc + "]: " + sendApdu);

        long startApdu = System.currentTimeMillis();
        String apduResp = GcSmartPosHelper.get().iccTransmit(CardSearchActivity.this, slot, sendApdu);
        long elapsedApdu = System.currentTimeMillis() - startApdu;

        if (apduResp != null) {
            appendLog("[Auto Read] APDU Response (" + elapsedApdu + "ms): " + apduResp);
            StringBuilder sb = new StringBuilder();
            sb.append("Card Type: ").append(formatCardType(info.getCardType())).append("\n");
            sb.append("Card Slot: ").append(slotName).append("\n");
            sb.append("APDU Cmd: ").append(sendApdu).append("\n  (").append(apduDesc).append(")\n");
            sb.append("APDU Resp: ").append(apduResp).append("\n");
            sb.append("Status: Auto Read Succeeded");
            mTvDetectedDetails.setText(sb.toString());
        } else {
            appendLog("[Auto Read] APDU Transmit failed on " + slotName + " (" + elapsedApdu + "ms)");
        }

        // Power down card slot after reading
        GcSmartPosHelper.get().iccPowerOff(CardSearchActivity.this, slot);
        appendLog("[Auto Read] Card power down complete on " + slotName);
    }

    private void doCancelSearch() {
        appendLog("[Card Search] Cancelling ongoing search...");
        int ret = GcSmartPosHelper.get().cancelSearchCard(this);
        appendLog("[Card Search] Cancel returned: " + ret);
        mTvDetectedDetails.setText("Search cancelled.");
        mTvDetectedDetails.setTextColor(COLOR_ACTIVE_DARK);
    }

    private String formatCardType(int cardType) {
        switch (cardType) {
            case CardType.MAG:
                return "Magnetic Stripe Card";
            case CardType.ICC:
                return "Smart Card (IC)";
            case CardType.RF:
                return "Contactless Card (NFC/RF)";
            case CardType.PSAM1:
                return "PSAM Card (Slot 1)";
            case CardType.PSAM2:
                return "PSAM Card (Slot 2)";
            case CardType.PSAM3:
                return "PSAM Card (Slot 3)";
            default:
                return "CardType(" + cardType + ")";
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        GcSmartPosHelper.get().cancelSearchCard(this);
    }
}
