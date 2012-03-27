## SMS Backup+

[![][Icon]][MarketLink]

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

Tested with Android 1.5 - 4.0.x.

SMS Backup+ is available for free in the market, there will never be a pro / paid version.

But if you like the app you can flattr it ([flattr it ??][WhatisFlattr]).
[![][FlattrButton]][FlattrLink]

You can also donate via PayPal from within the application (Menu - About, then hit the PayPal button).

## <a name="usage">Usage</a>

### Installation

![MarketQRCode][] Install via the Android market QR code link, [Android
Market][MarketLink] or directly from [github downloads][].

### First steps

You need to have an IMAP account or a Gmail account with IMAP enabled. See the
[Enabling IMAP in Gmail][] help page to learn how to enable IMAP for your Gmail
account or look at this [screenshot][imapenableshot]. If you use Google Apps
make sure you select "Sign in with a Google Apps Account"
([screenshot](http://skitch.com/jberkel/ditwx/5554-emu-2.2)).

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

### <a name="2step">Usage with Google 2-step verification</a>

XOAuth might not work when you are signed up for 2-step verification (see
[Getting started with 2-step verification][] for more information).
In this case you need to create an application-specific
password ([Authorized Access to your Google Account][]) and enable plain text
authentication in "Advanced Settings - IMAP server settings".  Provide the
username and password you created in the previous step.

### Usage without Gmail (IMAP)

You don't have to use Gmail to backup your text messages - change
Authentication to Plain text in "Advanced Settings - IMAP server settings",
then change the server address / username and password accordingly. Make sure
to set security to "SSL (Optional)" if the IMAP server you're connecting to has
a self-signed certificate ("Unknown certificate" error during backup).

Also note that Gmail labels correspond to IMAP folders, which will
automatically be created the first time you're performing a backup.

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

### <a name="droidx-received">I'm using a Motorola DROID X/2, and it does not back up incoming messages, only sent!</a>

It's a known SMS bug in the latest OTA 2.2 update ([details][droidbug]). As a workaround
you can try installing [SMS Time fix][] ([apk][smstimefixzip]) and set "Adjustment Method" to "Use Phone's Time"
([screenshot1][smstimefixshot1], [screenshot2][smstimefixshot2]).


### <a name="faq-request-token">When connecting, I get 'Could not obtain request token...'</a>

If you get this error message and your network connection is active
double-check that your time zone settings are correct, and that the local time is
displaying correctly. The authentication process won't work otherwise.

### <a name="faq-package-not-signed">When updating the app I get 'Package file was not signed correctly'</a>

Try uninstalling the app, then installing the new version. Make sure to select
"Skip" when doing the first backup, otherwise messages will get backed up
twice.

### <a name="faq-inbox">Why do backed up SMS show up in my inbox?</a>

This is probably related to Gmail's automatic priority inbox filing.
A workaround is to set up a filter with "subject: SMS with", let the filter
mark it as not important.

![](https://img.skitch.com/20120106-tymk3rn4i5apshhr6e1hbd17qn.jpg)
![](https://img.skitch.com/20120106-rsg7912rnus5gwe3e572rxwbae.jpg)

### <a name="faq-show-imap">I get the following Error during backup/restore: Command: SELECT "SMS"; response: #6# [NO, Unknown, Mailbox; SMS, [Failure]]</a>

Make sure you have the "Show IMAP" option checked in the Gmail label settings:

![Screenshot][showimap]

If this is the case make sure that the label name is set correctly (capitalization
matters!).

### <a name="faq-gmail-100">The app saves only 100 SMS/MMS per contact!</a>

This seems to be a limitation of Gmail. After the first hundred or so SMS being
backed up, Gmail will cease to properly thread many of the conversations.
You will notice that Gmail will eventually treat each SMS (in that initial
backup) as individual conversations and will not longer group/thread them
together.

A way around this is to do a full backup 100 SMS at a time (see `Advanced
settings`).

### <a name="faq-threading">In Gmail, I'd like to have all messages listed chronologically and not ordered by who sent them.</a>

It's a Gmail feature, but you can disable it.
In Gmail settings, set conversation view to `off`
([screenshot][converationviewoff]).

### <a name="faq-permissions">Why does it need so many permissions?</a>

  * Read contacts - Needed to map phone numbers to names and email addresses
  * Your messages (read / write SMS) - Needed for backup+restore
  * Prevent phone from sleeping - needed to keep network active during a backup
  * Modify calendar events - needed for the call log backup to GCal
  * Send email to guests - this refers to calendar invitations (which are not created by the app)
  * Storage (modify/delete SD card contents) - this is needed for caching
  * Your accounts (discover known accounts) - currently not used and will be removed in a future version

### <a name="faq-partial-restore">How do I restore the last N weeks / N messages?</a>

If you have a lot of messages backed up (let's say over 5000) restoring can be
very slow, especially if you're only interested in the most recent messages.

A workaround is to use the gmail web interface (or an IMAP email client) to
move the bulk of the messages to another label in gmail (e.g. SMSARCHIVED), and
only keep a few hundred or so messages in the SMS label.

Next time you restore it will only restore those messages and it will be a lot
faster.

### <a name="faq-does-it-sync">When I delete a text locally, will it delete the saved copy on gmail?</a>

No. SMS Backup+ does not do a "real" sync, once the text has been transferred
to Gmail it won't get modified or deleted by the app.

### <a name="faq-reset">How can I make the app think that it has to do the backup again?</a>

Select "Reset" from the menu, and confirm that you want to reset the current
sync state. All messages on the phone will be backed up on the next run.

### <a name="faq-revoke-access">How can I revoke SMS Backup+ access</a>

Go to [Authorized Access to your Google Account][] and select "Revoke Access"
next to "SMS Backup+".

### <a name="faq-delete">When I delete messages on my phone, will they get deleted on gmail?</a>

No. SMS Backup+ is a backup tool, so there is no 2-way syncing supported. It
will only ever append to your mailbox.

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

### <a name="faq-scheduling">I'd like SMS Backup+ to schedule a backup only at a given time of the day / when Wifi is available / etc.</a>

If you require more control over the backup schedule than what SMS Backup+ already
provides you can use a 3rd party app to trigger the backup. [Tasker][] for
example supports SMS Backup+ since version 1.0.14.

## <a name="contributing">Contributing</a>

### Installation from source

    $ git clone git://github.com/jberkel/sms-backup-plus.git
    $ cd sms-backup-plus
    $ android update project -p . -n sms-backup-plus
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
  * [Michael Scharfstein](http://github.com/smike) - Call log calendar ICS support
  * [k9mail](http://code.google.com/p/k9mail/) IMAP library, with some modifications ([k9mail/sms-backup-plus](http://github.com/jberkel/k9mail))
  * [acra](http://code.google.com/p/acra) ACRA - Application Crash Report for Android
  * [signpost](http://github.com/kaeppler/signpost) Signpost OAuth library
  * Jeffrey F. Cole - [NumberPicker.java][]
  * Shimon Simon (new icon designs)
  * [bbs.goapk.com](http://bbs.goapk.com) / [Chen](http://blog.thisischen.com/) - Chinese translation
  * [skolima](http://github.com/skolima) - Polish translation
  * Roberto Elena Ormad - Spanish translation
  * Gabriele Ravanetti / [Patryk Rzucidlo](http://www.ptkdev.it/) - Italian translation
  * Harun Sahin - Turkish translation
  * [Lukas Pribyl](http://www.lukaspribyl.eu) - Czech translation
  * João Pedro Ferreira - Portugese translation
  * Martijn Brouns - Dutch translation
  * [Tobeon](http://tobeon.net) - Norwegian translation
  * Nemanja Bračko - Serbian translation
  * Markus Osanger - German translation

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
[github downloads]: https://github.com/jberkel/sms-backup-plus/sms-backup-plus-v1.4.5.apk/qr_code
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
[smstimefixshot1]: https://github.com/downloads/jberkel/sms-backup-plus/sms_time_fix_new_1.png
[smstimefixshot2]: https://github.com/downloads/jberkel/sms-backup-plus/sms_time_fix_new_2.png
[imapenableshot]: https://skitch.com/jberkel/ryk1c/google-mail-settings-jan.berkel-gmail.com#lightbox
[showimap]: https://github.com/downloads/jberkel/sms-backup-plus/show_imap.png
[Tasker]: http://tasker.dinglisch.net/
[NumberPicker.java]: http://www.technologichron.net/?p=42
[Apache License, Version 2.0]: http://www.apache.org/licenses/LICENSE-2.0.html
[Icon]: https://github.com/downloads/jberkel/sms-backup-plus/smsbackup72.png
[MarketLink]: https://market.android.com/details?id=com.zegoggles.smssync
[Getting started with 2-step verification]: http://www.google.com/support/accounts/bin/static.py?page=guide.cs&guide=1056283&topic=1056284
[Authorized Access to your Google Account]: https://www.google.com/accounts/b/0/IssuedAuthSubTokens
