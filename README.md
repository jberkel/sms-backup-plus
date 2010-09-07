## SMS Backup+

This is a fork of the Android backup tool
[SMS Backup](http://code.google.com/p/android-sms), where development has stopped a while ago. It uses Gmail to perform SMS backups over the network.

Main differences / improvements:

  * New restore feature. SMS stored on Gmail can be transferred back to the phone. This even works for users who have already created their backups with older versions of SMS Backup.

  * XOAuth: SMS Backup+ will never ask you for your Gmail password. Instead it uses [XOAuth](http://code.google.com/apis/gmail/oauth/) to get access to your data.

  * Faster backups. SMS Backup+ saves around 30% of data transferred by avoiding Base64 encoding of emails.

  * Batch size limits removed.

  * Works with any IMAP server (but defaults to Gmail).

Tested with Android 1.5 - 2.2.

### Droid X

*Important:* Motorola Droid X is currently not fully supported, there appears to be a browser bug
which breaks the authorisation process, so connecting your Gmail account via XOAuth is not possible.

There are two possible workarounds:

  * Install a 3rd party browser (Dolphin HD has been reported to work)
  * Use plain text authentication (Advanced Settings - Server Settings), use your gmail address as username and supply your password

SMS Backup+ is available for free in the market, there will never be a pro / paid version.

[![][FlattrButton]][FlattrLink] But if you like the app you can flattr it ([flattr it ??][WhatisFlattr]).

## Usage

### Installation

![MarketQRCode][] Install via the Android market QR code link or directly from [github downloads][].

### First steps

You need to have an IMAP account or a Gmail account with IMAP enabled. See the
[Enabling IMAP in Gmail][] help page to learn how to enable IMAP for your Gmail
account. If you use Google Apps make sure you select "Sign in with a Google
Apps Account" ([screenshot](http://skitch.com/jberkel/ditwx/5554-emu-2.2)).

After starting SMS Backup+, tap on the "Connect" check box to start the authorization process. A browser window will open where Gmail either prompts you to log in *or* (if you were already logged in) a screen asking you to give SMS Backup+ permission to access your emails.

After clicking on "Grant Access" SMS Backup+ will become visible again, the checkbox should now be checked, indicating that the authorization process was successful.

### Upgrading (for users of SMS Backup)

You don't need to uninstall the old version in order to try out SMS Backup+. However make sure that "Auto backup" is disabled in both apps, otherwise you might end up with messages backed up multiple times, resulting in duplicates in Gmail.

If you've used SMS Backup before you should just connect your Gmail account as described above (SMS Backup+ is not able to access login information stored by SMS Backup). Make sure you select "Skip" when asked about the initial sync, otherwise already backed up messages will be backed up again.

### Initial backup

It is important that you perform the first backup manually. SMS Backup+ needs to know whether you want to upload messages currently stored on your device or not.

After having connected your Gmail account, SMS Backup+ will ask you to perform a first sync. If you choose "Backup", SMS Backup+ will start backing up all your messages to Gmail.

If you choose "Skip", nothing is sent to Gmail and all messages currently stored on your device are simply marked "backed up". This option is handy if you previously uninstalled SMS Backup+ and do not want to send your messages again to Gmail. Please note that any messages arrived after you last uninstalled SMS Backup and this initial backup won't ever be backed up to Gmail.

After you performed your initial backup, SMS Backup+ is ready to run in the background and finish uploading all of your current and future messages (check "Auto Backup").

### Restoring

If you wish to restore messages back to your phone tap "Restore". By default all messages stored on Gmail will be restored (this can be changed in "Advanced Settings"). You can safely restore to a phone which has already message stored on it, SMS Backup+ will skip the restore of already existing messages.

##Screenshots

![SMS Backup+ screenshot][shot1] ![Gmail screenshot][shot2]

## Contributing

### Installation from source

    $ git clone git://github.com/jberkel/sms-backup-plus.git
    $ cd sms-backup-plus
    $ echo "sdk.dir=/path/to/android-sdk" > local.properties
    $ ant debug
    $ adb install bin/sms-backup-plus-debug.apk

I've imported some relevant issues from the [original issue list][] to [github issues][].

If you want to help translating the UI to other languages: [crowdin project page][].

## Credits

  * [Christoph Studer](http://studer.tv/) (<chstuder@gmail.com>) Original author of SMS Backup
  * [k9mail](http://code.google.com/p/k9mail/) IMAP library, with some modifications ([k9mail/sms-backup-plus](http://github.com/jberkel/k9mail))
  * [signpost](http://github.com/kaeppler/signpost) Signpost OAuth library
  * [iTweek](http://itweek.deviantart.com/) and [ncrow](http://ncrow.deviantart.com/) for the Gmail icons

## License

This application is released under the terms of the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).

[original issue list]: http://code.google.com/p/android-sms/issues/list
[github issues]: http://github.com/jberkel/sms-backup-plus/issues
[MarketQRCode]: http://chart.apis.google.com/chart?cht=qr&chs=100x100&chl=http://cyrket.com/qr/144601
[WhatisFlattr]: http://en.wikipedia.org/wiki/Flattr
[FlattrLink]: http://flattr.com/thing/45809/SMS-Backup
[FlattrButton]: http://api.flattr.com/button/button-static-50x60.png
[github downloads]: http://github.com/downloads/jberkel/sms-backup-plus/sms-backup-plus-v1.0.5.apk/qr_code
[Enabling IMAP in Gmail]: http://mail.google.com/support/bin/answer.py?hl=en&answer=77695
[shot1]: http://cloud.github.com/downloads/jberkel/sms-backup-plus/sms_backup_plus_restoring.png
[shot2]: http://cloud.github.com/downloads/jberkel/sms-backup-plus/sms_gmail_screenshot.png
[crowdin project page]: http://crowdin.net/project/sms-backup-plus/invite
