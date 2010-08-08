## Android-SMS

This is a mirror/fork of the svn repository at
[http://code.google.com/p/android-sms](http://code.google.com/p/android-sms).

### Building

    $ cd android-client
    $ echo "sdk.dir=/path/to/android-sdk" > local.properties
    $ ant debug

### Restoring

The restore_backups branch contains code to restore SMS from gmail back to the
phone. This has not been integrated in the official product yet, but if you
want to test it you can find precompiled binaries in the download section.

The issue relating to this: [Issue 3](http://code.google.com/p/android-sms/issues/detail?id=3).

## Contributing

Development on the upstream project seems to have stalled at the moment. Check the [original issue list](http://code.google.com/p/android-sms/issues/list)
or the [github issues](http://github.com/jberkel/android-sms/issues) for a list of things to work on.

If you want to help translating the UI to other languages: [crowdin project page](http://crowdin.net/project/sms-backup-plus/invite).

## Credits

  * [Christoph Studer](http://studer.tv/) (<chstuder@gmail.com>) Original author of SMS Backup
  * [k9mail](http://code.google.com/p/k9mail/) IMAP library, with some modifications [k9mail/android-sms](http://github.com/jberkel/k9mail)
  * [signpost](http://github.com/kaeppler/signpost) Signpost
  * [iTweek](http://itweek.deviantart.com/) and [ncrow](http://http://ncrow.deviantart.com/) for the gmail icons
