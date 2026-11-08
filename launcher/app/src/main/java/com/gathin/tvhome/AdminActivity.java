package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Toast;

/**
 * The top-level password manager. The administrator PIN guards entry; once it is right the parent
 * can overwrite the children's or elders PIN without the old one, or change the administrator PIN.
 * All PINs here are four D-pad presses (arrow mode) so a remote without number keys still works.
 */
public class AdminActivity extends Activity {

    private static final boolean ARROWS = true;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Kids.init(this);
        if (Kids.adminPinSet()) {
            verifyAdmin();
        } else {
            createAdminFirst();
        }
    }

    private void createAdminFirst() {
        PinDialog dialog = new PinDialog(this, PinDialog.CREATE, ARROWS, new PinDialog.PinListener() {
            @Override public void onPin(String pin) {
                Kids.chooseAdminPin(pin, ARROWS);
                showMenu();
            }

            @Override public void onCancel() {
                finish();
            }
        });
        dialog.asAdmin();
        dialog.show();
    }

    private void verifyAdmin() {
        PinDialog dialog = new PinDialog(this, PinDialog.VERIFY, Kids.adminPinArrows(),
                new PinDialog.PinListener() {
            @Override public void onPin(String pin) {
                if (Kids.adminPinMatches(pin)) {
                    showMenu();
                } else {
                    Toast.makeText(AdminActivity.this, R.string.admin_pin_wrong, Toast.LENGTH_LONG).show();
                    verifyAdmin();
                }
            }

            @Override public void onCancel() {
                finish();
            }
        });
        dialog.asAdmin();
        dialog.show();
    }

    private void showMenu() {
        String[] items = {
                getString(R.string.admin_reset_kids),
                getString(R.string.admin_reset_elders),
                getString(R.string.admin_change_admin),
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_title)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) resetMode(Kids.Kind.KIDS);
                    else if (which == 1) resetMode(Kids.Kind.ELDERS);
                    else changeAdmin();
                })
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void resetMode(final Kids.Kind target) {
        PinDialog dialog = new PinDialog(this, PinDialog.CREATE, ARROWS, new PinDialog.PinListener() {
            @Override public void onPin(String pin) {
                Kids.resetPin(target, pin, ARROWS);
                Toast.makeText(AdminActivity.this, R.string.admin_reset_done, Toast.LENGTH_LONG).show();
                showMenu();
            }

            @Override public void onCancel() {
                showMenu();
            }
        });
        dialog.show();
    }

    private void changeAdmin() {
        PinDialog dialog = new PinDialog(this, PinDialog.CREATE, ARROWS, new PinDialog.PinListener() {
            @Override public void onPin(String pin) {
                Kids.chooseAdminPin(pin, ARROWS);
                Toast.makeText(AdminActivity.this, R.string.admin_reset_done, Toast.LENGTH_LONG).show();
                showMenu();
            }

            @Override public void onCancel() {
                showMenu();
            }
        });
        dialog.asAdmin();
        dialog.show();
    }
}
