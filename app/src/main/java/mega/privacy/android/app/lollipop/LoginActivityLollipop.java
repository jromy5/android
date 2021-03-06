package mega.privacy.android.app.lollipop;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;

import mega.privacy.android.app.BaseActivity;
import mega.privacy.android.app.DatabaseHandler;
import mega.privacy.android.app.EphemeralCredentials;
import mega.privacy.android.app.MegaApplication;
import mega.privacy.android.app.R;
import nz.mega.sdk.MegaApiAndroid;
import nz.mega.sdk.MegaApiJava;
import nz.mega.sdk.MegaChatApiAndroid;
import nz.mega.sdk.MegaContactRequest;
import nz.mega.sdk.MegaError;
import nz.mega.sdk.MegaEvent;
import nz.mega.sdk.MegaGlobalListenerInterface;
import nz.mega.sdk.MegaNode;
import nz.mega.sdk.MegaRequest;
import nz.mega.sdk.MegaRequestListenerInterface;
import nz.mega.sdk.MegaTransfer;
import nz.mega.sdk.MegaUser;
import nz.mega.sdk.MegaUserAlert;

import static mega.privacy.android.app.utils.Constants.*;
import static mega.privacy.android.app.utils.JobUtil.*;
import static mega.privacy.android.app.utils.LogUtil.*;
import static mega.privacy.android.app.utils.Util.*;

public class LoginActivityLollipop extends BaseActivity implements MegaGlobalListenerInterface, MegaRequestListenerInterface {

    float scaleH, scaleW;
    float density;
    DisplayMetrics outMetrics;
    Display display;

    RelativeLayout relativeContainer;

    boolean cancelledConfirmationProcess = false;

    //Fragments
    TourFragmentLollipop tourFragment;
    LoginFragmentLollipop loginFragment;
    ChooseAccountFragmentLollipop chooseAccountFragment;
    CreateAccountFragmentLollipop createAccountFragment;
    ConfirmEmailFragmentLollipop confirmEmailFragment;

    ActionBar aB;
    int visibleFragment;

    static LoginActivityLollipop loginActivity;

    Intent intentReceived = null;

    public String accountBlocked = null;

    DatabaseHandler dbH;

    Handler handler = new Handler();
    private MegaApiAndroid megaApi;
    private MegaApiAndroid megaApiFolder;

    private android.support.v7.app.AlertDialog alertDialogTransferOverquota;

    boolean waitingForConfirmAccount = false;
    String emailTemp = null;
    String passwdTemp = null;
    String sessionTemp = null;
    String firstNameTemp = null;
    String lastNameTemp = null;

    static boolean isBackFromLoginPage;
    static boolean isFetchingNodes;

    private BroadcastReceiver updateMyAccountReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            int actionType;

