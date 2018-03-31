package com.zegoggles.smssync.service.exception;


import java.util.Set;

public class MissingPermissionException extends Exception {
    public final Set<String> permissions;

    public MissingPermissionException(Set<String> permissions) {
        this.permissions = permissions;
    }
}
