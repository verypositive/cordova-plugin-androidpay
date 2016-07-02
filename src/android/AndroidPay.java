package com.verypositive.cordova;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.api.BooleanResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wallet.Cart;
import com.google.android.gms.wallet.FullWallet;
import com.google.android.gms.wallet.FullWalletRequest;
import com.google.android.gms.wallet.LineItem;
import com.google.android.gms.wallet.MaskedWallet;
import com.google.android.gms.wallet.MaskedWalletRequest;
import com.google.android.gms.wallet.PaymentMethodTokenizationParameters;
import com.google.android.gms.wallet.PaymentMethodTokenizationType;
import com.google.android.gms.wallet.Wallet;
import com.google.android.gms.wallet.WalletConstants;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AndroidPay extends CordovaPlugin {

    private static final int REQUEST_CODE_MASKED_WALLET = 888;
    private static final int REQUEST_CODE_FULL_WALLET = 999;
    private GoogleApiClient apiClient;
    private Cart cart;
    private CallbackContext paymentCallbackContext;

    @Override
    protected void pluginInitialize() {
        apiClient = new GoogleApiClient.Builder(cordova.getActivity())
                .addApi(Wallet.API, new Wallet.WalletOptions.Builder()
                                .setEnvironment(WalletConstants.ENVIRONMENT_TEST)
                                .setTheme(WalletConstants.THEME_LIGHT)
                                .build()
                )
                .build();
    }

    @Override
    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        cordova.getThreadPool().submit(new Runnable() {
            @Override
            public void run() {
                if ("canMakePayments".equals(action)) canMakePayments(args, callbackContext);
                else if ("makePaymentRequest".equals(action)) makePaymentRequest(args, callbackContext);
            }
        });

        return true;
    }

    private void canMakePayments(JSONArray args, final CallbackContext callbackContext) {
        if (!apiClient.isConnected()) apiClient.blockingConnect();
        Log.i("AndroidPay", "isReadyToPay " + apiClient.isConnected());

        Wallet.Payments.isReadyToPay(apiClient).setResultCallback(new ResultCallback<BooleanResult>() {
            @Override
            public void onResult(@NonNull BooleanResult booleanResult) {
                Log.i("AndroidPay", "onResult " + booleanResult.getStatus()
                                + " " + booleanResult.getValue()
                                + " " + booleanResult.getStatus().getStatusCode()
                                + " " + booleanResult.getStatus().getStatusMessage()
                );

                if (booleanResult.getStatus().isSuccess() && booleanResult.getValue()) callbackContext.success();
                else callbackContext.error(booleanResult.getStatus().getStatusMessage());
            }
        });
    }

    private void makePaymentRequest(JSONArray args, CallbackContext callbackContext) {
        if (!apiClient.isConnected()) apiClient.blockingConnect();

        PaymentMethodTokenizationParameters parameters = PaymentMethodTokenizationParameters.newBuilder()
                .setPaymentMethodTokenizationType(PaymentMethodTokenizationType.NETWORK_TOKEN)
                .addParameter("publicKey", "BO39Rh43UGXMQy5PAWWe7UGWd2a9YRjNLPEEVe+zWIbdIgALcDcnYCuHbmrrzl7h8FZjl6RCzoi5/cDrqXNRVSo=")
                .build();

        JSONObject order = args.optJSONObject(0);

        List<LineItem> lineItems = new ArrayList<LineItem>();

        JSONArray items = order.optJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);

            lineItems.add(LineItem.newBuilder()
                    .setDescription(item.optString("description"))
                    .setTotalPrice(item.optString("totalPrice"))
                    .build()
            );
        }

        cart = Cart.newBuilder()
                .setCurrencyCode(order.optString("currencyCode"))
                .setLineItems(lineItems)
                .setTotalPrice(order.optString("totalPrice"))
                .build();

        MaskedWalletRequest request = MaskedWalletRequest.newBuilder()
                .setMerchantName(order.optString("merchantName"))
                .setCurrencyCode(order.optString("currencyCode"))
                .setCart(cart)
                .setEstimatedTotalPrice(order.optString("totalPrice"))
                .setPaymentMethodTokenizationParameters(parameters)
                .build();

        paymentCallbackContext = callbackContext;
        cordova.setActivityResultCallback(this);
        Wallet.Payments.loadMaskedWallet(apiClient, request, REQUEST_CODE_MASKED_WALLET);
    }

    @Override
    public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext) {
        paymentCallbackContext = callbackContext;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case REQUEST_CODE_MASKED_WALLET:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        MaskedWallet wallet = intent.getParcelableExtra(WalletConstants.EXTRA_MASKED_WALLET);
                        Log.i("AndroidPay", "Masked wallet transaction: " + wallet.getGoogleTransactionId());
                        FullWalletRequest request = FullWalletRequest.newBuilder()
                                .setCart(cart)
                                .setGoogleTransactionId(wallet.getGoogleTransactionId())
                                .build();

                        Wallet.Payments.loadFullWallet(apiClient, request, REQUEST_CODE_FULL_WALLET);
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.i("AndroidPay", "Masked cancelled");
                        paymentCallbackContext.error("Masked wallet request cancelled");
                        break;
                    case WalletConstants.RESULT_ERROR:
                        Log.i("AndroidPay", "Masked error");
                        paymentCallbackContext.error("Masked wallet request error");
                        break;
                }

                return;

            case REQUEST_CODE_FULL_WALLET:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        FullWallet wallet = intent.getParcelableExtra(WalletConstants.EXTRA_FULL_WALLET);
                        Log.i("AndroidPay", "Token: " + wallet.getPaymentMethodToken().getToken());
                        paymentCallbackContext.success(wallet.getPaymentMethodToken().getToken());
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.i("AndroidPay", "Full cancelled");
                        paymentCallbackContext.error("Full wallet request cancelled");
                        break;
                    case WalletConstants.RESULT_ERROR:
                        Log.i("AndroidPay", "Full error");
                        paymentCallbackContext.error("Full wallet request error");
                        break;
                }

                return;

        }

        super.onActivityResult(requestCode, resultCode, intent);
    }
}