            if (intent != null){
                actionType = intent.getIntExtra("actionType", -1);

                if(actionType == UPDATE_GET_PRICING){
                    logDebug("BROADCAST TO UPDATE AFTER GET PRICING");
                    //UPGRADE_ACCOUNT_FRAGMENT

                    if(chooseAccountFragment!=null && chooseAccountFragment.isAdded()){
                        chooseAccountFragment.setPricingInfo();
                    }
                }
                else if(actionType == UPDATE_PAYMENT_METHODS){
                    logDebug("BROADCAST TO UPDATE AFTER UPDATE_PAYMENT_METHODS");
                }
            }
        }
    };

    @Override
    protected void onDestroy() {
        logDebug("onDestroy");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateMyAccountReceiver);
        if (megaApi != null) {
            megaApi.removeGlobalListener(this);
            megaApi.removeRequestListener(this);
        }
        super.onDestroy();
    }

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        logDebug("onCreate");
        super.onCreate(savedInstanceState);
        
        loginActivity = this;

        display = getWindowManager().getDefaultDisplay();
        outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);
        density = getResources().getDisplayMetrics().density;

        aB = getSupportActionBar();
        hideAB();

        scaleW = getScaleW(outMetrics, density);
        scaleH = getScaleH(outMetrics, density);

        dbH = DatabaseHandler.getDbHandler(getApplicationContext());
        if (megaApi == null) {
            megaApi = ((MegaApplication) getApplication()).getMegaApi();
        }

        if (megaApiFolder == null) {
            megaApiFolder = ((MegaApplication) getApplication()).getMegaApiFolder();
        }

        megaApi.addGlobalListener(this);
        megaApi.addRequestListener(this);

        setContentView(R.layout.activity_login);
        relativeContainer = (RelativeLayout) findViewById(R.id.relative_container_login);

        intentReceived = getIntent();
        if(savedInstanceState!=null) {
            logDebug("Bundle is NOT NULL");
            visibleFragment = savedInstanceState.getInt("visibleFragment", LOGIN_FRAGMENT);
        }
        else{
            if (intentReceived != null) {
                visibleFragment = intentReceived.getIntExtra("visibleFragment", LOGIN_FRAGMENT);
                logDebug("There is an intent! VisibleFragment: " + visibleFragment);
            } else {
                visibleFragment = LOGIN_FRAGMENT;
            }
        }

        if (dbH.getEphemeral() != null) {
            visibleFragment = CONFIRM_EMAIL_FRAGMENT;

            EphemeralCredentials ephemeralCredentials = dbH.getEphemeral();

            emailTemp = ephemeralCredentials.getEmail();
            passwdTemp = ephemeralCredentials.getPassword();
            sessionTemp = ephemeralCredentials.getSession();
            firstNameTemp = ephemeralCredentials.getFirstName();
            lastNameTemp = ephemeralCredentials.getLastName();

            megaApi.resumeCreateAccount(sessionTemp, this);
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(updateMyAccountReceiver, new IntentFilter(BROADCAST_ACTION_INTENT_UPDATE_ACCOUNT_DETAILS));
        isBackFromLoginPage = false;
        showFragment(visibleFragment);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()){
            case android.R.id.home: {
                switch (visibleFragment) {
                    case LOGIN_FRAGMENT: {
                        if (loginFragment != null && loginFragment.isAdded()) {
//                            loginFragment.returnToLogin();
                            onBackPressed();
                        }
                        break;
                    }
                    case CHOOSE_ACCOUNT_FRAGMENT: {
                        if (chooseAccountFragment != null && chooseAccountFragment.isAdded()) {
                            chooseAccountFragment.onFreeClick(null);
                        }
                        break;
                    }
                }
                break;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    public void showSnackbar(String message) {
        showSnackbar(relativeContainer, message);
    }

    public void showFragment(int visibleFragment) {
        logDebug("visibleFragment: " + visibleFragment);
        this.visibleFragment = visibleFragment;
        switch (visibleFragment) {
            case LOGIN_FRAGMENT: {
                logDebug("Show LOGIN_FRAGMENT");
                if (loginFragment == null) {
                    loginFragment = new LoginFragmentLollipop();
                }
                if ((passwdTemp != null) && (emailTemp != null)) {
                    loginFragment.setEmailTemp(emailTemp);
                    loginFragment.setPasswdTemp(passwdTemp);
                }

                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.fragment_container_login, loginFragment);
                ft.commitNowAllowingStateLoss();

                getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.status_bar_login));

//				getFragmentManager()
//						.beginTransaction()
//						.attach(loginFragment)
//						.commit();
                break;
            }
            case CHOOSE_ACCOUNT_FRAGMENT: {
                logDebug("Show CHOOSE_ACCOUNT_FRAGMENT");

                if (chooseAccountFragment == null) {
                    chooseAccountFragment = new ChooseAccountFragmentLollipop();
                }

                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.fragment_container_login, chooseAccountFragment);
                ft.commitNowAllowingStateLoss();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Window window = this.getWindow();
                    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                    window.setStatusBarColor(ContextCompat.getColor(this, R.color.dark_primary_color));
                }
                break;
            }
            case CREATE_ACCOUNT_FRAGMENT: {
                logDebug("Show CREATE_ACCOUNT_FRAGMENT");
                if (createAccountFragment == null || cancelledConfirmationProcess) {
                    createAccountFragment = new CreateAccountFragmentLollipop();
                    cancelledConfirmationProcess = false;
                }

                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.fragment_container_login, createAccountFragment);
                ft.commitNowAllowingStateLoss();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Window window = this.getWindow();
                    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                    window.setStatusBarColor(ContextCompat.getColor(this, R.color.status_bar_login));
                }
                break;

            }
            case TOUR_FRAGMENT: {
                logDebug("Show TOUR_FRAGMENT");

                if (tourFragment == null) {
                    tourFragment = new TourFragmentLollipop();
                }

                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.fragment_container_login, tourFragment);
                ft.commitNowAllowingStateLoss();
                break;
            }
            case CONFIRM_EMAIL_FRAGMENT: {

                if (confirmEmailFragment == null) {
                    confirmEmailFragment = new ConfirmEmailFragmentLollipop();
                    if ((passwdTemp != null) && (emailTemp != null)) {
                        confirmEmailFragment.setEmailTemp(emailTemp);
                        confirmEmailFragment.setPasswdTemp(passwdTemp);
                        confirmEmailFragment.setFirstNameTemp(firstNameTemp);
//						emailTemp = null;
//						passwdTemp = null;
//						nameTemp = null;
                    }
                } else {
                    if ((passwdTemp != null) && (emailTemp != null)) {
                        confirmEmailFragment.setEmailTemp(emailTemp);
                        confirmEmailFragment.setPasswdTemp(passwdTemp);
                        confirmEmailFragment.setFirstNameTemp(firstNameTemp);
//						emailTemp = null;
//						passwdTemp = null;
//						nameTemp = null;
                    }
                }

                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.fragment_container_login, confirmEmailFragment);
                ft.commitNowAllowingStateLoss();
                FragmentManager fragmentManager = getSupportFragmentManager();
                fragmentManager.executePendingTransactions();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Window window = this.getWindow();
                    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                    window.setStatusBarColor(ContextCompat.getColor(this, R.color.status_bar_login));
                }

                break;
            }
        }

        if( ((MegaApplication) getApplication()).isEsid()){
            showAlertLoggedOut();
        }
    }

    public void showAlertIncorrectRK() {
        logDebug("showAlertIncorrectRK");
        final android.support.v7.app.AlertDialog.Builder dialogBuilder = new android.support.v7.app.AlertDialog.Builder(this);

        dialogBuilder.setTitle(getString(R.string.incorrect_MK_title));
        dialogBuilder.setMessage(getString(R.string.incorrect_MK));
        dialogBuilder.setCancelable(false);

        dialogBuilder.setPositiveButton(getString(R.string.cam_sync_ok), new android.content.DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        android.support.v7.app.AlertDialog alert = dialogBuilder.create();
        alert.show();
    }

    public void showAlertLoggedOut() {
        logDebug("showAlertLoggedOut");
        ((MegaApplication) getApplication()).setEsid(false);
        if(!isFinishing()){
            final android.support.v7.app.AlertDialog.Builder dialogBuilder = new android.support.v7.app.AlertDialog.Builder(this);

            dialogBuilder.setTitle(getString(R.string.title_alert_logged_out));
            dialogBuilder.setMessage(getString(R.string.error_server_expired_session));

            dialogBuilder.setPositiveButton(getString(R.string.cam_sync_ok), new android.content.DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            android.support.v7.app.AlertDialog alert = dialogBuilder.create();
            alert.show();
        }
    }

    public void showTransferOverquotaDialog() {
        logDebug("showTransferOverquotaDialog");

        boolean show = true;

        if(alertDialogTransferOverquota!=null){
            if(alertDialogTransferOverquota.isShowing()){
                logDebug("Change show to false");
                show = false;
            }
        }

        if(show){
            android.support.v7.app.AlertDialog.Builder dialogBuilder = new android.support.v7.app.AlertDialog.Builder(this);

            LayoutInflater inflater = this.getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.transfer_overquota_layout_not_logged, null);
            dialogBuilder.setView(dialogView);

            TextView title = (TextView) dialogView.findViewById(R.id.not_logged_transfer_overquota_title);
            title.setText(getString(R.string.title_depleted_transfer_overquota));

            ImageView icon = (ImageView) dialogView.findViewById(R.id.not_logged_image_transfer_overquota);
            icon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.transfer_quota_empty));

            TextView text = (TextView) dialogView.findViewById(R.id.not_logged_text_transfer_overquota);
            text.setText(getString(R.string.text_depleted_transfer_overquota));

            Button continueButton = (Button) dialogView.findViewById(R.id.not_logged_transfer_overquota_button_dissmiss);
            continueButton.setText(getString(R.string.login_text));

            Button paymentButton = (Button) dialogView.findViewById(R.id.not_logged_transfer_overquota_button_payment);
            paymentButton.setText(getString(R.string.continue_without_account_transfer_overquota));

            Button cancelButton = (Button) dialogView.findViewById(R.id.not_logged_transfer_overquota_button_cancel);
            cancelButton.setText(getString(R.string.menu_cancel_all_transfers));

            alertDialogTransferOverquota = dialogBuilder.create();

            continueButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    alertDialogTransferOverquota.dismiss();
                }

            });

            paymentButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    alertDialogTransferOverquota.dismiss();
                }

            });

            cancelButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    alertDialogTransferOverquota.dismiss();
                    showConfirmationCancelAllTransfers();

                }

            });

            alertDialogTransferOverquota.setCancelable(false);
            alertDialogTransferOverquota.setCanceledOnTouchOutside(false);
            alertDialogTransferOverquota.show();
        }
    }

    public void startCameraUploadService(boolean firstTimeCam, int time) {
        logDebug("firstTimeCam: " + firstNameTemp + "time: " + time);
        if (firstTimeCam) {
            Intent intent = new Intent(this, ManagerActivityLollipop.class);
            intent.putExtra("firstLogin", true);
            startActivity(intent);
            finish();
        } else {
            logDebug("Start the Camera Uploads service");
            handler.postDelayed(new Runnable() {

                @Override
                public void run() {
                    logDebug("Now I start the service");
                    scheduleCameraUploadJob(LoginActivityLollipop.this);
                }
            }, time);
        }
    }

    public void showConfirmationCancelAllTransfers() {
        logDebug("showConfirmationCancelAllTransfers");

        setIntent(null);
        //Show confirmation message
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        logDebug("Pressed button positive to cancel transfer");
                        if (megaApi != null) {
                            megaApi.cancelTransfers(MegaTransfer.TYPE_DOWNLOAD);
                            if (megaApiFolder != null) {
                                megaApiFolder.cancelTransfers(MegaTransfer.TYPE_DOWNLOAD);
                            }
                        } else {
                            logWarning("megaAPI is null");
                            if (megaApiFolder != null) {
                                megaApiFolder.cancelTransfers(MegaTransfer.TYPE_DOWNLOAD);
                            }
                        }

                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        break;
                }
            }
        };

        android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
