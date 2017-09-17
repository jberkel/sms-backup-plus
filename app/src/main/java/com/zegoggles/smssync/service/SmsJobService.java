package com.zegoggles.smssync.service;

import android.content.Intent;

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;


public class SmsJobService extends JobService
{
  @Override
  public boolean onStartJob(JobParameters job)
  {
    final Intent intent = new Intent(this, SmsBackupService.class);
    intent.putExtras(job.getExtras());
    startService(intent);
    return true;
  }

  @Override
  public boolean onStopJob(JobParameters job)
  {
    return false;
  }
}
