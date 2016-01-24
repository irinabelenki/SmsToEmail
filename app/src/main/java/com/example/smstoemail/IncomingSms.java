package com.example.smstoemail;

/**
 * Created by Irina on 1/18/2016.
 */

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.mail.internet.MimeMessage;

/**
 * Created by Irina on 1/2/2016.
 */
public class IncomingSms extends BroadcastReceiver {

    public void onReceive(Context context, Intent intent) {
        SharedPreferences settings = context.getSharedPreferences(SmsToEmailApplication.SHARED_PREF_NAME, Context.MODE_PRIVATE);
        Boolean redirect = settings.getBoolean(SmsToEmailApplication.PREF_REDIRECT_TO_EMAIL, false);
        String accountName = settings.getString(SmsToEmailApplication.PREF_ACCOUNT_NAME, null);
        if(!redirect) {
            return;
        }

        final Bundle bundle = intent.getExtras();
        try {
            if (bundle != null) {
                final Object[] pdusObj = (Object[]) bundle.get("pdus");
                for (int i = 0; i < pdusObj.length; i++) {
                    SmsMessage currentMessage = SmsMessage.createFromPdu((byte[]) pdusObj[i]);
                    String phoneNumber = currentMessage.getDisplayOriginatingAddress();
                    String messageBody = currentMessage.getDisplayMessageBody();
                    Log.i(MainActivity.TAG, "phoneNumber: " + phoneNumber + "; message: " + messageBody);

                    GoogleAccountCredential credential = ((SmsToEmailApplication) context.getApplicationContext()).getGoogleAccountCredential();
                    String[] params = {accountName,
                            phoneNumber,
                            "From:" + phoneNumber,
                            messageBody};

                    new SendEmailTask(context, credential).execute(params);
                }
            }
        } catch (Exception e) {
            Log.e(MainActivity.TAG, "Exception smsReceiver" + e);
        }
    }

    private class SendEmailTask extends AsyncTask<String, Void, Void> {
        private com.google.api.services.gmail.Gmail mService = null;
        private Exception mLastError = null;
        private Context context;

        public SendEmailTask(Context context,
                             GoogleAccountCredential credential) {
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
                MimeMessage mimeMessage = MessageBuilder.createEmail(params[0], params[1], params[2], params[3]);
                Message message = MessageBuilder.createMessageWithEmail(mimeMessage);
                MessageBuilder.sendMessage(mService, "me", mimeMessage);
            } catch (Exception e) {
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
//TODO what to do with it?
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    //todo showGooglePlayServicesAvailabilityErrorDialog(((GooglePlayServicesAvailabilityIOException) mLastError).getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    //todo startActivityForResult(((UserRecoverableAuthIOException) mLastError).getIntent(), REQUEST_AUTHORIZATION);
                } else {
                    //mOutputText.setText("The following error occurred:\n" + mLastError.getMessage());
                    Toast.makeText(context, "The following error occurred:\n" + mLastError.getMessage(), Toast.LENGTH_LONG).show();
                }
            } else {
                //mOutputText.setText("Request cancelled.");
                Toast.makeText(context, "Request cancelled.", Toast.LENGTH_LONG).show();
            }
        }
    }

}

