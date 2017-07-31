package com.zegoggles.smssync.service.exception;

import com.zegoggles.smssync.R;

public class RequiresBackgroundDataException extends Exception implements LocalizableException {
    @Override
    public int errorResourceId() {
        return R.string.app_log_skip_backup_background_data;
    }
}
