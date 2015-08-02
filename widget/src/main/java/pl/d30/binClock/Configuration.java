package pl.d30.binClock;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.SkuDetails;
import com.anjlab.android.iab.v3.TransactionDetails;

import static android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID;
import static android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID;
import static pl.d30.binClock.ClockService.sendToService;

public class Configuration extends PreferenceActivity implements BillingProcessor.IBillingHandler {

    private static final String PREMIUM = "premium";
    private static final String PREMIUM_STORE_KEY = "premium.8b021080de";

    private BillingProcessor bp;

    private int widgetId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bp = new BillingProcessor(this, "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtQNvm3LK3qekOoo78aJIrrttSB4ZBdSPaUtbTSKSKKh4WS65guzavw1uLBE/sNcncPCLg9umWedvNeis++6gaxCQqTBT58RFsSjAap5zcbAWOpAD5UHrcmXt9DIKv66gcXtYEeTbRKPuoaSLWyoeng9O91TQjnHaPDkV0VgyFhN3GYY1eqrTCMRi9NnNiHvrKTV2HwFhtcbYpxU+W2ypSDfI5Sevs2s+GubCcyC3t850phVJe+qH1lDZPAmoxulWChTdNjyHR5WFQ6b6LSGv4gmgK0NlpMTJCa/7XpROlxXF18IhdjTgDC7lHjKK7DtC0E0beVrYv5jUAhZLkG18lQIDAQAB", this);
        bp.loadOwnedPurchasesFromGoogle();

        widgetId = getWidgetId(getIntent().getExtras());

        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_CANCELED, resultValue);

        if (widgetId == INVALID_APPWIDGET_ID)
            finish();

        BinaryWidgetSettings bws = new BinaryWidgetSettings();
        Bundle b = new Bundle();
        b.putInt(EXTRA_APPWIDGET_ID, widgetId);
        bws.setArguments(b);

        getFragmentManager()
            .beginTransaction()
            .replace(android.R.id.content, bws)
            .commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.preferences, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_done:
            case android.R.id.home:
                return createWidget();

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private boolean createWidget() {
        Bundle b = new Bundle();
        b.putInt("wid", widgetId);
        sendToService(this, ClockIntent.BINARY_WIDGET_CREATE, b);

        // "Phantom widgets" protection
        new Widget(this, widgetId).makeValid();

        Intent resultValue = new Intent();
        resultValue.putExtra(EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_OK, resultValue);

        finish();
        return true;
    }

    @Override
    public void onProductPurchased(String s, TransactionDetails transactionDetails) {
        Log.d("BinaryBilling", "onProductPurchased: " + s);
    }

    @Override
    public void onPurchaseHistoryRestored() {
        Log.d("BinaryBilling", "onPurchaseHistoryRestored");
    }

    @Override
    public void onBillingError(int i, Throwable throwable) {
        Log.d("BinaryBilling", "onBillingError: " + i);
    }

    @Override
    public void onBillingInitialized() {
        Log.d("BinaryBilling", "onBillingInitialized");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!bp.handleActivityResult(requestCode, resultCode, data))
            super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onDestroy() {
        if (bp != null)
            bp.release();

        super.onDestroy();
    }

    protected static int getWidgetId(Bundle b) {
        return b != null
            ? b.getInt(EXTRA_APPWIDGET_ID, INVALID_APPWIDGET_ID)
            : INVALID_APPWIDGET_ID;
    }

    public static class BinaryWidgetSettings extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            int widgetId = getWidgetId(getArguments());

            getPreferenceManager().setSharedPreferencesName(Widget.getPrefsName(widgetId));

            addPreferencesFromResource(R.xml.preferences);

            boolean isPremium = true;
            setPremium(isPremium);
        }


        private void enablePremium() {
            Preference premium = findPreference(PREMIUM);
            getPreferenceScreen().removePreference(premium);

            Preference dotColor = findPreference(Widget.SP_KEY_DOT_COLOR);
            dotColor.setEnabled(true);

            Preference compact = findPreference(Widget.SP_KEY_TYPE);
            compact.setEnabled(true);

            CheckBoxPreference am_pm = (CheckBoxPreference) findPreference(Widget.SP_KEY_AM_PM);
            am_pm.setEnabled(true);
            am_pm.setOnPreferenceChangeListener(new CheckBoxPreference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                Boolean enabled = (Boolean) newValue;
                if (enabled)
                    toast("Dem dot on dem top left be PM. No dot be AM.");

                return true;
                }
            });

        }

        private void showBuyPremiumDialog() {
            ((Configuration) getActivity()).showPremiumDialog();
        }

        private void disablePremium() {
            Preference premium = findPreference(PREMIUM);
            premium.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                showBuyPremiumDialog();
                return false;
                }
            });
        }

        private void setPremium(boolean premium) {
            if (premium) enablePremium();
            else disablePremium();
        }

        private void toast(String text) {
            Toast.makeText(getActivity(), text, Toast.LENGTH_SHORT).show();
        }
    }

    private void showPremiumDialog() {
        SkuDetails sku = bp.getPurchaseListingDetails(PREMIUM_STORE_KEY);
        Log.d("BinaryBilling", sku.toString());
        bp.purchase(this, PREMIUM_STORE_KEY);
    }
}