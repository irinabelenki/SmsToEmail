package com.irinabelenki.smstoemail;

/**
 * Created by Irina on 1/18/2016.
 */

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
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
import com.google.api.services.gmail.model.Message;

import javax.mail.internet.MimeMessage;

/**
 * Created by Irina on 1/2/2016.
 */
public class IncomingSms extends BroadcastReceiver {

    public void onReceive(Context context, Intent intent) {
        SharedPreferences settings = context.getSharedPreferences(SmsToEmailApplication.SHARED_PREF_NAME, Context.MODE_PRIVATE);
        String accountName = settings.getString(SmsToEmailApplication.PREF_ACCOUNT_NAME, null);
        Boolean redirect = settings.getBoolean(SmsToEmailApplication.PREF_REDIRECT_TO_EMAIL, false);
        if(!redirect || accountName == null) {
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
                    String logMsg = "Received SMS: phoneNumber: " + phoneNumber + "; messageBody: " + messageBody;
                    Log.i(MainActivity.TAG, logMsg);
                    Toast.makeText(context, logMsg, Toast.LENGTH_LONG).show();

                    GoogleAccountCredential credential = ((SmsToEmailApplication) context.getApplicationContext()).getGoogleAccountCredential();
                    String[] params = {
                            accountName,
                            phoneNumber,
                            messageBody};

                    new SendEmailTask(context, credential).execute(params);
                }
            }
        } catch (Exception e) {
            String errorMsg = "Exception in SMS receiver" + e;
            Log.e(MainActivity.TAG, errorMsg);
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show();
        }
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

        @Override
        protected Void doInBackground(String... params) {
            try {
                String displayName = this.getContactDisplayNameByPhoneNumber(params[1]);
                String msg = "Display name: " + displayName;
                Log.e(MainActivity.TAG, msg);

                String subject = "From: " + (displayName == null ? params[1] : displayName);
                MimeMessage mimeMessage = MessageBuilder.createEmail(params[0], params[1], subject, params[2]);
                Message message = MessageBuilder.createMessageWithEmail(mimeMessage);
                MessageBuilder.sendMessage(mService, "me", mimeMessage);
            } catch (Exception e) {
                String errorMsg = "Exception in doInBackground: " + e;
                Log.e(MainActivity.TAG, errorMsg);

                mLastError = e;
                cancel(true);
            }
            return null;
        }

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected void onPostExecute(Void output) {

        }

        @Override
        protected void onCancelled() {
            String errorMsg;
            if (mLastError != null) {
                GoogleAccountCredential credential = ((SmsToEmailApplication) context.getApplicationContext()).getGoogleAccountCredential();
                credential.setSelectedAccountName(null);
                SharedPreferences settings = context.getSharedPreferences(SmsToEmailApplication.SHARED_PREF_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = settings.edit();
                editor.putString(SmsToEmailApplication.PREF_ACCOUNT_NAME, null);
                editor.apply();

                //todo error dialog "some sms could be not forwarded"
                showErrorDialog();

                errorMsg = "onCancelled: lastError: " + mLastError.toString();
                Log.e(MainActivity.TAG, errorMsg);
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show();
            } else {
                errorMsg = "onCancelled: Request cancelled";
                Log.e(MainActivity.TAG, errorMsg);
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show();
            }
        }

        public String getContactDisplayNameByPhoneNumber(String phoneNumber) {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
            String name = null;

            ContentResolver contentResolver = context.getContentResolver();
            Cursor contactLookup = contentResolver.query(uri, new String[]{BaseColumns._ID,
                    ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);

            try {
                if (contactLookup != null && contactLookup.getCount() > 0) {
                    contactLookup.moveToNext();
                    name = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
                    //String contactId = contactLookup.getString(contactLookup.getColumnIndex(BaseColumns._ID));
                }
            } finally {
                if (contactLookup != null) {
                    contactLookup.close();
                }
            }

            return name;
        }

        private void showErrorDialog() {
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
            alertDialogBuilder.setTitle("Error");

            alertDialogBuilder
                    .setMessage("Some SMS could be not forwarded")
                    .setCancelable(false)
                    .setPositiveButton("OK",new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int id) {
                            dialog.dismiss();
                        }
                    });
            AlertDialog alertDialog = alertDialogBuilder.create();
            alertDialog.show();
        }
    }
}

