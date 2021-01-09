package zapsolutions.zap;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;

import java.io.IOException;
import java.security.GeneralSecurityException;

import zapsolutions.zap.baseClasses.App;
import zapsolutions.zap.baseClasses.BaseAppCompatActivity;
import zapsolutions.zap.connection.manageWalletConfigs.Cryptography;
import zapsolutions.zap.connection.manageWalletConfigs.WalletConfigsManager;
import zapsolutions.zap.setup.ConnectRemoteNodeActivity;
import zapsolutions.zap.util.NfcUtil;
import zapsolutions.zap.util.PinScreenUtil;
import zapsolutions.zap.util.PrefsUtil;
import zapsolutions.zap.util.RefConstants;
import zapsolutions.zap.util.UriUtil;
import zapsolutions.zap.util.ZapLog;

public class LandingActivity extends BaseAppCompatActivity {

    private static final String LOG_TAG = LandingActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Save data when App was started with a task.

        // Zap was started from an URI link.
        Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            App.getAppContext().setUriSchemeData(uri.toString());
            ZapLog.d(LOG_TAG, "URI was detected: " + uri.toString());
            if (!WalletConfigsManager.getInstance().hasAnyConfigs() && UriUtil.isLNDConnectUri(App.getAppContext().getUriSchemeData())) {
                setupWalletFromUri();
                return;
            }
        }

        // Zap was started using NFC.
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {

            NfcUtil.readTag(LandingActivity.this, intent, payload -> App.getAppContext().setUriSchemeData(payload));
            if (!WalletConfigsManager.getInstance().hasAnyConfigs() && UriUtil.isLNDConnectUri(App.getAppContext().getUriSchemeData())) {
                setupWalletFromUri();
                return;
            }
        }


        // support for clearing shared preferences, on breaking changes
        if (PrefsUtil.getPrefs().contains(PrefsUtil.SETTINGS_VERSION)) {
            int ver = PrefsUtil.getPrefs().getInt(PrefsUtil.SETTINGS_VERSION, RefConstants.CURRENT_SETTINGS_VERSION);
            if (ver < RefConstants.CURRENT_SETTINGS_VERSION) {
                if (ver == 18) {
                    transferEncryptedData();
                } else {
                    resetApp();
                }
            } else {
                enterWallet();
            }
        } else {
            // Make sure settings get reset for versions that don't expose settings version
            resetApp();
        }
    }

    private void resetApp() {
        if (WalletConfigsManager.getInstance().hasAnyConfigs()) {
            // Reset settings
            PrefsUtil.edit().clear().commit();

            new AlertDialog.Builder(LandingActivity.this)
                    .setTitle(R.string.app_reset_title)
                    .setMessage(R.string.app_reset_message)
                    .setCancelable(true)
                    .setOnCancelListener(dialogInterface -> enterWallet())
                    .setPositiveButton(R.string.ok, (dialog, whichButton) -> enterWallet())
                    .show();
        } else {
            enterWallet();
        }
    }

    private void transferEncryptedData() {

        // Transfer random source for PIN salt:
        String encryptedRandomSource = PrefsUtil.getPrefs().getString(PrefsUtil.RANDOM_SOURCE, "");
        try {
            if (encryptedRandomSource != null && !encryptedRandomSource.equals("")) {
                String decryptedRandomSource = new Cryptography(App.getAppContext()).decryptData(encryptedRandomSource);
                PrefsUtil.editEncryptedPrefs().putString(PrefsUtil.RANDOM_SOURCE, decryptedRandomSource).commit();
                PrefsUtil.edit().remove(PrefsUtil.RANDOM_SOURCE).commit();
            }
        } catch (Exception e) {
            e.printStackTrace();
            resetApp();
            return;
        }

        // Transfer PIN hash:
        if (PrefsUtil.getPrefs().contains(PrefsUtil.PIN_HASH)) {
            String pinHash = PrefsUtil.getPrefs().getString(PrefsUtil.PIN_HASH, "");
            try {
                PrefsUtil.editEncryptedPrefs().putString(PrefsUtil.PIN_HASH, pinHash).commit();
                PrefsUtil.edit().remove(PrefsUtil.PIN_HASH).commit();
            } catch (GeneralSecurityException | IOException e) {
                e.printStackTrace();
                resetApp();
                return;
            }
        }

        // Transfer Wallet configs:
        String encryptedWallets = PrefsUtil.getPrefs().getString(PrefsUtil.WALLET_CONFIGS, "");
        String decryptedWallets = null;
        try {
            decryptedWallets = new Cryptography(App.getAppContext()).decryptData(encryptedWallets);
            PrefsUtil.editEncryptedPrefs().putString(PrefsUtil.WALLET_CONFIGS, decryptedWallets).commit();
            PrefsUtil.edit().remove(PrefsUtil.WALLET_CONFIGS).commit();
        } catch (Exception e) {
            e.printStackTrace();
            resetApp();
            return;
        }

        enterWallet();
    }

    private void enterWallet() {

        // Set new settings version
        PrefsUtil.edit().putInt(PrefsUtil.SETTINGS_VERSION, RefConstants.CURRENT_SETTINGS_VERSION).commit();

        if (WalletConfigsManager.getInstance().hasAnyConfigs()) {
            PinScreenUtil.askForAccess(this, () -> {
                Intent homeIntent = new Intent(this, HomeActivity.class);
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);

                // FinishAffinity is needed here as this forces the on destroy events from previous activities to be executed before continuing.
                finishAffinity();

                startActivity(homeIntent);
            });

        } else {
            // Clear connection data if something is there
            PrefsUtil.edit().remove(PrefsUtil.WALLET_CONFIGS).commit();

            Intent homeIntent = new Intent(this, HomeActivity.class);
            startActivity(homeIntent);
        }
    }

    private void setupWalletFromUri() {
        Intent connectIntent = new Intent(this, ConnectRemoteNodeActivity.class);
        connectIntent.putExtra(ConnectRemoteNodeActivity.EXTRA_STARTED_FROM_URI, true);
        connectIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(connectIntent);
    }
}
