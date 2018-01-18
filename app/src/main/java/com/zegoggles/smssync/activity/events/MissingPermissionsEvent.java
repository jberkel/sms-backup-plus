package com.zegoggles.smssync.activity.events;

import com.zegoggles.smssync.activity.AppPermission;

import java.util.List;

public class MissingPermissionsEvent {
    public final List<AppPermission> permissions;

    public MissingPermissionsEvent(List<AppPermission> permissions) {
        this.permissions = permissions;
    }
}
