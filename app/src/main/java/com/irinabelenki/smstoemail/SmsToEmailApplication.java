package com.irinabelenki.smstoemail;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.gmail.GmailScopes;

import java.util.Arrays;

/**
 * Created by Irina on 1/18/2016.
 */
public class SmsToEmailApplication extends Application {

    private Context context;
    private GoogleAccountCredential googleAccountCredential;

    public static final String SHARED_PREF_NAME = "smsToEmailPref";
    public static final String PREF_ACCOUNT_NAME = "accountName";
    public static final String PREF_REDIRECT_TO_EMAIL = "redirectToEmail";
    private static final String[] SCOPES = { GmailScopes.MAIL_GOOGLE_COM };

    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();

        SharedPreferences settings = context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        googleAccountCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff())
                .setSelectedAccountName(settings.getString(PREF_ACCOUNT_NAME, null));
    }

    public Context getAppContext() {
        return context;
    }

    public GoogleAccountCredential getGoogleAccountCredential() {
        return  googleAccountCredential;
    }
}