//		builder.setTitle(getResources().getString(R.string.cancel_transfer_title));

        builder.setMessage(getResources().getString(R.string.cancel_all_transfer_confirmation));
        builder.setPositiveButton(R.string.context_delete, dialogClickListener);
        builder.setNegativeButton(R.string.general_cancel, dialogClickListener);

        builder.show();
    }

    @Override
    public void onBackPressed() {
        logDebug("onBackPressed");
        retryConnectionsAndSignalPresence();

        int valueReturn = -1;

        switch (visibleFragment) {
            case LOGIN_FRAGMENT: {
                if (loginFragment != null) {
                    valueReturn = loginFragment.onBackPressed();
                }
                break;
            }
            case CREATE_ACCOUNT_FRAGMENT: {
                showFragment(TOUR_FRAGMENT);
                break;
            }
            case TOUR_FRAGMENT: {
                valueReturn = 0;
                break;
            }
            case CONFIRM_EMAIL_FRAGMENT: {
                valueReturn = 0;
                break;
            }
            case CHOOSE_ACCOUNT_FRAGMENT: {
                if (chooseAccountFragment != null && chooseAccountFragment.isAdded()) {
                    chooseAccountFragment.onFreeClick(null);
                }
                break;
            }
        }

        if (valueReturn == 0) {
            super.onBackPressed();
        }
    }

    @Override
    public void onResume() {
        logDebug("onResume");
        super.onResume();
        setAppFontSize(this);
        Intent intent = getIntent();

        if (intent != null) {
            if (intent.getAction() != null) {
                if (intent.getAction().equals(ACTION_CANCEL_CAM_SYNC)) {
                    logDebug("ACTION_CANCEL_CAM_SYNC");
                    String title = getString(R.string.cam_sync_syncing);
                    String text = getString(R.string.cam_sync_cancel_sync);
                    AlertDialog.Builder builder = getCustomAlertBuilder(this, title, text, null);
                    builder.setPositiveButton(getString(R.string.cam_sync_stop),
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    stopRunningCameraUploadService(LoginActivityLollipop.this);
                                    dbH.setCamSyncEnabled(false);
                                }
                            });
                    builder.setNegativeButton(getString(R.string.general_cancel), null);
                    final AlertDialog dialog = builder.create();
                    try {
                        dialog.show();
                    } catch (Exception ex) {
                        logError("Exception", ex);
                    }
                }
                else if (intent.getAction().equals(ACTION_CANCEL_DOWNLOAD)) {
                    showConfirmationCancelAllTransfers();
                }
                else if (intent.getAction().equals(ACTION_OVERQUOTA_TRANSFER)) {
                    showTransferOverquotaDialog();

                }
                intent.setAction(null);
            }
        }

        setIntent(null);
    }

    boolean loggerPermissionKarere = false;
    boolean loggerPermissionSDK = false;

    public void showConfirmationEnableLogsKarere() {
        logDebug("showConfirmationEnableLogsKarere");

        if (loginFragment != null) {
            loginFragment.numberOfClicksKarere = 0;
        }

        loginActivity = this;

        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:{
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            boolean hasStoragePermission = (ContextCompat.checkSelfPermission(loginActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
                            if (!hasStoragePermission) {
                                loggerPermissionKarere = true;
                                ActivityCompat.requestPermissions(loginActivity,
                                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                        REQUEST_WRITE_STORAGE);
                            } else {
                                enableLogsKarere();
                            }
                        } else {
                            enableLogsKarere();
                        }
                        break;
                    }

                    case DialogInterface.BUTTON_NEGATIVE: {
                        break;
                    }
                }
            }
        };

        android.support.v7.app.AlertDialog.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            builder = new android.support.v7.app.AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        } else {
            builder = new android.support.v7.app.AlertDialog.Builder(this);
        }

        builder.setMessage(R.string.enable_log_text_dialog).setPositiveButton(R.string.general_enable, dialogClickListener)
                .setNegativeButton(R.string.general_cancel, dialogClickListener).show().setCanceledOnTouchOutside(false);
    }

    public void showConfirmationEnableLogsSDK() {
        logDebug("showConfirmationEnableLogsSDK");

        if (loginFragment != null) {
            loginFragment.numberOfClicksSDK = 0;
        }

        loginActivity = this;

        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:{
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            boolean hasStoragePermission = (ContextCompat.checkSelfPermission(loginActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
                            if (!hasStoragePermission) {
                                loggerPermissionSDK = true;
                                ActivityCompat.requestPermissions(loginActivity,
                                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                        REQUEST_WRITE_STORAGE);
                            } else {
                                enableLogsSDK();
                            }
                        } else {
                            enableLogsSDK();
                        }
                        break;
                    }

                    case DialogInterface.BUTTON_NEGATIVE: {
                        break;
                    }
                }
            }
        };

        android.support.v7.app.AlertDialog.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            builder = new android.support.v7.app.AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        } else {
            builder = new android.support.v7.app.AlertDialog.Builder(this);
        }

        builder.setMessage(R.string.enable_log_text_dialog).setPositiveButton(R.string.general_enable, dialogClickListener)
                .setNegativeButton(R.string.general_cancel, dialogClickListener).show().setCanceledOnTouchOutside(false);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        logDebug("onRequestPermissionsResult");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode){
            case REQUEST_WRITE_STORAGE:{
                if (loggerPermissionKarere){
                    loggerPermissionKarere = false;
                    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                        enableLogsKarere();
                    }
                }
                else if (loggerPermissionSDK){
                    loggerPermissionSDK = false;
                    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                        enableLogsSDK();
                    }
                }
            }
        }
    }

    public void enableLogsSDK() {
        logDebug("enableLogsSDK");

        dbH.setFileLoggerSDK(true);
        setFileLoggerSDK(true);
        MegaApiAndroid.setLogLevel(MegaApiAndroid.LOG_LEVEL_MAX);
        showSnackbar(getString(R.string.settings_enable_logs));
        logDebug("App Version: " + getVersion(this));
    }

    public void enableLogsKarere() {
        logDebug("enableLogsKarere");

        dbH.setFileLoggerKarere(true);
        setFileLoggerKarere(true);
        MegaChatApiAndroid.setLogLevel(MegaChatApiAndroid.LOG_LEVEL_MAX);
        showSnackbar(getString(R.string.settings_enable_logs));
        logDebug("App Version: " + getVersion(this));
    }

    public void setWaitingForConfirmAccount(boolean waitingForConfirmAccount) {
        this.waitingForConfirmAccount = waitingForConfirmAccount;
    }

    public boolean getWaitingForConfirmAccount() {
        return this.waitingForConfirmAccount;
    }

    public void setFirstNameTemp(String firstNameTemp) {
        this.firstNameTemp = firstNameTemp;
    }

    public void setLastNameTemp(String lastNameTemp) {
        this.lastNameTemp = lastNameTemp;
    }

    public String getFirstNameTemp() {
        return this.firstNameTemp;
    }

    public void setPasswdTemp(String passwdTemp) {
        this.passwdTemp = passwdTemp;
    }

    public String getPasswdTemp() {
        return this.passwdTemp;
    }

    public void setEmailTemp(String emailTemp) {
        this.emailTemp = emailTemp;
        if (dbH != null) {
            if (dbH.getEphemeral() != null) {
                EphemeralCredentials ephemeralCredentials = dbH.getEphemeral();
                ephemeralCredentials.setEmail(emailTemp);
                dbH.clearEphemeral();
                dbH.saveEphemeral(ephemeralCredentials);
            }
        }
    }

    public String getEmailTemp() {
        return this.emailTemp;
    }


