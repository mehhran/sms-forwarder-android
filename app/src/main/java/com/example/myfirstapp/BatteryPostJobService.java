package com.example.myfirstapp;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BatteryPostJobService extends JobService {

    private static final String TAG = "BatteryPostJobService";

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "Job started");
        doBackgroundWork(params);
        return true;
    }

    private void doBackgroundWork(JobParameters params) {

        new Thread(new Runnable() {
            @Override
            public void run() {

                RequestBody formBody = new FormBody.Builder()
                        .add("batteryPct", params.getExtras().getString("batteryPct"))
                        .add("action", params.getExtras().getString("action"))
                        .add("action_time", params.getExtras().getString("action_time"))
                        .build();

                String myApiKey = BuildConfig.API_KEY;
                String myApiUrl = BuildConfig.BATTERY_API_URL;

                Request request = new Request.Builder()
                        .url(myApiUrl)
                        .post(formBody)
                        .addHeader("Authorization", myApiKey)
                        .build();


                TrustManagerFactory trustManagerFactory = null;
                try {
                    trustManagerFactory = TrustManagerFactory.getInstance(
                            TrustManagerFactory.getDefaultAlgorithm());
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
                try {
                    assert trustManagerFactory != null;
                    trustManagerFactory.init((KeyStore) myKeyStore());
                } catch (KeyStoreException e) {
                    e.printStackTrace();
                }
                TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
                if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
                    throw new IllegalStateException("Unexpected default trust managers:"
                            + Arrays.toString(trustManagers));
                }
                X509TrustManager trustManager = (X509TrustManager) trustManagers[0];

                SSLContext sslContext = null;
                try {
                    sslContext = SSLContext.getInstance("TLS");
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
                try {
                    assert sslContext != null;
                    sslContext.init(null, new TrustManager[] { trustManager }, null);
                } catch (KeyManagementException e) {
                    e.printStackTrace();
                }
                SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

                OkHttpClient client = new OkHttpClient.Builder()
                        .hostnameVerifier((hostname, session) -> {
                            HostnameVerifier hv = HttpsURLConnection.getDefaultHostnameVerifier();
                            /* Never return true without verifying the hostname, otherwise you will be vulnerable
                            to man in the middle attacks. */
                            return  hv.verify("manager", session);
                        })
                        .sslSocketFactory(sslSocketFactory, trustManager)
                        .build();

                Call call = client.newCall(request);

                try {
                    Response response = call.execute();
                    Objects.requireNonNull(response.body()).close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Log.d(TAG, "Job finished");
                jobFinished(params, false);
            }
        }).start();
    }

    protected KeyStore myKeyStore() {
        try {
            final KeyStore ks = KeyStore.getInstance("BKS");

            final InputStream in = this.getResources().openRawResource(R.raw.manager);
            try {
                ks.load(in, this.getString( R.string.mystore_password ).toCharArray());
            } finally {
                in.close();
            }

            return ks;

        } catch( Exception e ) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "Job cancelled before completion");
        return true;
    }
}
