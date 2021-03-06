package com.irinabelenki.smstoemail;

import android.accounts.AccountManager;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.model.Message;

import javax.mail.internet.MimeMessage;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "SMS_TO_EMAIL";
    private TextView outputTextView;
    private CheckBox redirectCheckBox;
    private Button setupAccountButton;
    private GoogleAccountCredential credential;

    private SharedPreferences settings;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        outputTextView = (TextView) findViewById(R.id.output_textview);

        redirectCheckBox = (CheckBox) findViewById(R.id.redirect_checkbox);
        settings = this.getSharedPreferences(SmsToEmailApplication.SHARED_PREF_NAME, Context.MODE_PRIVATE);
        String accountName = settings.getString(SmsToEmailApplication.PREF_ACCOUNT_NAME, null);
        Boolean redirect = settings.getBoolean(SmsToEmailApplication.PREF_REDIRECT_TO_EMAIL, false);
        redirectCheckBox.setChecked(redirect);
        redirectCheckBox.setEnabled(accountName != null);

        redirectCheckBox.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putBoolean(SmsToEmailApplication.PREF_REDIRECT_TO_EMAIL, buttonView.isChecked());
                        editor.putString(SmsToEmailApplication.PREF_ERROR_MSG, null);
                        editor.apply();
                        outputTextView.setText(null);
                    }
                }
        );

        setupAccountButton = (Button) findViewById(R.id.setup_account_button);
        setupAccountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseAccount();
            }
        });

        Intent intent = getIntent();
        Exception lastError = (Exception)intent.getSerializableExtra("exception");
        if (lastError != null) {
            Log.i(TAG, lastError.toString());
        }

        credential = ((SmsToEmailApplication) getApplicationContext()).getGoogleAccountCredential();
    }

    protected void onResume() {
        super.onResume();
        if (isGooglePlayServicesAvailable()) {
            if (credential.getSelectedAccountName() == null) {
                chooseAccount();
            }
        } else {
            outputTextView.setText("Google Play Services required: " +
                    "after installing, close and relaunch this app.");
        }
        String accountName = settings.getString(SmsToEmailApplication.PREF_ACCOUNT_NAME, null);
        String errorMsg = settings.getString(SmsToEmailApplication.PREF_ERROR_MSG, null);
        redirectCheckBox.setEnabled(accountName != null);
        redirectCheckBox.setText("Redirect SMS to " + accountName);
        outputTextView.setText(errorMsg);
    }

    //@Override
    //public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
    //    getMenuInflater().inflate(R.menu.menu_main, menu);
    //    return true;
    //}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    isGooglePlayServicesAvailable();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        credential.setSelectedAccountName(accountName);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(SmsToEmailApplication.PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        //outputTextView.setText("Account was chosen.");
                        String message = "set account name: " + accountName;
                        Log.i(TAG, message);
                        //Toast.makeText(this, message, Toast.LENGTH_LONG).show();

                        sendConfirmationMail(accountName);
                    }
                } else if (resultCode == RESULT_CANCELED) {
                    //outputTextView.setText("Account not changed.");
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode != RESULT_OK) {
                    String message = "REQUEST_AUTHORIZATION is not OK";
                    Log.i(TAG, message);
                    //Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    chooseAccount();
                } else {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    String message = "REQUEST_AUTHORIZATION OK, account name: " + accountName;
                    Log.i(TAG, message);
                    //Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    sendConfirmationMail(accountName);
                }
                break;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void sendConfirmationMail(String accountName) {
        GoogleAccountCredential credential = ((SmsToEmailApplication) this.getApplicationContext()).getGoogleAccountCredential();
        String[] params = {
                accountName,
                "From: SmsToGmail application",
                "Your SmsToGmail default account was successfully set"};

        SharedPreferences.Editor editor = settings.edit();
        editor.putString(SmsToEmailApplication.PREF_ERROR_MSG, null);
        editor.apply();
        outputTextView.setText(null);

        new SendEmailTask(this, credential).execute(params);
    }

    /**
     * Starts an activity in Google Play Services so the user can pick an
     * account.
     */
    private void chooseAccount() {
        startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
    }

    /**
     * Check that Google Play services APK is installed and up to date. Will
     * launch an error dialog for the user to update Google Play Services if
     * possible.
     *
     * @return true if Google Play Services is available and up to
     * date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        final int connectionStatusCode =
                GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (GooglePlayServicesUtil.isUserRecoverableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
            return false;
        } else if (connectionStatusCode != ConnectionResult.SUCCESS) {
            return false;
        }
        return true;
    }

    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     *
     * @param connectionStatusCode code describing the presence (or lack of)
     *                             Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        Dialog dialog = GooglePlayServicesUtil.getErrorDialog(
                connectionStatusCode,
                MainActivity.this,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    private class SendEmailTask extends AsyncTask<String, Void, Void> {

        private com.google.api.services.gmail.Gmail mService = null;
        private Exception mLastError = null;
        private Context context;

        public SendEmailTask(Context context, GoogleAccountCredential credential) {
            this.context = context;
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.gmail.Gmail.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Gmail API Android Quickstart")
                    .build();
        }

        /**
         * Background task to send email using Gmail API.
         * @param params
         */
        @Override
        protected Void doInBackground(String... params) {
            try {
                MimeMessage mimeMessage = MessageBuilder.createEmail(params[0], params[0], params[1], params[2]);
                Message message = MessageBuilder.createMessageWithEmail(mimeMessage);
                MessageBuilder.sendMessage(mService, "me", mimeMessage);
            } catch (Exception e) {
                String errorMsg = "Exception in doInBackground" + e;
                Log.e(MainActivity.TAG, errorMsg);
                mLastError = e;
                cancel(true);
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            //mOutputText.setText("");
            //mProgress.show();
        }

        @Override
        protected void onPostExecute(Void output) {
            //mProgress.hide();
            //if (output == null || output.size() == 0) {
            //    mOutputText.setText("No results returned.");
            //} else {
            //    output.add(0, "Data retrieved using the Gmail API:");
            //    mOutputText.setText(TextUtils.join("\n", output));
            //}
        }

        @Override
        protected void onCancelled() {
            //mProgress.hide();
            String errorMsg;

            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    //todo
                    showGooglePlayServicesAvailabilityErrorDialog(((GooglePlayServicesAvailabilityIOException) mLastError).getConnectionStatusCode());

                    errorMsg = "onCancelled: mLastError instanceof GooglePlayServicesAvailabilityIOException ";
                    Log.e(MainActivity.TAG, errorMsg);
                    //Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show();
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    //todo
                    startActivityForResult(((UserRecoverableAuthIOException) mLastError).getIntent(), REQUEST_AUTHORIZATION);

                    errorMsg = "onCancelled: mLastError instanceof UserRecoverableAuthIOException ";
                    Log.e(MainActivity.TAG, errorMsg);
                    //Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show();
                } else {
                    outputTextView.setText("The following error occurred:\n" + mLastError.getMessage());

                    errorMsg = "onCancelled: The following error occurred:\n" + mLastError.getMessage();
                    Log.e(MainActivity.TAG, errorMsg);
                    //Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show();
                }
            } else {
                outputTextView.setText("Request cancelled.");

                errorMsg = "onCancelled: Request cancelled";
                Log.e(MainActivity.TAG, errorMsg);
                //Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show();
            }
        }
    }

    public static void showNotification(Context context, boolean failed){
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pIntent = PendingIntent.getActivity(context, 0, intent, 0);
        Notification mNotification = new Notification.Builder(context)
                        .setContentTitle(context.getResources().getString(R.string.app_name))
                        .setContentText(failed ?
                                context.getResources().getString(R.string.error) :
                                context.getResources().getString(R.string.open_settings))
                        .setSmallIcon(failed ?
                                R.drawable.mono_sms_broken :
                                R.drawable.mono_sms2gmail_small)
                        .setContentIntent(pIntent)
                        .setOngoing(failed)
                        .build();

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(0, mNotification);
    }

    public static void cancelNotification(Context context, int notificationId){
        if (Context.NOTIFICATION_SERVICE != null) {
            String ns = Context.NOTIFICATION_SERVICE;
            NotificationManager nMgr = (NotificationManager) context.getApplicationContext().getSystemService(ns);
            nMgr.cancel(notificationId);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        cancelNotification(this, 0);
    }

    @Override
    public void onStop() {
        super.onStop();
        String errorMsg = settings.getString(SmsToEmailApplication.PREF_ERROR_MSG, null);
        Boolean redirect = settings.getBoolean(SmsToEmailApplication.PREF_REDIRECT_TO_EMAIL, false);
        if (redirect) {
            showNotification(this, errorMsg != null);
        }
    }

    public static boolean isDeviceOnline(Context context) {
        ConnectivityManager connMgr =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

}
