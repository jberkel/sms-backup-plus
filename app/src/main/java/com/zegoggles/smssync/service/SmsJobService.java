/* Copyright (c) 2017 Jan Berkel <jan.berkel@gmail.com>
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
package com.zegoggles.smssync.service;

import android.content.Intent;

import android.util.Log;
import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;


public class SmsJobService extends JobService {
    /**
     * @return {@code true} if there is more work remaining in the worker thread, {@code false} if the
     * job was completed.
     */
    @Override
    public boolean onStartJob(JobParameters job) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "onStartJob(" + job + ", extras=" + job.getExtras() + ")");
        }
        final Intent intent = new Intent(this, SmsBackupService.class);
        intent.putExtras(job.getExtras());
        startService(intent);
        return true;
    }

    /**
     * @return true if the job should be retried
     */
    @Override
    public boolean onStopJob(JobParameters job) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "onStopJob(" + job + ", extras=" + job.getExtras() + ")");
        }
        return false;
    }
}
