package org.mycrimes.insecuretests.preferences;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.mycrimes.insecuretests.R;
import org.mycrimes.insecuretests.keyvalue.SignalStore;
import org.mycrimes.insecuretests.lock.v2.CreateKbsPinActivity;
import org.mycrimes.insecuretests.payments.backup.PaymentsRecoveryStartFragmentArgs;
import org.mycrimes.insecuretests.payments.preferences.PaymentsActivity;
import org.mycrimes.insecuretests.pin.PinOptOutDialog;
import org.mycrimes.insecuretests.util.TextSecurePreferences;

public class AdvancedPinPreferenceFragment extends ListSummaryPreferenceFragment {

  private static final String PREF_ENABLE  = "pref_pin_enable";
  private static final String PREF_DISABLE = "pref_pin_disable";

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_advanced_pin);
  }

  @Override
  public void onResume() {
    super.onResume();
    updatePreferenceState();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == CreateKbsPinActivity.REQUEST_NEW_PIN && resultCode == CreateKbsPinActivity.RESULT_OK) {
      Snackbar.make(requireView(), R.string.ApplicationPreferencesActivity_pin_created, Snackbar.LENGTH_LONG).show();
    }
  }

  private void updatePreferenceState() {
    Preference enable = this.findPreference(PREF_ENABLE);
    Preference disable = this.findPreference(PREF_DISABLE);

    if (SignalStore.kbsValues().hasOptedOut()) {
      enable.setVisible(true);
      disable.setVisible(false);

      enable.setOnPreferenceClickListener(preference -> {
        onPreferenceChanged(true);
        return true;
      });
    } else {
      enable.setVisible(false);
      disable.setVisible(true);

      disable.setOnPreferenceClickListener(preference -> {
        onPreferenceChanged(false);
        return true;
      });
    }
  }

  private void onPreferenceChanged(boolean enabled) {
    boolean hasRegistrationLock = TextSecurePreferences.isV1RegistrationLockEnabled(requireContext()) ||
                                  SignalStore.kbsValues().isV2RegistrationLockEnabled();

    if (!enabled && hasRegistrationLock) {
      new MaterialAlertDialogBuilder(requireContext())
                     .setMessage(R.string.ApplicationPreferencesActivity_pins_are_required_for_registration_lock)
                     .setCancelable(true)
                     .setPositiveButton(android.R.string.ok, (d, which) -> d.dismiss())
                     .show();
    } else if (!enabled && SignalStore.paymentsValues().mobileCoinPaymentsEnabled() && !SignalStore.paymentsValues().getUserConfirmedMnemonic()) {
      new MaterialAlertDialogBuilder(requireContext())
                     .setTitle(R.string.ApplicationPreferencesActivity_record_payments_recovery_phrase)
                     .setMessage(R.string.ApplicationPreferencesActivity_before_you_can_disable_your_pin)
                     .setPositiveButton(R.string.ApplicationPreferencesActivity_record_phrase, (dialog, which) -> {
                       Intent intent = new Intent(requireContext(), PaymentsActivity.class);
                       intent.putExtra(PaymentsActivity.EXTRA_PAYMENTS_STARTING_ACTION, R.id.action_directly_to_paymentsBackup);
                       intent.putExtra(PaymentsActivity.EXTRA_STARTING_ARGUMENTS, new PaymentsRecoveryStartFragmentArgs.Builder().setFinishOnConfirm(true).build().toBundle());

                       startActivity(intent);

                       dialog.dismiss();
                     })
                     .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                     .setCancelable(true)
                     .show();
    } else if (!enabled) {
      PinOptOutDialog.show(requireContext(),
                           () -> {
                             updatePreferenceState();
                             Snackbar.make(requireView(), R.string.ApplicationPreferencesActivity_pin_disabled, Snackbar.LENGTH_SHORT).show();
                           });
    } else {
      startActivityForResult(CreateKbsPinActivity.getIntentForPinCreate(requireContext()), CreateKbsPinActivity.REQUEST_NEW_PIN);
    }
  }
}
