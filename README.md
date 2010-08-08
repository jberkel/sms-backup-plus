## SMS Backup+

This is a fork of the Android backup tool
[SMS Backup](http://code.google.com/p/android-sms), where development has stopped a while ago.

Main differences / improvements:

  * New restore feature. SMS stored on Gmail can be transferred back to the phone. This even works for users who have already created their backups with older versions of SMS Backup.

  * XOAuth: SMS Backup+ will never ask you for your Gmail password. Instead it uses [XOAuth](http://code.google.com/apis/gmail/oauth/) to get access to your data.

  * Batch size limits removed.

  * Works with any IMAP server (but defaults to Gmail).

### Building

    $ echo "sdk.dir=/path/to/android-sdk" > local.properties
    $ ant debug

### Contributing

Check the [original issue list](http://code.google.com/p/android-sms/issues/list)
or the [github issues](http://github.com/jberkel/android-sms/issues) for a list of things to work on.

If you want to help translating the UI to other languages: [crowdin project page](http://crowdin.net/project/sms-backup-plus/invite).

## Credits

  * [Christoph Studer](http://studer.tv/) (<chstuder@gmail.com>) Original author of SMS Backup
  * [k9mail](http://code.google.com/p/k9mail/) IMAP library, with some modifications ([k9mail/android-sms](http://github.com/jberkel/k9mail))
  * [signpost](http://github.com/kaeppler/signpost) Signpost OAuth library
  * [iTweek](http://itweek.deviantart.com/) and [ncrow](http://http://ncrow.deviantart.com/) for the gmail icons

