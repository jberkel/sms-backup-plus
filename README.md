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

  * MMS backup support (since `1.1`), only available on Android 2.x

  * Call log backup (since `1.2`), with Google Calendar integration (since
  `1.3.`) and restore (since `1.4`).

  * Batch size limits removed.

  * Works with any IMAP server (but defaults to Gmail).

Tested with Android 1.5 - 2.3.

SMS Backup+ is available for free in the market, there will never be a pro / paid version.

But if you like the app you can flattr it ([flattr it ??][WhatisFlattr]).
[![][FlattrButton]][FlattrLink]

You can also donate via PayPal from within the application (Menu - About, then hit the PayPal button).

## <a name="usage">Usage</a>

### Installation

![MarketQRCode][] Install via the Android market QR code link or directly from [github downloads][].

### First steps

You need to have an IMAP account or a Gmail account with IMAP enabled. See the
[Enabling IMAP in Gmail][] help page to learn how to enable IMAP for your Gmail
account or look at this [screenshot][imapenableshot]. If you use Google Apps
make sure you select "Sign in with a Google Apps Account"
([screenshot](http://skitch.com/jberkel/ditwx/5554-emu-2.2)).  Also note that
it does not work when two-step authentication is enabled for a Google Apps
account login.

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
"Auto Backup"). Make sure that you have `Background data` enabled in your
Android `Accounts & Sync` settings
([screenshot](http://skitch.com/jberkel/d9pi7/5554-emu-2.2)).

### Restoring

If you wish to restore messages back to your phone tap "Restore". By default
all messages stored on Gmail will be restored (this can be changed in "Advanced
Settings"). You can safely restore to a phone which has already message stored
on it, SMS Backup+ will skip the restore of already existing messages.

### <a name="call-log-support">Call log support</a>

SMS Backup+ can also backup and restore your call logs. It stores all calls using a
separate label (defaults to `Call log`, but can be changed in Advanced
Settings). If you wish you can set this to the same value as `SMS`, to make all backed
up data use the same label.

The body of the call log message contains the duration of the
call in seconds followed by the phone number and call type (`incoming` /
`outgoing` / `missed`).

An example:

    267s (00:04:07)
    +44123456789 (incoming call)

You can also add call log entries to a Google calendar. Just select `Calendar
sync` in Advanced settings, and make sure you have selected a calendar to sync
with.

If you only want to backup specific call types you can do so as well.

### <a name="3rdparty">3rd party app integration</a>

If you want to trigger backups from another app, enable `3rd party integration`
in Advanced Settings and send the broadcast intent
`com.zegoggles.smssync.BACKUP`. This will work even when Auto Backup is
disabled.

## <a name="faq">FAQ</a>

<a name="browser-bug"/>
### <a name="faq-browser">After granting access I get the message "You do do not have permission to open this page"</a>

Some browsers / handsets which ship with non-standard browsers are
currently not fully supported, there appears to be a problem which breaks
the authorisation process, so connecting your Gmail account via XOAuth is not
possible.

Some possible workarounds:

  * Use the stock Android browser for the authentication (you can switch back
  afterwards)
  * Install a 3rd party browser (Dolphin HD has been reported to work)
  * Use plain text authentication (Advanced Settings - Server Settings), use
  your gmail address as username and supply your password

### <a name="faq-browser">After granting access, the Messaging app opens.</a>

See the answer to the previous question, it's the same problem.

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

### <a name="faq-scheduling>">I'd like SMS Backup+ to schedule a backup only at a given time of the day / when Wifi is available / etc.</a>

If you require more control over the backup schedule than what SMS Backup+ already
provides you can use a 3rd party app to trigger the backup. [Tasker][] for
example supports SMS Backup+ since version 1.0.14.

### <a name="faq-request-token">When connecting, I get 'Could not obtain request token...'</a>

If you get this error message and your network connection is active
double-check that your time zone settings are correct, and that the local time is
displaying correctly. The authentication process won't work otherwise.

### <a name="droidx-received">I'm using a Motorola DROID X/2, and it does not back up incoming messages, only sent!</a>

It's a known SMS bug in the latest OTA 2.2 update ([details][droidbug]). As a workaround
you can try installing [SMS Time fix][] ([apk][smstimefixzip]) and activate the "Last Resort" option
([screenshot][smstimefixshot]).

### <a name="faq-threading">In Gmail, I'd like to have all messages listed chronologically and not ordered by who sent them.</a>

It's a Gmail feature, but you can disable it.
In Gmail settings, set conversation view to `off`
([screenshot][converationviewoff]).

### <a name="faq-show-imap">I get the following Error during backup/restore: Command: SELECT "SMS"; response: #6# [NO, Unknown, Mailbox; SMS, [Failure]]</a>

Make sure you have the "Show IMAP" option checked in the Gmail label settings:

![Screenshot][showimap]

### <a name="faq-permissions">Why does it need so many permissions?</a>

  * Read contacts - Needed to map phone numbers to names and email addresses
  * Your messages (read / write SMS) - Needed for backup+restore
  * Prevent phone from sleeping - needed to keep network active during a backup
  * Modify calendar events - needed for the call log backup to GCal
  * Send email to guests - this refers to calendar invitations (which are not created by the app)
  * Storage (modify/delete SD card contents) - this is needed for caching
  * Your accounts (discover known accounts) - currently not used and will be removed in a future version

### <a name="faq-gmail-100">The app saves only 100 SMS/MMS per contact!</a>

This seems to be a limitation of Gmail. After the first hundred or so SMS being
backed up, Gmail will cease to properly thread many of the conversations.
You will notice that Gmail will eventually treat each SMS (in that initial
backup) as individual conversations and will not longer group/thread them
together.

A way around this is to do a full backup 100 SMS at a time (see `Advanced
settings`).

## <a name="contributing">Contributing</a>

### Installation from source

    $ git clone git://github.com/jberkel/sms-backup-plus.git
    $ cd sms-backup-plus
    $ echo "sdk.dir=/path/to/android-sdk" > local.properties
    $ ant debug
    $ adb install bin/sms-backup-plus-debug.apk

I've imported some relevant issues from the [original issue list][] to [github issues][].

### <a name="translating">Translating the UI</a>

If you want to help translating the UI to other languages download and
translate the following file, then send the translated version via email:

  * [strings.xml](https://github.com/jberkel/sms-backup-plus/raw/master/res/values/strings.xml)

However, if you're already familiar with Git I'd prefer if you cloned the
repository and send me a [pull request](http://help.github.com/pull-requests/).

##<a name="credits">Credits</a>

  * [Christoph Studer](http://studer.tv/) Original author of SMS Backup
  * [Ben Dodson](http://github.com/bjdodson) - Contacts 2.0 / MMS support
  * [Felix Knecht](http://github.com/dicer) - Call log backup code
  * [k9mail](http://code.google.com/p/k9mail/) IMAP library, with some modifications ([k9mail/sms-backup-plus](http://github.com/jberkel/k9mail))
  * [acra](http://code.google.com/p/acra) ACRA - Application Crash Report for Android
  * [signpost](http://github.com/kaeppler/signpost) Signpost OAuth library
  * Jeffrey F. Cole - [NumberPicker.java][]
  * [iTweek](http://itweek.deviantart.com/) and [ncrow](http://ncrow.deviantart.com/) for the Gmail icons
  * [bbs.goapk.com](http://bbs.goapk.com) / [Chen](http://blog.thisischen.com/) - Chinese translation
  * [skolima](http://github.com/skolima) - Polish translation
  * Roberto Elena Ormad - Spanish translation
  * Gabriele Ravanetti - Italian translation
  * Harun Sahin - Turkish translation
  * [Lukas Pribyl](http://www.lukaspribyl.eu) - Czech translation
  * João Pedro Ferreira - Portugese translation
  * Martijn Brouns - Dutch translation

##<a name="screenhots">Screenshots</a>

![SMS Backup+ screenshot][smsbackupshot] ![Gmail screenshot][gmailshot] ![Gcal screenshot][gcalshot]

##<a name="license">License</a>

This application is released under the terms of the [Apache License, Version 2.0][].

[original issue list]: http://code.google.com/p/android-sms/issues/list
[github issues]: http://github.com/jberkel/sms-backup-plus/issues
[MarketQRCode]: http://chart.apis.google.com/chart?cht=qr&chs=100x100&chl=http://cyrket.com/qr/144601
[WhatisFlattr]: http://en.wikipedia.org/wiki/Flattr
[FlattrLink]: http://flattr.com/thing/45809/SMS-Backup
[FlattrButton]: http://api.flattr.com/button/button-static-50x60.png
[github downloads]: https://github.com/jberkel/sms-backup-plus/sms-backup-plus-v1.4.1.apk/qr_code
[Enabling IMAP in Gmail]: http://mail.google.com/support/bin/answer.py?hl=en&answer=77695
[smsbackupshot]: https://github.com/downloads/jberkel/sms-backup-plus/sms_backup_plus_screen_1_4.png
[gmailshot]: https://github.com/downloads/jberkel/sms-backup-plus/sms_gmail_screenshot.png
[gcalshot]: https://github.com/downloads/jberkel/sms-backup-plus/sms_gcal_screenshot.png
[crowdin project page]: http://crowdin.net/project/sms-backup-plus/invite
[showoriginal]: http://skitch.com/jberkel/d51wp/google-mail-sms-with-orange-jan.berkel-gmail.com
[source]: http://skitch.com/jberkel/d51w1/https-mail.google.com-mail-u-0-ui-2-ik-968fde0a44-view-om-th-12a94407a2104820
[droidbug]: http://www.mydigitallife.info/2010/09/27/motorola-droid-x-froyo-text-messaging-bug-rectified-via-sms-time-fix/
[SMS Time fix]: http://www.appbrain.com/app/sms-time-fix/com.mattprecious.smsfix
[converationviewoff]: https://skitch.com/jberkel/ryk8y/soundcloud.com-mail-settings-jan-soundcloud.com
[smstimefixzip]: https://supportforums.motorola.com/servlet/JiveServlet/download/269690-40815/sms-time-fix.zip

[smstimefixshot]: https://github.com/downloads/jberkel/sms-backup-plus/sms_time_fix.png
[imapenableshot]: https://skitch.com/jberkel/ryk1c/google-mail-settings-jan.berkel-gmail.com#lightbox
[showimap]: https://github.com/downloads/jberkel/sms-backup-plus/show_imap.png
[Tasker]: http://tasker.dinglisch.net/
[NumberPicker.java]: http://www.technologichron.net/?p=42
[Apache License, Version 2.0]: http://www.apache.org/licenses/LICENSE-2.0.html
