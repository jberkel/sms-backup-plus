## Android-SMS

This is a mirror/fork of the svn repository at
[http://code.google.com/p/android-sms](http://code.google.com/p/android-sms).

## Building

    $ cd android-client
    $ echo "sdk.dir=/path/to/android-sdk" > local.properties
    $ ant debug

### Restoring

The restore_backups branch contains code to restore SMS from gmail back to the
phone. This has not been integrated in the official product yet, but if you
want to test it you can find precompiled binaries in the download section.

The issue relating to this: [Issue 3](http://code.google.com/p/android-sms/issues/detail?id=3).