//	public void onNewIntent(Intent intent){
//		if (intent != null && ACTION_CONFIRM.equals(intent.getAction())) {
//			loginFragment.handleConfirmationIntent(intent);
//		}
//	}

    @Override
    public void onUsersUpdate(MegaApiJava api, ArrayList<MegaUser> users) {

    }

    @Override
    public void onUserAlertsUpdate(MegaApiJava api, ArrayList<MegaUserAlert> userAlerts) {
        logDebug("onUserAlertsUpdate");
    }

    @Override
    public void onNodesUpdate(MegaApiJava api, ArrayList<MegaNode> nodeList) {

    }

    @Override
    public void onReloadNeeded(MegaApiJava api) {
    }

    @Override
    public void onAccountUpdate(MegaApiJava api) {
        logDebug("onAccountUpdate");

        if (waitingForConfirmAccount) {
            waitingForConfirmAccount = false;
            visibleFragment = LOGIN_FRAGMENT;
            showFragment(visibleFragment);
        }
    }

    @Override
    public void onContactRequestsUpdate(MegaApiJava api, ArrayList<MegaContactRequest> requests) {
    }


    @Override
    public void onEvent(MegaApiJava api, MegaEvent event) {
        logDebug("onEvent");
        if(event.getType()==MegaEvent.EVENT_ACCOUNT_BLOCKED){
            logDebug("Event received: " + event.getText() + "_" + event.getNumber());
            if(event.getNumber()==200){
                accountBlocked = getString(R.string.account_suspended_multiple_breaches_ToS);
            }
            else if(event.getNumber()==300){
                accountBlocked = getString(R.string.account_suspended_breache_ToS);
            }
        }
    }

    @Override
    public void onRequestStart(MegaApiJava api, MegaRequest request) {
        logDebug("onRequestStart - " + request.getRequestString());
    }

    @Override
    public void onRequestUpdate(MegaApiJava api, MegaRequest request) {
        logDebug("onRequestUpdate - " + request.getRequestString());
    }

    @Override
    public void onRequestFinish(MegaApiJava api, MegaRequest request, MegaError e) {
        logDebug("onRequestFinish - " + request.getRequestString() + "_" + e.getErrorCode());

        if(request.getType() == MegaRequest.TYPE_LOGOUT){

            if(accountBlocked!=null){
                showSnackbar(accountBlocked);
            }
            accountBlocked=null;

        }
        else if (request.getType() == MegaRequest.TYPE_CREATE_ACCOUNT){
            try {
                if (request.getParamType() == 1) {
                    if (e.getErrorCode() == MegaError.API_OK) {
                        waitingForConfirmAccount = true;
                        visibleFragment = CONFIRM_EMAIL_FRAGMENT;
                        showFragment(visibleFragment);

                    } else {
                        cancelConfirmationAccount();
                    }
                }
            }
            catch (Exception exc){
                logError("Exception", exc);
            }
        }
    }

    public void cancelConfirmationAccount(){
        logDebug("cancelConfirmationAccount");
        dbH.clearEphemeral();
        dbH.clearCredentials();
        cancelledConfirmationProcess = true;
        waitingForConfirmAccount = false;
        passwdTemp = null;
        emailTemp = null;
        visibleFragment = TOUR_FRAGMENT;
        showFragment(visibleFragment);
    }

    @Override
    public void onRequestTemporaryError(MegaApiJava api, MegaRequest request, MegaError e) {
        logWarning("onRequestTemporaryError - " + request.getRequestString());
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        logDebug("onSaveInstanceState");

        super.onSaveInstanceState(outState);

        outState.putInt("visibleFragment", visibleFragment);
    }

    @Override
    protected void onPause() {
        logDebug("onPause");
        super.onPause();
    }

    public void showAB(Toolbar tB){
        setSupportActionBar(tB);
        aB = getSupportActionBar();
        aB.show();
        aB.setHomeButtonEnabled(true);
        aB.setDisplayHomeAsUpEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            if (visibleFragment == LOGIN_FRAGMENT) {
                window.setStatusBarColor(ContextCompat.getColor(this, R.color.dark_primary_color_secondary));
            }
        }
    }

    public void hideAB(){
        if (aB != null){
            aB.hide();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.status_bar_login));
        }
    }
}
