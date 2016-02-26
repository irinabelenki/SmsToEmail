package com.irinabelenki.smstoemail;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

/**
 * Created by Irina on 2/22/2016.
 */
public class IncomingMms extends BroadcastReceiver {
        private static final String ACTION_MMS_RECEIVED = "android.provider.Telephony.WAP_PUSH_RECEIVED";
        private static final String MMS_DATA_TYPE = "application/vnd.wap.mms-message";

        // Retrieve MMS
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String type = intent.getType();

            if(action.equals(ACTION_MMS_RECEIVED) && type.equals(MMS_DATA_TYPE)){
                Bundle bundle = intent.getExtras();

                Log.d(MainActivity.TAG, "bundle " + bundle);
                SmsMessage[] msgs = null;
                String str = "";
                int contactId = -1;
                String address;

                if (bundle != null) {

                    byte[] buffer = bundle.getByteArray("data");
                    Log.d(MainActivity.TAG, "buffer " + buffer);
                    String incomingNumber = new String(buffer);
                    int indx = incomingNumber.indexOf("/TYPE");
                    if(indx>0 && (indx-15)>0){
                        int newIndx = indx - 15;
                        incomingNumber = incomingNumber.substring(newIndx, indx);
                        indx = incomingNumber.indexOf("+");
                        if(indx>0){
                            incomingNumber = incomingNumber.substring(indx);
                            Log.d(MainActivity.TAG, "Mobile Number: " + incomingNumber);
                        }
                    }

                    int transactionId = bundle.getInt("transactionId");
                    Log.d(MainActivity.TAG, "transactionId " + transactionId);

                    int pduType = bundle.getInt("pduType");
                    Log.d(MainActivity.TAG, "pduType " + pduType);

                    byte[] buffer2 = bundle.getByteArray("header");
                    String header = new String(buffer2);
                    Log.d(MainActivity.TAG, "header " + header);

                    //if(contactId != -1){
                    //    showNotification(contactId, str);
                    //}

                    // ---send a broadcast intent to update the MMS received in the
                    // activity---
                    //Intent broadcastIntent = new Intent();
                    //broadcastIntent.setAction("MMS_RECEIVED_ACTION");
                    //broadcastIntent.putExtra("mms", str);
                    //context.sendBroadcast(broadcastIntent);

                }
            }

        }

        /**
         * The notification is the icon and associated expanded entry in the status
         * bar.
         */
        protected void showNotification(int contactId, String message) {
            //Display notification...
        }
    }
