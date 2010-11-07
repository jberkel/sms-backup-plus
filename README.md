## SMS Backup+

This is a fork of the Android backup tool
[SMS Backup](http://code.google.com/p/android-sms), where development has
stopped a while ago. It uses Gmail to perform SMS, MMS and call log backups over
the network.

Main differences / improvements:

  * New restore feature. SMS stored on Gmail can be transferred back to the
  phone. This even works for users who have already created their backups with
  older versions of SMS Backup. Note: MMS are currently not restored.

  * XOAuth: SMS Backup+ will never ask you for your Gmail password. Instead it
  uses [XOAuth](http://code.google.com/apis/gmail/oauth/) to get access to your
  data. You can revoke the access rights at any time.

  * MMS backup support (since 1.1)

  * Call log backup (since 1.2)

  * Faster backups. SMS Backup+ saves around 30% of data transferred by
  avoiding Base64 encoding of emails.

  * Batch size limits removed.

  * Works with any IMAP server (but defaults to Gmail).

Tested with Android 1.5 - 2.2.

### Users of Opera Mini / Droid X < 2.2 / non-standard browsers

*Important:* Some browsers / handsets which ship with non-standard browsers are
currently not fully supported, there appears to be a problem which breaks
the authorisation process, so connecting your Gmail account via XOAuth is not
possible.

Some possible workarounds:

  * Use the stock Android browser for the authentication (you can switch back
  afterwards)
  * Droid X: install a 3rd party browser (Dolphin HD has been reported to
  work). However Droid X 2.2 has been reported to work.
  * Use plain text authentication (Advanced Settings - Server Settings), use
  your gmail address as username and supply your password

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

After starting SMS Backup+, tap on the "Connect" check box to start the
authorization process. A browser window will open where Gmail either prompts
you to log in *or* (if you were already logged in) a screen asking you to give
SMS Backup+ permission to access your emails.

After clicking on "Grant Access" SMS Backup+ will become visible again, the
checkbox should now be checked, indicating that the authorization process was
successful.

### Upgrading (for users of SMS Backup)

You don't need to uninstall the old version in order to try out SMS Backup+.
However make sure that "Auto backup" is disabled in both apps, otherwise you
might end up with messages backed up multiple times, resulting in duplicates in
Gmail.

If you've used SMS Backup before you should just connect your Gmail account as
described above (SMS Backup+ is not able to access login information stored by
SMS Backup). Make sure you select "Skip" when asked about the initial sync,
otherwise already backed up messages will be backed up again.

### Initial backup

It is important that you perform the first backup manually. SMS Backup+ needs
to know whether you want to upload messages currently stored on your device or
not.

After having connected your Gmail account, SMS Backup+ will ask you to perform
a first sync. If you choose "Backup", SMS Backup+ will start backing up all
your messages to Gmail.

If you choose "Skip", nothing is sent to Gmail and all messages currently
stored on your device are simply marked "backed up". This option is handy if
you previously uninstalled SMS Backup+ and do not want to send your messages
again to Gmail. Please note that any messages arrived after you last
uninstalled SMS Backup and this initial backup won't ever be backed up to
Gmail.

After you performed your initial backup, SMS Backup+ is ready to run in the
background and finish uploading all of your current and future messages (check
"Auto Backup").

### Restoring

If you wish to restore messages back to your phone tap "Restore". By default
all messages stored on Gmail will be restored (this can be changed in "Advanced
Settings"). You can safely restore to a phone which has already message stored
on it, SMS Backup+ will skip the restore of already existing messages.

### Call log support (backup only)

SMS Backup+ can also backup your call logs. It stores all calls using a
separate label (defaults to `Calllog`, but can be changed in Advanced
Settings). The body of the message contains the number and the duration of the
call in seconds. 0 seconds here means missed call.

##Screenshots

![SMS Backup+ screenshot][smsbackupshot] ![Gmail screenshot][gmailshot]

## FAQ

### <a name="faq-schedule">What's the difference between regular and incoming backup schedule?</a>

Incoming backup schedule is used for incoming messages. 3 minutes here means
that any incoming SMS will trigger a backup after 3 minutes. It is a full
backup (including any sent messages). You should set the incoming schedule to a
low value if you want to make sure that incoming SMS show up in
Gmail shortly after arrival.

Regular schedule is used to perform backups in specific intervals. 2 hours here
means that the device will try to backup all messages every 2 hours.

Fewer updates performed by the app means less energy consumed, so there's
a trade-off data protection vs. battery life.

### <a name="faq-timestamps">All the timestamps of my backups are wrong in Gmail!</a>

This is caused by a bug which has been fixed in 1.1.2. However even older
backups have the correct timestamp set, just not in the threading view.
You can verify this by selecting [Show original][showoriginal]
in Gmail, and then check the Date header in the email ([screenshot][source]).

All SMS backed up with version 1.1.2+ should show the correct time.

### <a name="faq-request-token">When connecting, I get 'Could not obtain request token...'</a>

If you get this error message and your network connection is active
double-check that your time zone settings are correct, and that the local time is
displaying correctly. The authentication process won't work otherwise.

### <a name="droidx-received">I'm using a Motorola DROID X, and it does not back up received messages!</a>

It's a known SMS bug in the 2.2 update ([details][droidbug]). As a workaround
you can try installing [SMS Time fix][].

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

  * [Christoph Studer](http://studer.tv/) Original author of SMS Backup
  * [k9mail](http://code.google.com/p/k9mail/) IMAP library, with some modifications ([k9mail/sms-backup-plus](http://github.com/jberkel/k9mail))
  * [signpost](http://github.com/kaeppler/signpost) Signpost OAuth library
  * [iTweek](http://itweek.deviantart.com/) and [ncrow](http://ncrow.deviantart.com/) for the Gmail icons
  * [Ben Dodson](http://github.com/bjdodson) - Contacts 2.0 / MMS support
  * [dicer](http://github.com/dicer) - Call log backup code
  * [bbs.goapk.com](http://bbs.goapk.com) / [Chen](http://blog.thisischen.com/) - Chinese translation
  * [skolima](http://github.com/skolima) - Polish translation
  * Roberto Elena Ormad - Spanish translation
  * Gabriele Ravanetti - Italian translation

## License

This application is released under the terms of the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).

[original issue list]: http://code.google.com/p/android-sms/issues/list
[github issues]: http://github.com/jberkel/sms-backup-plus/issues
[MarketQRCode]: http://chart.apis.google.com/chart?cht=qr&chs=100x100&chl=http://cyrket.com/qr/144601
[WhatisFlattr]: http://en.wikipedia.org/wiki/Flattr
[FlattrLink]: http://flattr.com/thing/45809/SMS-Backup
[FlattrButton]: http://api.flattr.com/button/button-static-50x60.png
[github downloads]: http://github.com/downloads/jberkel/sms-backup-plus/sms-backup-plus-v1.2.apk/qr_code
[Enabling IMAP in Gmail]: http://mail.google.com/support/bin/answer.py?hl=en&answer=77695
[smsbackupshot]: http://cloud.github.com/downloads/jberkel/sms-backup-plus/sms_backup_plus_screen_1_2.png
[gmailshot]: http://cloud.github.com/downloads/jberkel/sms-backup-plus/sms_gmail_screenshot.png
[crowdin project page]: http://crowdin.net/project/sms-backup-plus/invite
[showoriginal]: http://skitch.com/jberkel/d51wp/google-mail-sms-with-orange-jan.berkel-gmail.com
[source]: http://skitch.com/jberkel/d51w1/https-mail.google.com-mail-u-0-ui-2-ik-968fde0a44-view-om-th-12a94407a2104820
[droidbug]: http://www.enterprisemobiletoday.com/news/article.php/3905466/Android-22-Droid-X-Update-Causing-SMS-Bug.htm
[SMS Time fix]: http://www.appbrain.com/app/sms-time-fix/com.mattprecious.smsfix
