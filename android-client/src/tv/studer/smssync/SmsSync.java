/* Copyright (c) 2009 Christoph Studer <chstuder@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tv.studer.smssync;

import tv.studer.smssync.SmsSyncService.SmsSyncState;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class SmsSync extends Activity implements OnClickListener,
        SmsSyncService.StateChangeListener {
    private Button mSyncButton;

    private TextView mStatusLabel;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mSyncButton = (Button)findViewById(R.id.sync_button);
        mSyncButton.setOnClickListener(this);
        mStatusLabel = (TextView)findViewById(R.id.status);
        updateServiceStatus(SmsSyncState.IDLE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        SmsSyncService.unsetStateChangeListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SmsSyncService.setStateChangeListener(this);
    }

    private void startSync() {
        Intent intent = new Intent(this, SmsSyncService.class);
        startService(intent);
    }

    @Override
    public void onClick(View v) {
        if (v == mSyncButton) {
            startSync();
        }
    }

    @Override
    public void stateChanged(final SmsSyncState oldState, final SmsSyncState newState) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateServiceStatus(oldState);
            }
        });
    }

    private void updateServiceStatus(SmsSyncState oldState) {
        SmsSyncState newState = SmsSyncService.getState();
        CharSequence statusLabel;

        switch (newState) {
            case AUTH_FAILED:
                statusLabel = getText(R.string.status_auth_failure);
                break;
            case CALC:
                statusLabel = getText(R.string.status_calc);
                break;
            case IDLE:
                if (oldState == SmsSyncState.SYNC || oldState == SmsSyncState.CALC) {
                    statusLabel = getText(R.string.status_done);
                } else {
                    statusLabel = getText(R.string.status_idle);
                }
                break;
            case LOGIN:
                statusLabel = getText(R.string.status_login);
                break;
            case REG:
                statusLabel = getText(R.string.status_reg);
                break;
            case SYNC:
                statusLabel = getString(R.string.status_sync, SmsSyncService
                        .getCurrentSyncedItems(), SmsSyncService.getItemsToSyncCount());
                break;
            default:
                statusLabel = "";
        }
        mStatusLabel.setText(statusLabel);
    }
}
